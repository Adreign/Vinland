package com.vinland.app.auth;

import android.content.Intent;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.os.Bundle;
import android.text.TextUtils;
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

import androidx.appcompat.app.AppCompatActivity;

import com.google.android.material.button.MaterialButton;
import com.google.android.material.textfield.TextInputEditText;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FirebaseFirestore;
import com.vinland.app.R;
import com.vinland.app.dashboard.DashboardActivity;
import com.vinland.app.data.Crop;
import com.vinland.app.data.CropDatabaseSeeder;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class RegisterActivity extends AppCompatActivity {

    private TextInputEditText etFirstName, etLastName, etEmail, etPassword, etConfirmPassword;
    private AutoCompleteTextView actvBarangay;
    private RadioGroup rgGardenType;
    private LinearLayout layoutCropsContainer;
    private MaterialButton btnRegister;
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

        // Bind views
        etFirstName = findViewById(R.id.etFirstName);
        etLastName = findViewById(R.id.etLastName);
        etEmail = findViewById(R.id.etEmail);
        etPassword = findViewById(R.id.etPassword);
        etConfirmPassword = findViewById(R.id.etConfirmPassword);
        actvBarangay = findViewById(R.id.actvBarangay);
        rgGardenType = findViewById(R.id.rgGardenType);
        layoutCropsContainer = findViewById(R.id.layoutCropsContainer);
        btnRegister = findViewById(R.id.btnRegister);
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

        btnRegister.setOnClickListener(v -> attemptRegister());

        tvGoToLogin.setOnClickListener(v -> {
            startActivity(new Intent(this, LoginActivity.class));
            finish();
        });

        CropDatabaseSeeder.seedIfEmpty(db, this::loadCropsFromFirestore);
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

    private void attemptRegister() {
        String firstName = getText(etFirstName);
        String lastName = getText(etLastName);
        String email = getText(etEmail);
        String password = getText(etPassword);
        String confirmPassword = getText(etConfirmPassword);
        String barangay = actvBarangay.getText().toString().trim();

        // --- Validation ---
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
        if (TextUtils.isEmpty(password) || password.length() < 6) {
            showError("Password must be at least 6 characters.");
            return;
        }
        if (!password.equals(confirmPassword)) {
            showError("Passwords do not match.");
            return;
        }
        if (TextUtils.isEmpty(barangay)) {
            showError("Please select your barangay.");
            return;
        }
        boolean isValidBarangay = false;
        for (String validName : getResources().getStringArray(R.array.qc_barangays)) {
            if (validName.equalsIgnoreCase(barangay)) {
                isValidBarangay = true;
                barangay = validName; // normalize casing
                break;
            }
        }
        if (!isValidBarangay) {
            showError("Please select a valid barangay from the suggestions.");
            return;
        }
        int selectedGardenTypeId = rgGardenType.getCheckedRadioButtonId();
        if (selectedGardenTypeId == -1) {
            showError("Please select your garden type.");
            return;
        }

        List<String> selectedCrops = getSelectedCrops();
        if (selectedCrops.isEmpty()) {
            showError("Please select at least one crop you grow.");
            return;
        }

        hideError();
        setLoading(true);

        final String finalBarangay = barangay;
        String gardenType = ((RadioButton) findViewById(selectedGardenTypeId)).getText().toString();

        // --- Firebase Auth: create account ---
        mAuth.createUserWithEmailAndPassword(email, password)
                .addOnCompleteListener(this, task -> {
                    if (task.isSuccessful()) {
                        FirebaseUser firebaseUser = mAuth.getCurrentUser();
                        if (firebaseUser != null) {
                            saveGardenerProfile(firebaseUser.getUid(), firstName, lastName, email, finalBarangay, gardenType, selectedCrops);
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

    private List<String> getSelectedCrops() {
        List<String> result = new ArrayList<>();

        // Also check static XML checkboxes if present
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

        // Check dynamic checkboxes in container
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
        Map<String, Object> profile = new HashMap<>();
        profile.put("firstName", firstName);
        profile.put("lastName", lastName);
        profile.put("fullName", firstName + " " + lastName);
        profile.put("email", email);
        profile.put("barangay", barangay);
        profile.put("gardenType", gardenType);
        profile.put("crops", crops);
        profile.put("sensorDeviceId", null);
        profile.put("latitude", null);
        profile.put("longitude", null);
        profile.put("fcmToken", null);
        profile.put("createdAt", System.currentTimeMillis());

        db.collection("users").document(uid)
                .set(profile)
                .addOnSuccessListener(unused -> {
                    setLoading(false);
                    Toast.makeText(this, "Account created! Welcome to Vinland.", Toast.LENGTH_SHORT).show();
                    startActivity(new Intent(this, DashboardActivity.class));
                    finish();
                })
                .addOnFailureListener(e -> {
                    setLoading(false);
                    showError("Account created, but saving your profile failed: " + e.getMessage());
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
        btnRegister.setEnabled(!loading);
    }

    private int dpToPx(int dp) {
        float density = getResources().getDisplayMetrics().density;
        return Math.round(dp * density);
    }
}
