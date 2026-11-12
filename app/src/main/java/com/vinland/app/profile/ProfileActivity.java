package com.vinland.app.profile;

import android.content.res.ColorStateList;
import android.graphics.Color;
import android.os.Bundle;
import android.text.TextUtils;
import android.view.View;
import android.view.ViewGroup;
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
import com.google.android.material.chip.ChipGroup;
import com.google.android.material.textfield.TextInputEditText;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FirebaseFirestore;
import com.vinland.app.R;
import com.vinland.app.data.Crop;
import com.vinland.app.data.CropDatabaseSeeder;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class ProfileActivity extends AppCompatActivity {

    private TextView tvEmail, tvError, tvSuccess;
    private TextInputEditText etFirstName, etLastName, etSensorDeviceId;
    private AutoCompleteTextView actvBarangay;
    private RadioGroup rgGardenType;
    private LinearLayout layoutEditButtons;
    private ChipGroup layoutCropsContainer;
    private MaterialButton btnEditProfile, btnCancel, btnSave;
    private ProgressBar progressBar;

    private FirebaseAuth mAuth;
    private FirebaseFirestore db;

    private final List<String> loadedUserCrops = new ArrayList<>();
    private boolean isEditMode = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_profile);

        mAuth = FirebaseAuth.getInstance();
        db = FirebaseFirestore.getInstance();

        TextView tvBack = findViewById(R.id.tvBack);
        tvEmail = findViewById(R.id.tvEmail);
        tvError = findViewById(R.id.tvError);
        tvSuccess = findViewById(R.id.tvSuccess);
        etFirstName = findViewById(R.id.etFirstName);
        etLastName = findViewById(R.id.etLastName);
        etSensorDeviceId = findViewById(R.id.etSensorDeviceId);
        actvBarangay = findViewById(R.id.actvBarangay);
        rgGardenType = findViewById(R.id.rgGardenType);
        layoutCropsContainer = findViewById(R.id.layoutCropsContainer);
        layoutEditButtons = findViewById(R.id.layoutEditButtons);
        btnEditProfile = findViewById(R.id.btnEditProfile);
        btnCancel = findViewById(R.id.btnCancel);
        btnSave = findViewById(R.id.btnSave);
        progressBar = findViewById(R.id.progressBar);

        ArrayAdapter<String> barangayAdapter = new ArrayAdapter<>(
                this,
                android.R.layout.simple_dropdown_item_1line,
                getResources().getStringArray(R.array.qc_barangays)
        );
        actvBarangay.setAdapter(barangayAdapter);

        tvBack.setOnClickListener(v -> finish());
        btnEditProfile.setOnClickListener(v -> setFieldsEditable(true));
        btnCancel.setOnClickListener(v -> {
            setFieldsEditable(false);
            loadProfile(); // Reset unsaved changes
        });
        btnSave.setOnClickListener(v -> saveChanges());

        setFieldsEditable(false); // Default: View-Only Mode
        CropDatabaseSeeder.seedIfEmpty(db, this::loadCropsAndProfile);
    }

    private void setFieldsEditable(boolean editable) {
        isEditMode = editable;
        hideMessages();

        etFirstName.setEnabled(editable);
        etLastName.setEnabled(editable);
        actvBarangay.setEnabled(editable);
        etSensorDeviceId.setEnabled(editable);

        for (int i = 0; i < rgGardenType.getChildCount(); i++) {
            rgGardenType.getChildAt(i).setEnabled(editable);
        }

        for (int i = 0; i < layoutCropsContainer.getChildCount(); i++) {
            layoutCropsContainer.getChildAt(i).setEnabled(editable);
        }

        btnEditProfile.setVisibility(editable ? View.GONE : View.VISIBLE);
        layoutEditButtons.setVisibility(editable ? View.VISIBLE : View.GONE);
    }

    private void loadCropsAndProfile() {
        if (mAuth.getCurrentUser() == null) {
            finish();
            return;
        }

        tvEmail.setText(mAuth.getCurrentUser().getEmail());

        db.collection("crops").get().addOnSuccessListener(querySnapshot -> {
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
                    cb.setEnabled(isEditMode);

                    ViewGroup.MarginLayoutParams params = new ViewGroup.MarginLayoutParams(
                            ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
                    cb.setLayoutParams(params);

                    layoutCropsContainer.addView(cb);
                }
            }

            // After crops are loaded, fetch user profile to mark checkboxes
            loadProfile();
        }).addOnFailureListener(e -> loadProfile());
    }

    private void loadProfile() {
        if (mAuth.getCurrentUser() == null) return;

        db.collection("users").document(mAuth.getCurrentUser().getUid())
                .get()
                .addOnSuccessListener(this::populateForm)
                .addOnFailureListener(e -> showError("Could not load profile: " + e.getMessage()));
    }

    private void populateForm(DocumentSnapshot document) {
        if (!document.exists()) return;

        String firstName = document.getString("firstName");
        String lastName = document.getString("lastName");
        if (TextUtils.isEmpty(firstName) && TextUtils.isEmpty(lastName)) {
            String fullName = document.getString("fullName");
            if (fullName != null) {
                int spaceIdx = fullName.indexOf(' ');
                if (spaceIdx != -1) {
                    firstName = fullName.substring(0, spaceIdx);
                    lastName = fullName.substring(spaceIdx + 1);
                } else {
                    firstName = fullName;
                    lastName = "";
                }
            }
        }
        etFirstName.setText(firstName != null ? firstName : "");
        etLastName.setText(lastName != null ? lastName : "");

        if (document.contains("sensorDeviceId") && etSensorDeviceId != null) {
            etSensorDeviceId.setText(document.getString("sensorDeviceId"));
        }
        actvBarangay.setText(document.getString("barangay"), false);

        String gardenType = document.getString("gardenType");
        if ("Backyard".equalsIgnoreCase(gardenType)) {
            ((RadioButton) findViewById(R.id.rbBackyard)).setChecked(true);
        } else if ("Rooftop".equalsIgnoreCase(gardenType)) {
            ((RadioButton) findViewById(R.id.rbRooftop)).setChecked(true);
        } else if ("Community Plot".equalsIgnoreCase(gardenType)) {
            ((RadioButton) findViewById(R.id.rbCommunityPlot)).setChecked(true);
        }

        loadedUserCrops.clear();
        List<Object> crops = (List<Object>) document.get("crops");
        if (crops != null) {
            for (Object crop : crops) {
                loadedUserCrops.add(crop.toString().toLowerCase());
            }
        }

        for (int i = 0; i < layoutCropsContainer.getChildCount(); i++) {
            View child = layoutCropsContainer.getChildAt(i);
            if (child instanceof CheckBox) {
                CheckBox cb = (CheckBox) child;
                if (cb.getTag() != null && loadedUserCrops.contains(cb.getTag().toString().toLowerCase())) {
                    cb.setChecked(true);
                }
            }
        }
    }

    private void saveChanges() {
        String firstName = getText(etFirstName);
        String lastName = getText(etLastName);
        String barangay = actvBarangay.getText().toString().trim();

        if (TextUtils.isEmpty(firstName)) {
            showError("Please enter your first name.");
            return;
        }
        if (TextUtils.isEmpty(lastName)) {
            showError("Please enter your last name.");
            return;
        }

        boolean isValidBarangay = TextUtils.isEmpty(barangay);
        for (String validName : getResources().getStringArray(R.array.qc_barangays)) {
            if (validName.equalsIgnoreCase(barangay)) {
                isValidBarangay = true;
                barangay = validName;
                break;
            }
        }
        if (TextUtils.isEmpty(barangay) || !isValidBarangay) {
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

        hideMessages();
        setLoading(true);

        String gardenType = ((RadioButton) findViewById(selectedGardenTypeId)).getText().toString();
        String sensorDeviceId = getText(etSensorDeviceId);

        Map<String, Object> updates = new HashMap<>();
        updates.put("firstName", firstName);
        updates.put("lastName", lastName);
        updates.put("fullName", firstName + " " + lastName);
        updates.put("barangay", barangay);
        updates.put("gardenType", gardenType);
        updates.put("crops", selectedCrops);
        updates.put("sensorDeviceId", sensorDeviceId);

        db.collection("users").document(mAuth.getCurrentUser().getUid())
                .update(updates)
                .addOnSuccessListener(unused -> {
                    setLoading(false);
                    setFieldsEditable(false); // Return to View-Only Mode upon success
                    Toast.makeText(this, "✅ Profile updated successfully!", Toast.LENGTH_SHORT).show();
                    showSuccess("Profile updated successfully!");
                })
                .addOnFailureListener(e -> {
                    setLoading(false);
                    showError("Could not save changes: " + e.getMessage());
                });
    }

    private List<String> getSelectedCrops() {
        List<String> result = new ArrayList<>();
        for (int i = 0; i < layoutCropsContainer.getChildCount(); i++) {
            View child = layoutCropsContainer.getChildAt(i);
            if (child instanceof CheckBox) {
                CheckBox cb = (CheckBox) child;
                if (cb.isChecked() && cb.getTag() != null) {
                    result.add(cb.getTag().toString());
                }
            }
        }
        return result;
    }

    private String getText(TextInputEditText field) {
        return field.getText() != null ? field.getText().toString().trim() : "";
    }

    private void showError(String message) {
        tvSuccess.setVisibility(View.GONE);
        tvError.setText(message);
        tvError.setVisibility(View.VISIBLE);
    }

    private void showSuccess(String message) {
        tvError.setVisibility(View.GONE);
        tvSuccess.setText(message);
        tvSuccess.setVisibility(View.VISIBLE);
    }

    private void hideMessages() {
        tvError.setVisibility(View.GONE);
        tvSuccess.setVisibility(View.GONE);
    }

    private void setLoading(boolean loading) {
        progressBar.setVisibility(loading ? View.VISIBLE : View.GONE);
        btnSave.setEnabled(!loading);
        btnCancel.setEnabled(!loading);
    }

    private int dpToPx(int dp) {
        float density = getResources().getDisplayMetrics().density;
        return Math.round(dp * density);
    }
}
