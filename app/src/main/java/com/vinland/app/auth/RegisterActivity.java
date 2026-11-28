package com.vinland.app.auth;

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

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;

import com.google.android.material.button.MaterialButton;
import com.google.android.material.progressindicator.LinearProgressIndicator;
import com.google.android.material.textfield.TextInputEditText;
import com.google.android.material.textfield.TextInputLayout;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseAuthUserCollisionException;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.SetOptions;
import com.vinland.app.R;
import com.vinland.app.dashboard.DashboardActivity;
import com.vinland.app.data.Crop;
import com.vinland.app.data.CropDatabaseSeeder;

import java.util.ArrayList;
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
    private MaterialButton btnNextStep1, btnNextStep2, btnNextStep3, btnRegister;
    private MaterialButton btnBackStep2, btnBackStep3, btnBackStep4;
    private TextView tvStepIndicator;
    private LinearProgressIndicator progressStep;
    private ProgressBar progressBar;
    private TextView tvError, tvGoToLogin;

    private FirebaseAuth mAuth;
    private FirebaseFirestore db;

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
        setupPasswordHint();
        actvBarangay = findViewById(R.id.actvBarangay);
        rgGardenType = findViewById(R.id.rgGardenType);
        layoutCropsContainer = findViewById(R.id.layoutCropsContainer);

        // Bind action buttons
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

        // Barangay type-ahead suggestions
        ArrayAdapter<String> barangayAdapter = new ArrayAdapter<>(
                this,
                android.R.layout.simple_dropdown_item_1line,
                getResources().getStringArray(R.array.qc_barangays)
        );
        actvBarangay.setAdapter(barangayAdapter);

        // Navigation click listeners
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

    private void validateAndProceedStep1() {
        String firstName = getText(etFirstName);
        String lastName = getText(etLastName);
        String email = getText(etEmail);
        String rawPassword = etPassword.getText() != null ? etPassword.getText().toString() : "";
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

        String passwordProblem = getPasswordProblem(rawPassword);
        if (passwordProblem != null) {
            showError(passwordProblem);
            return;
        }
        if (!password.equals(confirmPassword)) {
            showError("Passwords do not match.");
            return;
        }

        showStep(2);
    }

    private static final int MIN_PASSWORD_LENGTH = 8;
    private static final String PASSWORD_RULES_HINT = "At least 8 characters, with a letter and a number";

    /** Returns what's wrong with the password, or null if it's fine. */
    private static String getPasswordProblem(String password) {
        if (TextUtils.isEmpty(password)) {
            return "Please create a password.";
        }
        if (!password.equals(password.trim())) {
            return "Password can't start or end with a space.";
        }
        if (password.length() < MIN_PASSWORD_LENGTH) {
            return "Password must be at least " + MIN_PASSWORD_LENGTH + " characters.";
        }
        if (!password.matches(".*[A-Za-z].*")) {
            return "Password must include at least one letter.";
        }
        if (!password.matches(".*[0-9].*")) {
            return "Password must include at least one number.";
        }
        return null;
    }

    /** Shows the password rules under the box and updates them as the user types. */
    private void setupPasswordHint() {
        tilPassword.setHelperText(PASSWORD_RULES_HINT);
        etPassword.addTextChangedListener(new android.text.TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) { }
            @Override public void onTextChanged(CharSequence s, int start, int before, int count) { }

            @Override
            public void afterTextChanged(android.text.Editable s) {
                String text = s.toString();
                if (text.isEmpty()) {
                    tilPassword.setHelperText(PASSWORD_RULES_HINT);
                    return;
                }
                String problem = getPasswordProblem(text);
                tilPassword.setHelperText(problem == null ? "✅ Password looks good" : "⚠️ " + problem);
            }
        });
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

        if (currentUser != null && currentUser.getEmail() != null && currentUser.getEmail().equalsIgnoreCase(email)) {
            // Already signed in with this email -> Save/update profile directly in Firestore
            saveGardenerProfile(currentUser.getUid(), firstName, lastName, email, barangay, gardenType, selectedCrops, true);
        } else {
            // Attempt Firebase Email/Password creation
            mAuth.createUserWithEmailAndPassword(email, password)
                    .addOnCompleteListener(this, task -> {
                        if (task.isSuccessful()) {
                            FirebaseUser firebaseUser = mAuth.getCurrentUser();
                            if (firebaseUser != null) {
                                saveGardenerProfile(firebaseUser.getUid(), firstName, lastName, email, barangay, gardenType, selectedCrops, true);
                            }
                        } else {
                            Exception e = task.getException();
                            if (e instanceof FirebaseAuthUserCollisionException || (e != null && e.getMessage() != null && e.getMessage().contains("already in use"))) {
                                // Email ALREADY exists in Firebase Authentication!
                                // Attempt sign in with password to associate profile or update Firestore doc
                                mAuth.signInWithEmailAndPassword(email, password)
                                        .addOnSuccessListener(authResult -> {
                                            FirebaseUser u = authResult.getUser();
                                            if (u != null) {
                                                saveGardenerProfile(u.getUid(), firstName, lastName, email, barangay, gardenType, selectedCrops, true);
                                            } else {
                                                setLoading(false);
                                                showError("This email is already registered. Please tap 'Log in' below to sign in.");
                                            }
                                        })
                                        .addOnFailureListener(authErr -> {
                                            setLoading(false);
                                            showError("This email address is already registered in Vinland. Please tap 'Log in' below to sign in.");
                                        });
                            } else {
                                setLoading(false);
                                String message = e != null ? e.getMessage() : "Registration failed. Please try again.";
                                showError(message);
                            }
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
        profile.put("createdAt", System.currentTimeMillis());

        db.collection("users").document(uid)
                .set(profile, SetOptions.merge())
                .addOnSuccessListener(unused -> {
                    Log.d(TAG, "Successfully saved gardener profile in Firestore for UID: " + uid);
                    if (navigateToDashboard) {
                        setLoading(false);
                        FirebaseUser firebaseUser = mAuth.getCurrentUser();

                        if (firebaseUser != null && !firebaseUser.isEmailVerified()) {
                            // Send Email Verification Link for Email/Password registration
                            firebaseUser.sendEmailVerification();

                            new AlertDialog.Builder(RegisterActivity.this)
                                    .setTitle("📧 Verify Your Email Address")
                                    .setMessage("Account registered successfully!\n\nWe have sent a verification link to your email:\n" + email + "\n\nPlease check your email inbox and click the link to verify your account before logging in.")
                                    .setPositiveButton("Go to Login", (dialog, which) -> {
                                        mAuth.signOut();
                                        Intent intent = new Intent(RegisterActivity.this, LoginActivity.class);
                                        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
                                        startActivity(intent);
                                        finish();
                                    })
                                    .setCancelable(false)
                                    .show();
                        } else {
                            // Already verified -> Go to dashboard
                            Toast.makeText(this, "Account registered! Welcome to Vinland.", Toast.LENGTH_SHORT).show();
                            startActivity(new Intent(this, DashboardActivity.class));
                            finish();
                        }
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
    }

    private int dpToPx(int dp) {
        float density = getResources().getDisplayMetrics().density;
        return Math.round(dp * density);
    }
}
