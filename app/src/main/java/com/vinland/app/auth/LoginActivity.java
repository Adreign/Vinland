package com.vinland.app.auth;

import android.accounts.AccountManager;
import android.content.Intent;
import android.os.Bundle;
import android.text.InputType;
import android.text.TextUtils;
import android.util.Patterns;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;

import com.google.android.gms.auth.api.signin.GoogleSignIn;
import com.google.android.gms.auth.api.signin.GoogleSignInAccount;
import com.google.android.gms.auth.api.signin.GoogleSignInClient;
import com.google.android.gms.auth.api.signin.GoogleSignInOptions;
import com.google.android.gms.common.AccountPicker;
import com.google.android.gms.common.api.ApiException;
import com.google.android.gms.tasks.Task;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.textfield.TextInputEditText;
import com.google.firebase.auth.AuthCredential;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseAuthInvalidCredentialsException;
import com.google.firebase.auth.FirebaseAuthInvalidUserException;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.auth.GoogleAuthProvider;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FirebaseFirestore;
import com.vinland.app.R;
import com.vinland.app.dashboard.DashboardActivity;

public class LoginActivity extends AppCompatActivity {

    private TextInputEditText etEmail, etPassword;
    private MaterialButton btnLogin, btnGoogleSignIn;
    private ProgressBar progressBar;
    private TextView tvError, tvGoToRegister, tvForgotPassword;

    private FirebaseAuth mAuth;
    private FirebaseFirestore db;
    private GoogleSignInClient mGoogleSignInClient;
    private ActivityResultLauncher<Intent> googleSignInLauncher;
    private ActivityResultLauncher<Intent> nativeAccountPickerLauncher;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_login);

        mAuth = FirebaseAuth.getInstance();
        db = FirebaseFirestore.getInstance();

        etEmail = findViewById(R.id.etEmail);
        etPassword = findViewById(R.id.etPassword);
        btnLogin = findViewById(R.id.btnLogin);
        btnGoogleSignIn = findViewById(R.id.btnGoogleSignIn);
        progressBar = findViewById(R.id.progressBar);
        tvError = findViewById(R.id.tvError);
        tvGoToRegister = findViewById(R.id.tvGoToRegister);
        tvForgotPassword = findViewById(R.id.tvForgotPassword);

        // Configure Google Sign-In
        GoogleSignInOptions gso = new GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
                .requestIdToken(getString(R.string.default_web_client_id))
                .requestEmail()
                .build();
        mGoogleSignInClient = GoogleSignIn.getClient(this, gso);

        // 1. Google OAuth Launcher
        googleSignInLauncher = registerForActivityResult(
                new ActivityResultContracts.StartActivityForResult(),
                result -> {
                    if (result.getResultCode() == RESULT_OK && result.getData() != null) {
                        Task<GoogleSignInAccount> task = GoogleSignIn.getSignedInAccountFromIntent(result.getData());
                        try {
                            GoogleSignInAccount account = task.getResult(ApiException.class);
                            if (account != null && account.getIdToken() != null) {
                                firebaseAuthWithGoogle(account.getIdToken());
                            } else {
                                launchNativeDeviceAccountPicker();
                            }
                        } catch (ApiException e) {
                            setLoading(false);
                            launchNativeDeviceAccountPicker();
                        }
                    } else {
                        setLoading(false);
                        launchNativeDeviceAccountPicker();
                    }
                }
        );

        // 2. Native Account Picker Launcher (Fallback)
        nativeAccountPickerLauncher = registerForActivityResult(
                new ActivityResultContracts.StartActivityForResult(),
                result -> {
                    setLoading(false);
                    if (result.getResultCode() == RESULT_OK && result.getData() != null) {
                        String accountName = result.getData().getStringExtra(AccountManager.KEY_ACCOUNT_NAME);
                        if (!TextUtils.isEmpty(accountName)) {
                            etEmail.setText(accountName);
                            Toast.makeText(this, "Gmail address " + accountName + " filled! Please enter your password or tap Register.", Toast.LENGTH_LONG).show();
                        }
                    }
                }
        );

        btnLogin.setOnClickListener(v -> attemptLogin());
        btnGoogleSignIn.setOnClickListener(v -> attemptGoogleSignIn());

        tvGoToRegister.setOnClickListener(v ->
                startActivity(new Intent(this, RegisterActivity.class)));

        tvForgotPassword.setOnClickListener(v -> attemptPasswordReset());
    }

    @Override
    protected void onStart() {
        super.onStart();
        // If already logged in, check profile status before going to dashboard
        FirebaseUser user = mAuth.getCurrentUser();
        if (user != null) {
            setLoading(true);
            user.reload().addOnCompleteListener(task -> {
                setLoading(false);
                if (user.isEmailVerified() || isGoogleAccount(user)) {
                    checkUserStatusAndNavigate(user);
                } else {
                    mAuth.signOut();
                }
            });
        }
    }

    private boolean isGoogleAccount(FirebaseUser user) {
        if (user != null && user.getProviderData() != null) {
            for (com.google.firebase.auth.UserInfo profile : user.getProviderData()) {
                if ("google.com".equalsIgnoreCase(profile.getProviderId())) {
                    return true;
                }
            }
        }
        return false;
    }

    private void attemptLogin() {
        String email = getText(etEmail);
        String password = getText(etPassword);

        if (TextUtils.isEmpty(email) || !Patterns.EMAIL_ADDRESS.matcher(email).matches()) {
            showError("Please enter a valid email address.");
            return;
        }
        if (TextUtils.isEmpty(password)) {
            showError("Please enter your password.");
            return;
        }

        hideError();
        setLoading(true);

        mAuth.signInWithEmailAndPassword(email, password)
                .addOnCompleteListener(this, task -> {
                    if (task.isSuccessful()) {
                        FirebaseUser user = mAuth.getCurrentUser();
                        if (user != null) {
                            user.reload().addOnCompleteListener(reloadTask -> {
                                if (!user.isEmailVerified() && !isGoogleAccount(user)) {
                                    setLoading(false);
                                    showUnverifiedEmailDialog(user);
                                } else {
                                    checkUserStatusAndNavigate(user);
                                }
                            });
                        } else {
                            setLoading(false);
                            showError("Login failed.");
                        }
                    } else {
                        setLoading(false);
                        Exception e = task.getException();
                        String message;
                        if (e instanceof FirebaseAuthInvalidCredentialsException || e instanceof FirebaseAuthInvalidUserException) {
                            message = "Incorrect email or password. Tap 'Register' below if you haven't created an account yet.";
                        } else if (e != null && e.getMessage() != null) {
                            message = e.getMessage();
                            if (message.contains("auth credential") || message.contains("invalid")) {
                                message = "Incorrect password or account not found. Tap 'Register' below or 'Forgot Password?'.";
                            }
                        } else {
                            message = "Login failed. Please check your email and password.";
                        }
                        showError(message);
                    }
                });
    }

    private void showUnverifiedEmailDialog(FirebaseUser user) {
        new AlertDialog.Builder(this)
                .setTitle("📧 Email Not Verified")
                .setMessage("Your email address (" + user.getEmail() + ") has not been verified yet.\n\nPlease check your email inbox and click the verification link before logging in.")
                .setPositiveButton("Resend Verification Link", (dialog, which) -> {
                    setLoading(true);
                    user.sendEmailVerification().addOnCompleteListener(task -> {
                        setLoading(false);
                        if (task.isSuccessful()) {
                            Toast.makeText(LoginActivity.this, "📧 Verification link resent to " + user.getEmail() + "!", Toast.LENGTH_LONG).show();
                        } else {
                            Toast.makeText(LoginActivity.this, "Could not resend email: " + (task.getException() != null ? task.getException().getMessage() : "Error"), Toast.LENGTH_LONG).show();
                        }
                        mAuth.signOut();
                    });
                })
                .setNegativeButton("Cancel", (dialog, which) -> mAuth.signOut())
                .setCancelable(false)
                .show();
    }

    private void attemptGoogleSignIn() {
        hideError();
        setLoading(true);
        mGoogleSignInClient.signOut().addOnCompleteListener(this, task -> {
            try {
                Intent signInIntent = mGoogleSignInClient.getSignInIntent();
                googleSignInLauncher.launch(signInIntent);
            } catch (Exception e) {
                launchNativeDeviceAccountPicker();
            }
        });
    }

    private void launchNativeDeviceAccountPicker() {
        try {
            Intent intent = AccountPicker.newChooseAccountIntent(
                    new AccountPicker.AccountChooserOptions.Builder()
                            .setAllowableAccountsTypes(java.util.Collections.singletonList("com.google"))
                            .build()
            );
            nativeAccountPickerLauncher.launch(intent);
        } catch (Exception e) {
            try {
                Intent intent = AccountManager.newChooseAccountIntent(
                        null, null, new String[]{"com.google"}, false, null, null, null, null
                );
                nativeAccountPickerLauncher.launch(intent);
            } catch (Exception ex) {
                setLoading(false);
                showError("Please enter your email and password above.");
            }
        }
    }

    private void firebaseAuthWithGoogle(String idToken) {
        AuthCredential credential = GoogleAuthProvider.getCredential(idToken, null);
        mAuth.signInWithCredential(credential)
                .addOnCompleteListener(this, task -> {
                    if (task.isSuccessful()) {
                        FirebaseUser user = mAuth.getCurrentUser();
                        if (user != null) {
                            Toast.makeText(this, "Signed in as " + user.getEmail() + "!", Toast.LENGTH_SHORT).show();
                            checkUserStatusAndNavigate(user);
                        } else {
                            setLoading(false);
                            goToDashboard();
                        }
                    } else {
                        setLoading(false);
                        showError("Google Sign-In failed: " + (task.getException() != null ? task.getException().getMessage() : "Authentication error"));
                    }
                });
    }

    private void checkUserStatusAndNavigate(FirebaseUser user) {
        db.collection("users").document(user.getUid())
                .get()
                .addOnCompleteListener(task -> {
                    setLoading(false);
                    if (task.isSuccessful() && task.getResult() != null && task.getResult().exists()) {
                        DocumentSnapshot doc = task.getResult();
                        String status = doc.getString("status");
                        Boolean isDeleted = doc.getBoolean("deleted");

                        if ("Suspended".equalsIgnoreCase(status) || "Deactivated".equalsIgnoreCase(status) || Boolean.TRUE.equals(isDeleted)) {
                            // Block Login!
                            mAuth.signOut();
                            showError("🚫 Access Denied: Your account has been deleted or suspended by the administrator.");
                            Toast.makeText(this, "This account was deleted or suspended by administrator.", Toast.LENGTH_LONG).show();
                            return;
                        }

                        // Status is Active -> Proceed
                        goToDashboard();
                    } else if (task.isSuccessful() && task.getResult() != null && !task.getResult().exists()) {
                        // Document was deleted from Firestore!
                        mAuth.signOut();
                        showError("🚫 Account Not Found: This account was permanently deleted from the database.");
                        Toast.makeText(this, "This account was deleted by the administrator.", Toast.LENGTH_LONG).show();
                    } else {
                        // Error loading profile doc
                        showError("Could not verify account status. Check your internet connection.");
                    }
                });
    }

    private void attemptPasswordReset() {
        String prefilledEmail = getText(etEmail);

        LinearLayout container = new LinearLayout(this);
        container.setOrientation(LinearLayout.VERTICAL);
        int paddingPx = Math.round(20 * getResources().getDisplayMetrics().density);
        container.setPadding(paddingPx, Math.round(12 * getResources().getDisplayMetrics().density), paddingPx, 0);

        final TextInputEditText input = new TextInputEditText(this);
        input.setHint("Enter your email address");
        input.setInputType(InputType.TYPE_TEXT_VARIATION_EMAIL_ADDRESS);
        if (!TextUtils.isEmpty(prefilledEmail)) {
            input.setText(prefilledEmail);
        }
        container.addView(input);

        new AlertDialog.Builder(this)
                .setTitle("🔑 Password Reset Request")
                .setMessage("Please enter your registered Vinland account email address below. We will send you an official link to securely reset your password.")
                .setView(container)
                .setPositiveButton("Send Reset Instructions", (dialog, which) -> {
                    String email = input.getText() != null ? input.getText().toString().trim() : "";
                    if (TextUtils.isEmpty(email) || !Patterns.EMAIL_ADDRESS.matcher(email).matches()) {
                        showError("Please enter a valid email address to reset your password.");
                        return;
                    }
                    hideError();
                    setLoading(true);
                    mAuth.sendPasswordResetEmail(email)
                            .addOnSuccessListener(unused -> {
                                setLoading(false);
                                new AlertDialog.Builder(LoginActivity.this)
                                        .setTitle("📧 Password Reset Email Sent")
                                        .setMessage("A secure password reset link has been dispatched to:\n\n" + email + "\n\nPlease check your email inbox and follow the instructions to establish your new password.")
                                        .setPositiveButton("Understood", null)
                                        .show();
                            })
                            .addOnFailureListener(e -> {
                                setLoading(false);
                                String msg = e.getMessage();
                                if (msg != null && (msg.contains("user-not-found") || msg.contains("no user"))) {
                                    msg = "No Vinland account exists for this email address. Please register a new account.";
                                }
                                showError("Password Reset Request Error: " + msg);
                            });
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    private void goToDashboard() {
        startActivity(new Intent(this, DashboardActivity.class));
        finish();
    }

    private String getText(TextInputEditText field) {
        return field.getText() != null ? field.getText().toString().trim() : "";
    }

    private void showError(String message) {
        tvError.setText(message);
        tvError.setVisibility(View.VISIBLE);
    }

    private void hideError() {
        tvError.setVisibility(View.GONE);
    }

    private void setLoading(boolean loading) {
        progressBar.setVisibility(loading ? View.VISIBLE : View.GONE);
        btnLogin.setEnabled(!loading);
        btnGoogleSignIn.setEnabled(!loading);
    }
}
