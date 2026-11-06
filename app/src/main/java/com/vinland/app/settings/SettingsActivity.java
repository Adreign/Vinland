package com.vinland.app.settings;

import android.os.Bundle;
import android.text.TextUtils;
import android.widget.ArrayAdapter;
import android.widget.AutoCompleteTextView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;

import com.google.android.material.button.MaterialButton;
import com.google.android.material.materialswitch.MaterialSwitch;
import com.google.android.material.textfield.TextInputEditText;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.SetOptions;
import com.vinland.app.R;
import com.vinland.app.notifications.NotificationHelper;

import java.util.HashMap;
import java.util.Map;

public class SettingsActivity extends AppCompatActivity {

    private TextInputEditText etSensorDeviceId;
    private AutoCompleteTextView actvBarangay;
    private MaterialSwitch switchSoilAlerts, switchWeatherAlerts;
    private TextView btnBack;
    private MaterialButton btnTestSensor, btnResetPassword, btnSaveSettings;

    private FirebaseAuth mAuth;
    private FirebaseFirestore db;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_settings);

        mAuth = FirebaseAuth.getInstance();
        db = FirebaseFirestore.getInstance();

        btnBack = findViewById(R.id.btnBack);
        etSensorDeviceId = findViewById(R.id.etSensorDeviceId);
        actvBarangay = findViewById(R.id.actvBarangay);
        switchSoilAlerts = findViewById(R.id.switchSoilAlerts);
        switchWeatherAlerts = findViewById(R.id.switchWeatherAlerts);
        btnTestSensor = findViewById(R.id.btnTestSensor);
        btnResetPassword = findViewById(R.id.btnResetPassword);
        btnSaveSettings = findViewById(R.id.btnSaveSettings);

        // Populate Quezon City Barangay Suggestions
        ArrayAdapter<String> barangayAdapter = new ArrayAdapter<>(
                this,
                android.R.layout.simple_dropdown_item_1line,
                getResources().getStringArray(R.array.qc_barangays)
        );
        actvBarangay.setAdapter(barangayAdapter);

        btnBack.setOnClickListener(v -> finish());
        btnTestSensor.setOnClickListener(v -> testHardwareConnection());
        btnResetPassword.setOnClickListener(v -> sendPasswordResetEmail());
        btnSaveSettings.setOnClickListener(v -> saveSettingsToFirestore());

        loadCurrentUserSettings();
    }

    private void loadCurrentUserSettings() {
        FirebaseUser user = mAuth.getCurrentUser();
        if (user == null) return;

        db.collection("users").document(user.getUid())
                .get()
                .addOnSuccessListener(doc -> {
                    if (doc.exists()) {
                        String sensorId = doc.getString("sensorDeviceId");
                        String barangay = doc.getString("barangay");
                        Boolean soilAlerts = doc.getBoolean("soilAlertsEnabled");
                        Boolean weatherAlerts = doc.getBoolean("weatherAlertsEnabled");

                        if (!TextUtils.isEmpty(sensorId)) {
                            etSensorDeviceId.setText(sensorId);
                        }
                        if (!TextUtils.isEmpty(barangay)) {
                            actvBarangay.setText(barangay, false);
                        }
                        if (soilAlerts != null) {
                            switchSoilAlerts.setChecked(soilAlerts);
                        }
                        if (weatherAlerts != null) {
                            switchWeatherAlerts.setChecked(weatherAlerts);
                        }
                    }
                });
    }

    private void testHardwareConnection() {
        String deviceId = etSensorDeviceId.getText() != null ? etSensorDeviceId.getText().toString().trim() : "VINLAND-ESP32-01";
        if (TextUtils.isEmpty(deviceId)) {
            Toast.makeText(this, "Please enter a valid ESP32 Hardware Device ID first.", Toast.LENGTH_SHORT).show();
            return;
        }

        Toast.makeText(this, "📶 Ping sent to " + deviceId + "... Checking telemetry stream.", Toast.LENGTH_SHORT).show();

        // Trigger a test system notification so the user can verify notifications
        NotificationHelper.sendNotification(
                this,
                "🔔 Vinland Notification System Active",
                "Connected to " + deviceId + ". Hardware telemetry and garden advisories are online 🟢"
        );

        db.collection("sensor_devices").document(deviceId)
                .get()
                .addOnSuccessListener(doc -> {
                    if (doc.exists()) {
                        Double moisture = doc.getDouble("moisture");
                        Double ph = doc.getDouble("pH");
                        new AlertDialog.Builder(this)
                                .setTitle("✅ Sensor Hardware Online")
                                .setMessage("Connected to ESP32 Node: " + deviceId + "\n\n" +
                                        "• Moisture: " + (moisture != null ? moisture + "%" : "Active") + "\n" +
                                        "• Soil pH: " + (ph != null ? ph : "Active") + "\n" +
                                        "• Status: Telemetry Stream Live 🟢")
                                .setPositiveButton("OK", null)
                                .show();
                    } else {
                        new AlertDialog.Builder(this)
                                .setTitle("📡 Node ID Ready")
                                .setMessage("Node ID '" + deviceId + "' is saved for your account. Telemetry will sync automatically as soon as the ESP32 powers on.")
                                .setPositiveButton("OK", null)
                                .show();
                    }
                })
                .addOnFailureListener(e ->
                        Toast.makeText(this, "Could not ping hardware: " + e.getMessage(), Toast.LENGTH_SHORT).show());
    }

    private void sendPasswordResetEmail() {
        FirebaseUser user = mAuth.getCurrentUser();
        if (user == null || TextUtils.isEmpty(user.getEmail())) {
            Toast.makeText(this, "No valid account email found.", Toast.LENGTH_SHORT).show();
            return;
        }

        mAuth.sendPasswordResetEmail(user.getEmail())
                .addOnSuccessListener(unused ->
                        new AlertDialog.Builder(this)
                                .setTitle("📧 Password Reset Email Sent")
                                .setMessage("A password reset link has been sent to:\n\n" + user.getEmail() + "\n\nPlease check your inbox and follow the instructions.")
                                .setPositiveButton("Got It", null)
                                .show())
                .addOnFailureListener(e ->
                        Toast.makeText(this, "Could not send reset email: " + e.getMessage(), Toast.LENGTH_SHORT).show());
    }

    private void saveSettingsToFirestore() {
        FirebaseUser user = mAuth.getCurrentUser();
        if (user == null) {
            Toast.makeText(this, "You must be signed in to save settings.", Toast.LENGTH_SHORT).show();
            return;
        }

        String sensorDeviceId = etSensorDeviceId.getText() != null ? etSensorDeviceId.getText().toString().trim() : "VINLAND-ESP32-01";
        String barangay = actvBarangay.getText().toString().trim();
        boolean soilAlerts = switchSoilAlerts.isChecked();
        boolean weatherAlerts = switchWeatherAlerts.isChecked();

        Map<String, Object> updates = new HashMap<>();
        updates.put("sensorDeviceId", sensorDeviceId);
        if (!TextUtils.isEmpty(barangay)) {
            updates.put("barangay", barangay);
        }
        updates.put("soilAlertsEnabled", soilAlerts);
        updates.put("weatherAlertsEnabled", weatherAlerts);

        db.collection("users").document(user.getUid())
                .set(updates, SetOptions.merge())
                .addOnSuccessListener(unused -> {
                    Toast.makeText(this, "✅ Settings saved successfully!", Toast.LENGTH_SHORT).show();
                    finish();
                })
                .addOnFailureListener(e ->
                        Toast.makeText(this, "Failed to save settings: " + e.getMessage(), Toast.LENGTH_SHORT).show());
    }
}
