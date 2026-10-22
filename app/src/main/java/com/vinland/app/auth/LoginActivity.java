package com.vinland.app.auth;

import android.content.Intent;
import android.os.Bundle;
import android.text.TextUtils;
import android.util.Patterns;
import android.view.View;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AppCompatActivity;

import com.google.android.gms.auth.api.signin.GoogleSignIn;
import com.google.android.gms.auth.api.signin.GoogleSignInAccount;
import com.google.android.gms.auth.api.signin.GoogleSignInClient;
import com.google.android.gms.auth.api.signin.GoogleSignInOptions;
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
import com.google.firebase.firestore.FirebaseFirestore;
import com.vinland.app.R;
import com.vinland.app.dashboard.DashboardActivity;

import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;

public class LoginActivity extends AppCompatActivity {

    private TextInputEditText etEmail, etPassword;
    private MaterialButton btnLogin, btnGoogleSignIn;
    private ProgressBar progressBar;
    private TextView tvError, tvGoToRegister, tvForgotPassword;

    private FirebaseAuth mAuth;
    private FirebaseFirestore db;
    private GoogleSignInClient mGoogleSignInClient;
    private ActivityResultLauncher<Intent> googleSignInLauncher;

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
                                setLoading(false);
                                showError("Could not retrieve Google authentication token.");
                            }
                        } catch (ApiException e) {
                            setLoading(false);
                            showError("Google Sign-In failed: " + e.getMessage());
                        }
                    } else {
                        setLoading(false);
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
        // If already logged in, skip straight to the dashboard
        if (mAuth.getCurrentUser() != null) {
            goToDashboard();
        }
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
                            ensureGardenerProfileExists(user);
                        } else {
                            setLoading(false);
                            goToDashboard();
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

    private void attemptGoogleSignIn() {
        hideError();
        setLoading(true);
        Intent signInIntent = mGoogleSignInClient.getSignInIntent();
        googleSignInLauncher.launch(signInIntent);
    }

    private void firebaseAuthWithGoogle(String idToken) {
        AuthCredential credential = GoogleAuthProvider.getCredential(idToken, null);
        mAuth.signInWithCredential(credential)
                .addOnCompleteListener(this, task -> {
                    if (task.isSuccessful()) {
                        FirebaseUser user = mAuth.getCurrentUser();
                        if (user != null) {
                            ensureGardenerProfileExists(user);
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

    private void ensureGardenerProfileExists(FirebaseUser user) {
        db.collection("users").document(user.getUid())
                .get()
                .addOnCompleteListener(task -> {
                    if (task.isSuccessful() && task.getResult() != null && !task.getResult().exists()) {
                        // Create gardener profile document for Google Sign-In user
                        Map<String, Object> profile = new HashMap<>();
                        String name = user.getDisplayName() != null ? user.getDisplayName() : "Gardener";
                        profile.put("fullName", name);
                        profile.put("firstName", name.contains(" ") ? name.substring(0, name.indexOf(' ')) : name);
                        profile.put("lastName", name.contains(" ") ? name.substring(name.indexOf(' ') + 1) : "");
                        profile.put("email", user.getEmail());
                        profile.put("barangay", "Quezon City");
                        profile.put("gardenType", "Backyard Garden");
                        profile.put("crops", Arrays.asList("Tomato", "Green Pepper"));
                        profile.put("role", "gardener");
                        profile.put("status", "Active");
                        profile.put("sensorDeviceId", "VINLAND-ESP32-01");
                        profile.put("createdAt", System.currentTimeMillis());

                        db.collection("users").document(user.getUid())
                                .set(profile)
                                .addOnCompleteListener(t -> {
                                    setLoading(false);
                                    goToDashboard();
                                });
                    } else {
                        setLoading(false);
                        goToDashboard();
                    }
                });
    }

    private void attemptPasswordReset() {
        String email = getText(etEmail);
        if (TextUtils.isEmpty(email) || !Patterns.EMAIL_ADDRESS.matcher(email).matches()) {
            showError("Enter your email address above first, then tap \"Forgot Password?\".");
            return;
        }
        mAuth.sendPasswordResetEmail(email)
                .addOnSuccessListener(unused ->
                        Toast.makeText(this, "Password reset email sent to " + email + ". Check your inbox.", Toast.LENGTH_LONG).show())
                .addOnFailureListener(e ->
                        showError("Could not send reset email: " + e.getMessage()));
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
