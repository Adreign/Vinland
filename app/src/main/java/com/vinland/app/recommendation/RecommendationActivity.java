package com.vinland.app.recommendation;

import android.os.Bundle;
import android.text.TextUtils;
import android.view.Gravity;
import android.view.View;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;

import com.android.volley.Request;
import com.android.volley.RequestQueue;
import com.android.volley.toolbox.JsonObjectRequest;
import com.android.volley.toolbox.Volley;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.button.MaterialButtonToggleGroup;
import com.google.android.material.card.MaterialCardView;
import com.google.android.material.textfield.TextInputEditText;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.ListenerRegistration;
import com.vinland.app.R;
import com.vinland.app.data.Crop;
import com.vinland.app.data.CropDatabaseSeeder;
import com.vinland.app.data.SoilLog;
import com.vinland.app.weather.WeatherConfig;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public class RecommendationActivity extends AppCompatActivity {

    private TextView tvBack, tvInputError, tvAiAdvice;
    private TextView tvLiveStatusDot, tvLiveStatusText, tvLiveLastUpdated, tvChangeDevice;
    private TextInputEditText etMoisture, etPh, etNitrogen, etPhosphorus, etPotassium;
    private MaterialButton btnAnalyze, btnSaveToHistory;
    private MaterialButtonToggleGroup toggleGroupMode;
    private MaterialCardView cardLiveSensorBanner;
    private LinearLayout layoutResults, layoutParameterResults, layoutRecommendedCrops;
    private ProgressBar progressAiLoading;

    private FirebaseAuth mAuth;
    private FirebaseFirestore db;
    private RequestQueue requestQueue;

    private ListenerRegistration sensorListenerRegistration;
    private String activeSensorDeviceId = "VINLAND-ESP32-01";
    private boolean isAutoMode = false;

    private final Map<String, Crop> cropMap = new HashMap<>();
    private Crop topRecommendedCrop = null;

    private double currentMoisture, currentPh, currentN, currentP, currentK;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_recommendation);

        mAuth = FirebaseAuth.getInstance();
        db = FirebaseFirestore.getInstance();
        requestQueue = Volley.newRequestQueue(this);

        tvBack = findViewById(R.id.tvBack);
        tvInputError = findViewById(R.id.tvInputError);
        etMoisture = findViewById(R.id.etMoisture);
        etPh = findViewById(R.id.etPh);
        etNitrogen = findViewById(R.id.etNitrogen);
        etPhosphorus = findViewById(R.id.etPhosphorus);
        etPotassium = findViewById(R.id.etPotassium);
        btnAnalyze = findViewById(R.id.btnAnalyze);
        btnSaveToHistory = findViewById(R.id.btnSaveToHistory);
        toggleGroupMode = findViewById(R.id.toggleGroupMode);
        cardLiveSensorBanner = findViewById(R.id.cardLiveSensorBanner);
        tvLiveStatusDot = findViewById(R.id.tvLiveStatusDot);
        tvLiveStatusText = findViewById(R.id.tvLiveStatusText);
        tvLiveLastUpdated = findViewById(R.id.tvLiveLastUpdated);
        tvChangeDevice = findViewById(R.id.tvChangeDevice);
        layoutResults = findViewById(R.id.layoutResults);
        layoutParameterResults = findViewById(R.id.layoutParameterResults);
        layoutRecommendedCrops = findViewById(R.id.layoutRecommendedCrops);
        progressAiLoading = findViewById(R.id.progressAiLoading);
        tvAiAdvice = findViewById(R.id.tvAiAdvice);

        tvBack.setOnClickListener(v -> finish());
        btnAnalyze.setOnClickListener(v -> analyzeSoil());
        btnSaveToHistory.setOnClickListener(v -> saveReadingToHistory());
        tvChangeDevice.setOnClickListener(v -> showChangeDeviceDialog());

        toggleGroupMode.addOnButtonCheckedListener((group, checkedId, isChecked) -> {
            if (isChecked) {
                if (checkedId == R.id.btnModeAuto) {
                    switchToAutoMode();
                } else {
                    switchToManualMode();
                }
            }
        });

        loadUserSavedDeviceId();
        CropDatabaseSeeder.seedIfEmpty(db, this::loadCropsFromFirestore);
    }

    private void loadUserSavedDeviceId() {
        if (mAuth.getCurrentUser() == null) return;
        db.collection("users").document(mAuth.getCurrentUser().getUid())
                .get()
                .addOnSuccessListener(document -> {
                    if (document.exists() && document.contains("sensorDeviceId")) {
                        String savedId = document.getString("sensorDeviceId");
                        if (!TextUtils.isEmpty(savedId)) {
                            activeSensorDeviceId = savedId;
                        }
                    }
                });
    }

    private void switchToAutoMode() {
        isAutoMode = true;
        cardLiveSensorBanner.setVisibility(View.VISIBLE);
        setFieldsEditable(false);
        btnAnalyze.setText("🔄 Refresh Analysis from Live Sensor");
        listenToLiveSensorData();
    }

    private void switchToManualMode() {
        isAutoMode = false;
        cardLiveSensorBanner.setVisibility(View.GONE);
        detachSensorListener();
        setFieldsEditable(true);
        btnAnalyze.setText("Analyze Soil");
    }

    private void setFieldsEditable(boolean editable) {
        etMoisture.setFocusable(editable);
        etMoisture.setFocusableInTouchMode(editable);
        etPh.setFocusable(editable);
        etPh.setFocusableInTouchMode(editable);
        etNitrogen.setFocusable(editable);
        etNitrogen.setFocusableInTouchMode(editable);
        etPhosphorus.setFocusable(editable);
        etPhosphorus.setFocusableInTouchMode(editable);
        etPotassium.setFocusable(editable);
        etPotassium.setFocusableInTouchMode(editable);
    }

    private void listenToLiveSensorData() {
        detachSensorListener();
        tvLiveStatusDot.setText("🟡");
        tvLiveStatusText.setText("Connecting to " + activeSensorDeviceId + "...");
        tvLiveLastUpdated.setText("Waiting for sensor telemetry...");

        sensorListenerRegistration = db.collection("sensor_devices")
                .document(activeSensorDeviceId)
                .addSnapshotListener((snapshot, error) -> {
                    if (!isAutoMode) return;

                    if (error != null) {
                        tvLiveStatusDot.setText("🔴");
                        tvLiveStatusText.setText("Connection Error: " + error.getMessage());
                        return;
                    }

                    if (snapshot != null && snapshot.exists()) {
                        tvLiveStatusDot.setText("🟢");
                        tvLiveStatusText.setText("Connected: " + activeSensorDeviceId);

                        Double m = snapshot.getDouble("moisture");
                        Double ph = snapshot.getDouble("ph");
                        Double n = snapshot.getDouble("nitrogen");
                        Double p = snapshot.getDouble("phosphorus");
                        Double k = snapshot.getDouble("potassium");
                        Long ts = snapshot.getLong("timestamp");

                        if (m != null && ph != null && n != null && p != null && k != null) {
                            etMoisture.setText(String.valueOf(m));
                            etPh.setText(String.valueOf(ph));
                            etNitrogen.setText(String.valueOf(n));
                            etPhosphorus.setText(String.valueOf(p));
                            etPotassium.setText(String.valueOf(k));

                            String timeStr = ts != null ? new SimpleDateFormat("MMM dd, hh:mm:ss a", Locale.US).format(new Date(ts)) : "Just now";
                            tvLiveLastUpdated.setText("Last update: " + timeStr);

                            analyzeSoil();
                        } else {
                            tvLiveLastUpdated.setText("Sensor record missing NPK/pH payload values");
                        }
                    } else {
                        tvLiveStatusDot.setText("🟡");
                        tvLiveStatusText.setText("Initializing " + activeSensorDeviceId + "...");
                        tvLiveLastUpdated.setText("Creating default sensor document in Firestore...");
                        seedDefaultSensorDevice(activeSensorDeviceId);
                    }
                });
    }

    private void seedDefaultSensorDevice(String deviceId) {
        Map<String, Object> initialReading = new HashMap<>();
        initialReading.put("moisture", 52.0);
        initialReading.put("ph", 6.6);
        initialReading.put("nitrogen", 35.0);
        initialReading.put("phosphorus", 22.0);
        initialReading.put("potassium", 145.0);
        initialReading.put("timestamp", System.currentTimeMillis());

        db.collection("sensor_devices").document(deviceId)
                .set(initialReading)
                .addOnSuccessListener(unused -> Toast.makeText(this, "Created initial sensor record for " + deviceId, Toast.LENGTH_SHORT).show())
                .addOnFailureListener(e -> {
                    tvLiveStatusDot.setText("🔴");
                    tvLiveStatusText.setText("Could not initialize " + deviceId);
                    tvLiveLastUpdated.setText("Error: " + e.getMessage());
                });
    }

    private void detachSensorListener() {
        if (sensorListenerRegistration != null) {
            sensorListenerRegistration.remove();
            sensorListenerRegistration = null;
        }
    }

    private void showChangeDeviceDialog() {
        final EditText input = new EditText(this);
        input.setHint("e.g. VINLAND-ESP32-01");
        input.setText(activeSensorDeviceId);
        input.setPadding(dpToPx(16), dpToPx(12), dpToPx(16), dpToPx(12));

        new AlertDialog.Builder(this)
                .setTitle("Pair ESP32 Device ID")
                .setMessage("Enter the device ID programmed into your ESP32 microcontroller:")
                .setView(input)
                .setPositiveButton("Pair Device", (dialog, which) -> {
                    String newId = input.getText().toString().trim();
                    if (!TextUtils.isEmpty(newId)) {
                        activeSensorDeviceId = newId;
                        Toast.makeText(this, "Paired with device: " + activeSensorDeviceId, Toast.LENGTH_SHORT).show();
                        if (isAutoMode) {
                            listenToLiveSensorData();
                        }
                    }
                })
                .setNegativeButton("Cancel", (dialog, which) -> dialog.dismiss())
                .show();
    }

    @Override
    protected void onStop() {
        super.onStop();
        detachSensorListener();
    }

    private void loadCropsFromFirestore() {
        db.collection("crops").get().addOnSuccessListener(querySnapshot -> {
            cropMap.clear();
            for (DocumentSnapshot doc : querySnapshot.getDocuments()) {
                Crop crop = doc.toObject(Crop.class);
                if (crop != null) {
                    cropMap.put(crop.getName().toLowerCase(), crop);
                }
            }
        }).addOnFailureListener(e -> {
            Toast.makeText(this, "Could not load crops database", Toast.LENGTH_SHORT).show();
        });
    }

    private void analyzeSoil() {
        String moistureStr = getText(etMoisture);
        String phStr = getText(etPh);
        String nStr = getText(etNitrogen);
        String pStr = getText(etPhosphorus);
        String kStr = getText(etPotassium);

        if (TextUtils.isEmpty(moistureStr) || TextUtils.isEmpty(phStr)
                || TextUtils.isEmpty(nStr) || TextUtils.isEmpty(pStr) || TextUtils.isEmpty(kStr)) {
            showInputError("Please fill in all five soil readings (Moisture, pH, N, P, K).");
            return;
        }

        try {
            currentMoisture = Double.parseDouble(moistureStr);
            currentPh = Double.parseDouble(phStr);
            currentN = Double.parseDouble(nStr);
            currentP = Double.parseDouble(pStr);
            currentK = Double.parseDouble(kStr);
        } catch (NumberFormatException e) {
            showInputError("Please enter valid numbers only.");
            return;
        }

        hideInputError();
        evaluateRecommendedCrops(currentMoisture, currentPh, currentN, currentP, currentK);
        showGeneralParameterResults(currentMoisture, currentPh, currentN, currentP, currentK);
        fetchGeminiAiRecommendation(currentMoisture, currentPh, currentN, currentP, currentK);
    }

    private void evaluateRecommendedCrops(double moisture, double ph, double n, double p, double k) {
        if (layoutRecommendedCrops == null) return;
        layoutRecommendedCrops.removeAllViews();

        List<CropMatch> matches = new ArrayList<>();
        for (Crop crop : cropMap.values()) {
            int score = 0;
            if (ph >= crop.getPhMin() - 0.5 && ph <= crop.getPhMax() + 0.5) score++;
            if (moisture >= crop.getMoistureMin() - 10 && moisture <= crop.getMoistureMax() + 10) score++;
            if (n >= crop.getNitrogenMin() * 0.6 && n <= crop.getNitrogenMax() * 1.4) score++;
            if (p >= crop.getPhosphorusMin() * 0.6 && p <= crop.getPhosphorusMax() * 1.4) score++;
            if (k >= crop.getPotassiumMin() * 0.6 && k <= crop.getPotassiumMax() * 1.4) score++;

            matches.add(new CropMatch(crop, score));
        }

        matches.sort((a, b) -> Integer.compare(b.score, a.score));

        int count = 0;
        for (CropMatch match : matches) {
            if (match.score >= 2) {
                addCropMatchCard(layoutRecommendedCrops, match.crop, match.score);
                if (count == 0) {
                    topRecommendedCrop = match.crop;
                }
                count++;
                if (count >= 4) break;
            }
        }

        if (count == 0 && !matches.isEmpty()) {
            for (int i = 0; i < Math.min(2, matches.size()); i++) {
                addCropMatchCard(layoutRecommendedCrops, matches.get(i).crop, matches.get(i).score);
                if (i == 0) topRecommendedCrop = matches.get(0).crop;
            }
        }

        if (topRecommendedCrop == null && !cropMap.isEmpty()) {
            topRecommendedCrop = cropMap.values().iterator().next();
        }

        layoutResults.setVisibility(View.VISIBLE);
    }

    private static class CropMatch {
        Crop crop;
        int score;
        CropMatch(Crop crop, int score) {
            this.crop = crop;
            this.score = score;
        }
    }

    private void addCropMatchCard(LinearLayout container, Crop crop, int score) {
        MaterialCardView card = new MaterialCardView(this);
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        params.bottomMargin = dpToPx(8);
        card.setLayoutParams(params);
        card.setRadius(dpToPx(12));
        card.setCardElevation(0);
        card.setCardBackgroundColor(android.graphics.Color.WHITE);

        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setPadding(dpToPx(14), dpToPx(12), dpToPx(14), dpToPx(12));
        row.setGravity(Gravity.CENTER_VERTICAL);

        TextView tvEmoji = new TextView(this);
        tvEmoji.setText(crop.getEmoji() != null ? crop.getEmoji() : "🌱");
        tvEmoji.setTextSize(26);
        tvEmoji.setPadding(0, 0, dpToPx(12), 0);
        row.addView(tvEmoji);

        LinearLayout textCol = new LinearLayout(this);
        textCol.setOrientation(LinearLayout.VERTICAL);
        textCol.setLayoutParams(new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1));

        TextView tvName = new TextView(this);
        tvName.setText(crop.getName());
        tvName.setTextSize(14);
        tvName.setTypeface(null, android.graphics.Typeface.BOLD);
        tvName.setTextColor(android.graphics.Color.parseColor("#212121"));

        String matchText = score >= 4 ? "⭐ Excellent Match (" + score + "/5 optimal parameters)" : "👍 Compatible Match (" + score + "/5 optimal)";
        TextView tvMatch = new TextView(this);
        tvMatch.setText(matchText);
        tvMatch.setTextSize(12);
        tvMatch.setTextColor(android.graphics.Color.parseColor("#2E7D32"));
        tvMatch.setTypeface(null, android.graphics.Typeface.BOLD);

        textCol.addView(tvName);
        textCol.addView(tvMatch);
        row.addView(textCol);

        card.addView(row);
        container.addView(card);
    }

    private void showGeneralParameterResults(double moisture, double ph, double n, double p, double k) {
        layoutParameterResults.removeAllViews();

        addResultRow("💧 Moisture", moisture + "%", classifyGeneralMoisture(moisture));
        addResultRow("⚗️ pH Level", String.valueOf(ph), classifyGeneralPh(ph));
        addResultRow("🌿 Nitrogen (N)", n + " ppm", classifyGeneralNutrient(n, 100, 250, "Nitrogen"));
        addResultRow("🌿 Phosphorus (P)", p + " ppm", classifyGeneralNutrient(p, 50, 150, "Phosphorus"));
        addResultRow("🌿 Potassium (K)", k + " ppm", classifyGeneralNutrient(k, 100, 300, "Potassium"));
    }

    private String[] classifyGeneralMoisture(double m) {
        if (m < 35) return new String[]{"Too Dry", "Moisture is low. Water soil thoroughly.", "#D32F2F"};
        if (m > 80) return new String[]{"Too Wet", "Moisture is very high. Ensure drainage.", "#F9A825"};
        return new String[]{"Optimal", "Moisture is in a healthy growing range.", "#2E7D32"};
    }

    private String[] classifyGeneralPh(double ph) {
        if (ph < 5.5) return new String[]{"Too Acidic", "Consider adding garden lime to raise pH.", "#D32F2F"};
        if (ph > 7.5) return new String[]{"Too Alkaline", "Consider adding organic compost to lower pH.", "#F9A825"};
        return new String[]{"Optimal", "pH is neutral and ideal for most vegetables.", "#2E7D32"};
    }

    private String[] classifyGeneralNutrient(double val, double min, double max, String name) {
        if (val < min) return new String[]{"Deficient", name + " is low. Consider organic fertilizer.", "#D32F2F"};
        if (val > max) return new String[]{"High", name + " is high. Reduce fertilization.", "#F9A825"};
        return new String[]{"Optimal", name + " level is balanced.", "#2E7D32"};
    }

    private void fetchGeminiAiRecommendation(double moisture, double ph, double n, double p, double k) {
        progressAiLoading.setVisibility(View.VISIBLE);
        tvAiAdvice.setText("Consulting Gemini AI for crop recommendations based on your soil...");

        String prompt = "You are an expert AI agricultural agronomist for urban gardening in Quezon City, Philippines. "
                + "A gardener tested their soil with these NPK sensor readings: "
                + "Soil Moisture: " + moisture + "%, pH: " + ph + ", Nitrogen (N): " + n + " ppm, Phosphorus (P): " + p + " ppm, Potassium (K): " + k + " ppm. "
                + "Recommend 2 to 3 best crops to plant in these soil conditions and give brief practical advice on watering or soil amendments for Quezon City.";

        String url = "https://generativelanguage.googleapis.com/v1beta/models/gemini-2.5-flash:generateContent?key=" + WeatherConfig.GEMINI_API_KEY;

        try {
            JSONObject jsonBody = new JSONObject();
            JSONArray contents = new JSONArray();
            JSONObject contentObj = new JSONObject();
            JSONArray parts = new JSONArray();
            JSONObject partObj = new JSONObject();

            partObj.put("text", prompt);
            parts.put(partObj);
            contentObj.put("parts", parts);
            contents.put(contentObj);
            jsonBody.put("contents", contents);

            JsonObjectRequest request = new JsonObjectRequest(
                    Request.Method.POST,
                    url,
                    jsonBody,
                    response -> {
                        progressAiLoading.setVisibility(View.GONE);
                        try {
                            JSONArray candidates = response.optJSONArray("candidates");
                            if (candidates != null && candidates.length() > 0) {
                                JSONObject content = candidates.getJSONObject(0).optJSONObject("content");
                                if (content != null) {
                                    JSONArray resParts = content.optJSONArray("parts");
                                    if (resParts != null && resParts.length() > 0) {
                                        String aiText = resParts.getJSONObject(0).optString("text", "");
                                        tvAiAdvice.setText(aiText.trim());
                                        return;
                                    }
                                }
                            }
                            tvAiAdvice.setText("Soil and crop matches evaluated successfully.");
                        } catch (JSONException e) {
                            tvAiAdvice.setText("Soil and crop matches evaluated successfully.");
                        }
                    },
                    error -> {
                        progressAiLoading.setVisibility(View.GONE);
                        tvAiAdvice.setText("Soil and crop matches evaluated successfully.");
                    }
            );

            requestQueue.add(request);

        } catch (JSONException e) {
            progressAiLoading.setVisibility(View.GONE);
            tvAiAdvice.setText("Soil and crop matches evaluated successfully.");
        }
    }

    private void saveReadingToHistory() {
        if (mAuth.getCurrentUser() == null) return;
        String uid = mAuth.getCurrentUser().getUid();
        String cropName = topRecommendedCrop != null ? topRecommendedCrop.getName() : "General Soil Test";

        String logId = db.collection("users").document(uid).collection("soil_logs").document().getId();
        SoilLog log = new SoilLog(
                logId,
                cropName,
                currentMoisture,
                currentPh,
                currentN,
                currentP,
                currentK,
                "Soil Test & Recommendation Match",
                System.currentTimeMillis()
        );

        db.collection("users").document(uid).collection("soil_logs").document(logId)
                .set(log)
                .addOnSuccessListener(unused -> Toast.makeText(this, "Soil reading saved to history!", Toast.LENGTH_SHORT).show())
                .addOnFailureListener(e -> Toast.makeText(this, "Could not save reading: " + e.getMessage(), Toast.LENGTH_SHORT).show());
    }

    private void addResultRow(String label, String reading, String[] statusData) {
        String status = statusData[0];
        String explanation = statusData[1];
        String color = statusData[2];

        MaterialCardView card = new MaterialCardView(this);
        LinearLayout.LayoutParams cardParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        cardParams.bottomMargin = dpToPx(10);
        card.setLayoutParams(cardParams);
        card.setRadius(dpToPx(14));
        card.setCardElevation(0);
        card.setCardBackgroundColor(android.graphics.Color.WHITE);

        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setPadding(dpToPx(16), dpToPx(14), dpToPx(16), dpToPx(14));
        row.setGravity(Gravity.CENTER_VERTICAL);

        View accent = new View(this);
        LinearLayout.LayoutParams accentParams = new LinearLayout.LayoutParams(dpToPx(4), LinearLayout.LayoutParams.MATCH_PARENT);
        accentParams.rightMargin = dpToPx(12);
        accent.setLayoutParams(accentParams);
        accent.setBackgroundColor(android.graphics.Color.parseColor(color));
        row.addView(accent);

        LinearLayout textColumn = new LinearLayout(this);
        textColumn.setOrientation(LinearLayout.VERTICAL);
        textColumn.setLayoutParams(new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1));

        TextView tvLabel = new TextView(this);
        tvLabel.setText(label + "  (" + reading + ")");
        tvLabel.setTextSize(13);
        tvLabel.setTextColor(android.graphics.Color.parseColor("#212121"));
        tvLabel.setTypeface(null, android.graphics.Typeface.BOLD);

        TextView tvStatus = new TextView(this);
        tvStatus.setText(status);
        tvStatus.setTextSize(13);
        tvStatus.setTextColor(android.graphics.Color.parseColor(color));
        tvStatus.setTypeface(null, android.graphics.Typeface.BOLD);
        tvStatus.setPadding(0, dpToPx(2), 0, dpToPx(2));

        TextView tvExplanation = new TextView(this);
        tvExplanation.setText(explanation);
        tvExplanation.setTextSize(12);
        tvExplanation.setTextColor(android.graphics.Color.parseColor("#666666"));

        textColumn.addView(tvLabel);
        textColumn.addView(tvStatus);
        textColumn.addView(tvExplanation);
        row.addView(textColumn);

        card.addView(row);
        layoutParameterResults.addView(card);
    }

    private int dpToPx(int dp) {
        float density = getResources().getDisplayMetrics().density;
        return Math.round(dp * density);
    }

    private String getText(TextInputEditText field) {
        return field.getText() != null ? field.getText().toString().trim() : "";
    }

    private void showInputError(String message) {
        tvInputError.setText(message);
        tvInputError.setVisibility(View.VISIBLE);
    }

    private void hideInputError() {
        tvInputError.setVisibility(View.GONE);
    }
}
