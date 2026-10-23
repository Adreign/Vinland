package com.vinland.app.auth;

import android.accounts.AccountManager;
import android.content.Intent;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.os.Bundle;
import android.text.TextUtils;
import android.util.Log;
import android.util.Patterns;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.AutoCompleteTextView;
import android.widget.CheckBox;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.RadioButton;
import android.widget.RadioGroup;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AppCompatActivity;

import com.google.android.gms.auth.api.signin.GoogleSignIn;
import com.google.android.gms.auth.api.signin.GoogleSignInAccount;
import com.google.android.gms.auth.api.signin.GoogleSignInClient;
import com.google.android.gms.auth.api.signin.GoogleSignInOptions;
import com.google.android.gms.common.AccountPicker;
import com.google.android.gms.common.api.ApiException;
import com.google.android.gms.tasks.Task;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.progressindicator.LinearProgressIndicator;
import com.google.android.material.textfield.TextInputEditText;
import com.google.android.material.textfield.TextInputLayout;
import com.google.firebase.auth.AuthCredential;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.auth.GoogleAuthProvider;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.SetOptions;
import com.vinland.app.R;
import com.vinland.app.dashboard.DashboardActivity;
import com.vinland.app.data.Crop;
import com.vinland.app.data.CropDatabaseSeeder;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class RegisterActivity extends AppCompatActivity {

    private static final String TAG = "VinlandRegister";

    private TextInputEditText etFirstName, etLastName, etEmail, etPassword, etConfirmPassword;
    private TextInputLayout tilPassword, tilConfirmPassword;
    private AutoCompleteTextView actvBarangay;
    private RadioGroup rgGardenType;
    private LinearLayout layoutCropsContainer;
    private LinearLayout layoutStep1, layoutStep2, layoutStep3, layoutStep4;
    private MaterialButton btnGoogleRegister, btnNextStep1, btnNextStep2, btnNextStep3, btnRegister;
    private MaterialButton btnBackStep2, btnBackStep3, btnBackStep4;
    private TextView tvStepIndicator;
    private LinearProgressIndicator progressStep;
    private ProgressBar progressBar;
    private TextView tvError, tvGoToLogin;

    private FirebaseAuth mAuth;
    private FirebaseFirestore db;
    private GoogleSignInClient mGoogleSignInClient;
    private ActivityResultLauncher<Intent> googleSignInLauncher;
    private ActivityResultLauncher<Intent> nativeAccountPickerLauncher;
    private boolean isGoogleAuthenticated = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_register);

        mAuth = FirebaseAuth.getInstance();
        db = FirebaseFirestore.getInstance();

        // Bind step containers
        layoutStep1 = findViewById(R.id.layoutStep1);
        layoutStep2 = findViewById(R.id.layoutStep2);
        layoutStep3 = findViewById(R.id.layoutStep3);
        layoutStep4 = findViewById(R.id.layoutStep4);
        tvStepIndicator = findViewById(R.id.tvStepIndicator);
        progressStep = findViewById(R.id.progressStep);

        // Bind form inputs
        etFirstName = findViewById(R.id.etFirstName);
        etLastName = findViewById(R.id.etLastName);
        etEmail = findViewById(R.id.etEmail);
        etPassword = findViewById(R.id.etPassword);
        etConfirmPassword = findViewById(R.id.etConfirmPassword);
        tilPassword = findViewById(R.id.tilPassword);
        tilConfirmPassword = findViewById(R.id.tilConfirmPassword);
        actvBarangay = findViewById(R.id.actvBarangay);
        rgGardenType = findViewById(R.id.rgGardenType);
        layoutCropsContainer = findViewById(R.id.layoutCropsContainer);

        // Bind action buttons
        btnGoogleRegister = findViewById(R.id.btnGoogleRegister);
        btnNextStep1 = findViewById(R.id.btnNextStep1);
        btnNextStep2 = findViewById(R.id.btnNextStep2);
        btnNextStep3 = findViewById(R.id.btnNextStep3);
        btnRegister = findViewById(R.id.btnRegister);

        btnBackStep2 = findViewById(R.id.btnBackStep2);
        btnBackStep3 = findViewById(R.id.btnBackStep3);
        btnBackStep4 = findViewById(R.id.btnBackStep4);

        progressBar = findViewById(R.id.progressBar);
        tvError = findViewById(R.id.tvError);
        tvGoToLogin = findViewById(R.id.tvGoToLogin);

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
                                firebaseAuthWithGoogle(account.getIdToken(), account);
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
                            applyAutoFilledEmail(accountName);
                        }
                    }
                }
        );

        // Barangay type-ahead suggestions
        ArrayAdapter<String> barangayAdapter = new ArrayAdapter<>(
                this,
                android.R.layout.simple_dropdown_item_1line,
                getResources().getStringArray(R.array.qc_barangays)
        );
        actvBarangay.setAdapter(barangayAdapter);

        // Navigation click listeners
        btnGoogleRegister.setOnClickListener(v -> attemptGoogleRegister());
        btnNextStep1.setOnClickListener(v -> validateAndProceedStep1());

        btnNextStep2.setOnClickListener(v -> validateAndProceedStep2());
        btnBackStep2.setOnClickListener(v -> showStep(1));

        btnNextStep3.setOnClickListener(v -> validateAndProceedStep3());
        btnBackStep3.setOnClickListener(v -> showStep(2));

        btnRegister.setOnClickListener(v -> completeRegistrationStep4());
        btnBackStep4.setOnClickListener(v -> showStep(3));

        tvGoToLogin.setOnClickListener(v -> {
            startActivity(new Intent(this, LoginActivity.class));
            finish();
        });

        CropDatabaseSeeder.seedIfEmpty(db, this::loadCropsFromFirestore);
        showStep(1);
    }

    private void showStep(int step) {
        hideError();

        progressStep.setProgress(step * 25);

        layoutStep1.setVisibility(step == 1 ? View.VISIBLE : View.GONE);
        layoutStep2.setVisibility(step == 2 ? View.VISIBLE : View.GONE);
        layoutStep3.setVisibility(step == 3 ? View.VISIBLE : View.GONE);
        layoutStep4.setVisibility(step == 4 ? View.VISIBLE : View.GONE);

        switch (step) {
            case 1:
                tvStepIndicator.setText("Step 1 of 4: Account Authentication");
                break;
            case 2:
                tvStepIndicator.setText("Step 2 of 4: Select Barangay Location");
                actvBarangay.requestFocus();
                break;
            case 3:
                tvStepIndicator.setText("Step 3 of 4: Garden Typology");
                break;
            case 4:
                tvStepIndicator.setText("Step 4 of 4: Choose Crops to Grow");
                break;
        }
    }

    private void attemptGoogleRegister() {
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
                showError("Please enter your email address in the box below.");
            }
        }
    }

    private void firebaseAuthWithGoogle(String idToken, GoogleSignInAccount account) {
        AuthCredential credential = GoogleAuthProvider.getCredential(idToken, null);
        mAuth.signInWithCredential(credential)
                .addOnCompleteListener(this, task -> {
                    setLoading(false);
                    if (task.isSuccessful()) {
                        isGoogleAuthenticated = true;
                        FirebaseUser firebaseUser = mAuth.getCurrentUser();

                        String mail = firebaseUser != null && firebaseUser.getEmail() != null
                                ? firebaseUser.getEmail()
                                : (account != null ? account.getEmail() : "");

                        // Stage 1: Instantly write account document to Firestore database upon Google Auth
                        if (firebaseUser != null) {
                            saveGardenerProfile(firebaseUser.getUid(), "Gardener", "", mail, "Quezon City", "Backyard Garden", Arrays.asList("Tomato", "Green Pepper"), false);
                        }

                        applyAutoFilledEmail(mail);
                    } else {
                        if (account != null && !TextUtils.isEmpty(account.getEmail())) {
                            applyAutoFilledEmail(account.getEmail());
                        } else {
                            showError("Google authentication failed: " + (task.getException() != null ? task.getException().getMessage() : "Error"));
                        }
                    }
                });
    }

    private void applyAutoFilledEmail(String mail) {
        if (TextUtils.isEmpty(mail)) return;

        isGoogleAuthenticated = true;
        runOnUiThread(() -> {
            etEmail.setText(mail);
            etEmail.setEnabled(false); // Lock verified Gmail address

            // Keep Password and Confirm Password text boxes ALWAYS visible
            if (tilPassword != null) tilPassword.setVisibility(View.VISIBLE);
            if (tilConfirmPassword != null) tilConfirmPassword.setVisibility(View.VISIBLE);

            showStep(1);
            Toast.makeText(RegisterActivity.this, "✅ Gmail address " + mail + " autofilled! Enter First Name and Last Name below.", Toast.LENGTH_LONG).show();

            if (TextUtils.isEmpty(getText(etFirstName))) {
                etFirstName.requestFocus();
            }
        });
    }

    private void validateAndProceedStep1() {
        String firstName = getText(etFirstName);
        String lastName = getText(etLastName);
        String email = getText(etEmail);
        String password = getText(etPassword);
        String confirmPassword = getText(etConfirmPassword);

        if (TextUtils.isEmpty(firstName)) {
            showError("Please enter your first name.");
            return;
        }
        if (TextUtils.isEmpty(lastName)) {
            showError("Please enter your last name.");
            return;
        }
        if (TextUtils.isEmpty(email) || !Patterns.EMAIL_ADDRESS.matcher(email).matches()) {
            showError("Please enter a valid email address.");
            return;
        }

        if (!isGoogleAuthenticated) {
            if (TextUtils.isEmpty(password) || password.length() < 6) {
                showError("Password must be at least 6 characters.");
                return;
            }
            if (!password.equals(confirmPassword)) {
                showError("Passwords do not match.");
                return;
            }
        }

        showStep(2);
    }

    private void validateAndProceedStep2() {
        String barangay = actvBarangay.getText().toString().trim();
        if (TextUtils.isEmpty(barangay)) {
            showError("Please select your barangay.");
            return;
        }
        boolean isValidBarangay = false;
        for (String validName : getResources().getStringArray(R.array.qc_barangays)) {
            if (validName.equalsIgnoreCase(barangay)) {
                isValidBarangay = true;
                break;
            }
        }
        if (!isValidBarangay) {
            showError("Please select a valid barangay from the suggestions.");
            return;
        }

        showStep(3);
    }

    private void validateAndProceedStep3() {
        int selectedGardenTypeId = rgGardenType.getCheckedRadioButtonId();
        if (selectedGardenTypeId == -1) {
            showError("Please select your garden type.");
            return;
        }

        showStep(4);
    }

    private void completeRegistrationStep4() {
        List<String> selectedCrops = getSelectedCrops();
        if (selectedCrops.isEmpty()) {
            showError("Please select at least one crop you grow.");
            return;
        }

        String firstName = getText(etFirstName);
        String lastName = getText(etLastName);
        String email = getText(etEmail);
        String password = getText(etPassword);
        String barangay = actvBarangay.getText().toString().trim();

        int selectedGardenTypeId = rgGardenType.getCheckedRadioButtonId();
        String gardenType = ((RadioButton) findViewById(selectedGardenTypeId)).getText().toString();

        hideError();
        setLoading(true);

        FirebaseUser currentUser = mAuth.getCurrentUser();
        if (isGoogleAuthenticated && currentUser != null) {
            // Stage 2: Save completed profile to Firestore database
            saveGardenerProfile(currentUser.getUid(), firstName, lastName, email, barangay, gardenType, selectedCrops, true);
        } else if (currentUser != null) {
            // User authenticated
            saveGardenerProfile(currentUser.getUid(), firstName, lastName, email, barangay, gardenType, selectedCrops, true);
        } else {
            // Create Firebase Email/Password user
            mAuth.createUserWithEmailAndPassword(email, password)
                    .addOnCompleteListener(this, task -> {
                        if (task.isSuccessful()) {
                            FirebaseUser firebaseUser = mAuth.getCurrentUser();
                            if (firebaseUser != null) {
                                saveGardenerProfile(firebaseUser.getUid(), firstName, lastName, email, barangay, gardenType, selectedCrops, true);
                            }
                        } else {
                            setLoading(false);
                            String message = task.getException() != null
                                    ? task.getException().getMessage()
                                    : "Registration failed. Please try again.";
                            showError(message);
                        }
                    });
        }
    }

    private void loadCropsFromFirestore() {
        db.collection("crops").get().addOnSuccessListener(querySnapshot -> {
            List<String> previouslyChecked = getSelectedCrops();
            layoutCropsContainer.removeAllViews();

            ColorStateList greenTint = ColorStateList.valueOf(Color.parseColor("#2E7D32"));

            for (DocumentSnapshot doc : querySnapshot.getDocuments()) {
                Crop crop = doc.toObject(Crop.class);
                if (crop != null) {
                    CheckBox cb = new CheckBox(this);
                    String label = crop.getEmoji() != null ? crop.getEmoji() + " " + crop.getName() : crop.getName();
                    cb.setText(label);
                    cb.setTag(crop.getName());
                    cb.setButtonTintList(greenTint);

                    if (previouslyChecked.contains(crop.getName())) {
                        cb.setChecked(true);
                    }

                    LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                            LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT);
                    params.rightMargin = dpToPx(16);
                    cb.setLayoutParams(params);

                    layoutCropsContainer.addView(cb);
                }
            }
        });
    }

    private List<String> getSelectedCrops() {
        List<String> result = new ArrayList<>();

        CheckBox cbTomato = findViewById(R.id.cbTomato);
        CheckBox cbGreenPepper = findViewById(R.id.cbGreenPepper);
        if (cbTomato != null && cbTomato.isChecked()) {
            if (!result.contains("Tomato")) {
                result.add("Tomato");
            }
        }
        if (cbGreenPepper != null && cbGreenPepper.isChecked()) {
            if (!result.contains("Green Pepper")) {
                result.add("Green Pepper");
            }
        }

        for (int i = 0; i < layoutCropsContainer.getChildCount(); i++) {
            View child = layoutCropsContainer.getChildAt(i);
            if (child instanceof CheckBox) {
                CheckBox cb = (CheckBox) child;
                if (cb.isChecked() && cb.getTag() != null) {
                    String tag = cb.getTag().toString();
                    if (!result.contains(tag)) {
                        result.add(tag);
                    }
                }
            }
        }
        return result;
    }

    private void saveGardenerProfile(String uid, String firstName, String lastName, String email, String barangay,
                                     String gardenType, List<String> crops) {
        saveGardenerProfile(uid, firstName, lastName, email, barangay, gardenType, crops, true);
    }

    private void saveGardenerProfile(String uid, String firstName, String lastName, String email, String barangay,
                                     String gardenType, List<String> crops, boolean navigateToDashboard) {
        Map<String, Object> profile = new HashMap<>();
        profile.put("firstName", firstName);
        profile.put("lastName", lastName);
        profile.put("fullName", (firstName + " " + lastName).trim());
        profile.put("email", email);
        profile.put("barangay", barangay);
        profile.put("gardenType", gardenType);
        profile.put("crops", crops);
        profile.put("role", "gardener");
        profile.put("status", "Active");
        profile.put("sensorDeviceId", "VINLAND-ESP32-01");
        profile.put("createdAt", System.currentTimeMillis());

        db.collection("users").document(uid)
                .set(profile, SetOptions.merge())
                .addOnSuccessListener(unused -> {
                    Log.d(TAG, "Successfully saved gardener profile in Firestore for UID: " + uid);
                    if (navigateToDashboard) {
                        setLoading(false);
                        Toast.makeText(this, "Account registered! Welcome to Vinland.", Toast.LENGTH_SHORT).show();
                        startActivity(new Intent(this, DashboardActivity.class));
                        finish();
                    }
                })
                .addOnFailureListener(e -> {
                    Log.e(TAG, "Failed to save profile in Firestore: " + e.getMessage());
                    if (navigateToDashboard) {
                        setLoading(false);
                        showError("Account registered, but saving your profile failed: " + e.getMessage());
                    }
                });
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
        if (btnRegister != null) btnRegister.setEnabled(!loading);
        if (btnNextStep1 != null) btnNextStep1.setEnabled(!loading);
        if (btnNextStep2 != null) btnNextStep2.setEnabled(!loading);
        if (btnNextStep3 != null) btnNextStep3.setEnabled(!loading);
        if (btnGoogleRegister != null) btnGoogleRegister.setEnabled(!loading);
    }

    private int dpToPx(int dp) {
        float density = getResources().getDisplayMetrics().density;
        return Math.round(dp * density);
    }
}
