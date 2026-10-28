package com.vinland.app.recommendation;

import android.content.Context;
import android.content.SharedPreferences;
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
    private TextView tvSummaryBadge, tvSummaryKeyFindings, tvSummaryTopCropName, tvSummaryActionNeeded;
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

        tvSummaryBadge = findViewById(R.id.tvSummaryBadge);
        tvSummaryKeyFindings = findViewById(R.id.tvSummaryKeyFindings);
        tvSummaryTopCropName = findViewById(R.id.tvSummaryTopCropName);
        tvSummaryActionNeeded = findViewById(R.id.tvSummaryActionNeeded);

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

    private String getEffectiveGeminiApiKey() {
        SharedPreferences prefs = getSharedPreferences("VinlandPrefs", Context.MODE_PRIVATE);
        String customKey = prefs.getString("gemini_api_key", "");
        if (!TextUtils.isEmpty(customKey)) {
            return customKey;
        }
        return WeatherConfig.GEMINI_API_KEY;
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
        btnAnalyze.setText("✨ Generate Recommendation");
        listenToLiveSensorData();
    }

    private void switchToManualMode() {
        isAutoMode = false;
        cardLiveSensorBanner.setVisibility(View.GONE);
        detachSensorListener();
        setFieldsEditable(true);
        btnAnalyze.setText("✨ Generate Recommendation");
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

                            String timeStr;
                            if (ts != null && ts > 100000000000L) {
                                timeStr = new SimpleDateFormat("MMM dd, hh:mm:ss a", Locale.US).format(new Date(ts));
                            } else {
                                timeStr = "Just now (" + new SimpleDateFormat("hh:mm:ss a", Locale.US).format(new Date()) + ")";
                            }
                            tvLiveLastUpdated.setText("Last update: " + timeStr);
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
        updateExecutiveSummary(currentMoisture, currentPh, currentN, currentP, currentK);
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

        String activeApiKey = getEffectiveGeminiApiKey();

        String prompt = "You are an expert AI agricultural agronomist and master urban gardener in Quezon City, Philippines. "
                + "A local gardener tested their soil and provided these exact sensor readings: "
                + "• Soil Moisture: " + moisture + "% "
                + "• Soil pH: " + ph + " "
                + "• Nitrogen (N): " + n + " ppm "
                + "• Phosphorus (P): " + p + " ppm "
                + "• Potassium (K): " + k + " ppm. "
                + "Please provide a detailed, comprehensive agronomist report structured as follows:\n"
                + "1. **Soil Health Breakdown**: Analyze each parameter (moisture, pH, N, P, K) and explain what it means for plant growth in tropical Quezon City conditions.\n"
                + "2. **Top Crop Recommendations**: Recommend 2 to 3 specific crops that thrive under these exact conditions, explaining why they are a good match.\n"
                + "3. **Actionable Soil Amendments & Fertilizers**: Provide specific, practical steps on what organic amendments, fertilizers, compost, or pH balancers to add.\n"
                + "4. **Watering & Container Advice**: Give practical watering schedules and container/drainage management tips tailored for urban gardeners in Quezon City (handling wet and dry seasons).";

        String url = "https://generativelanguage.googleapis.com/v1beta/models/gemini-1.5-flash:generateContent?key=" + activeApiKey;

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
                                        if (!TextUtils.isEmpty(aiText)) {
                                            displayFormattedAiText(tvAiAdvice, aiText.trim());
                                            return;
                                        }
                                    }
                                }
                            }
                            displayFormattedAiText(tvAiAdvice, generateLocalAgronomistReport(moisture, ph, n, p, k));
                        } catch (JSONException e) {
                            displayFormattedAiText(tvAiAdvice, generateLocalAgronomistReport(moisture, ph, n, p, k));
                        }
                    },
                    error -> {
                        progressAiLoading.setVisibility(View.GONE);
                        displayFormattedAiText(tvAiAdvice, generateLocalAgronomistReport(moisture, ph, n, p, k));
                    }
            );

            requestQueue.add(request);

        } catch (JSONException e) {
            progressAiLoading.setVisibility(View.GONE);
            displayFormattedAiText(tvAiAdvice, generateLocalAgronomistReport(moisture, ph, n, p, k));
        }
    }

    private String generateLocalAgronomistReport(double moisture, double ph, double n, double p, double k) {
        StringBuilder report = new StringBuilder();
        report.append("<b>🌿 Vinland AI Agronomist Soil Analysis Report</b><br/><br/>");

        // 1. Soil Health Breakdown
        report.append("<b>1. Soil Health Breakdown:</b><br/>");
        report.append("• <b>Soil Moisture (").append(moisture).append("%):</b> ");
        if (moisture < 35) {
            report.append("Low moisture level. Your soil is dry and requires immediate deep watering.<br/>");
        } else if (moisture > 80) {
            report.append("High moisture level. Ensure pot drainage holes are clear to prevent root rot.<br/>");
        } else {
            report.append("Optimal soil moisture level for tropical urban gardening.<br/>");
        }

        report.append("• <b>Soil pH (").append(ph).append("):</b> ");
        if (ph < 5.5) {
            report.append("Acidic soil. Apply agricultural lime (calcium carbonate) to balance pH.<br/>");
        } else if (ph > 7.5) {
            report.append("Alkaline soil. Add organic vermicompost to lower pH.<br/>");
        } else {
            report.append("Ideal neutral pH for Quezon City vegetable crops.<br/>");
        }

        report.append("• <b>NPK Nutrients:</b> ");
        report.append("Nitrogen ").append(n < 100 ? "Deficient" : n > 250 ? "High" : "Optimal").append(" (").append(n).append(" ppm), ");
        report.append("Phosphorus ").append(p < 50 ? "Deficient" : p > 150 ? "High" : "Optimal").append(" (").append(p).append(" ppm), ");
        report.append("Potassium ").append(k < 100 ? "Deficient" : k > 300 ? "High" : "Optimal").append(" (").append(k).append(" ppm).<br/><br/>");

        // 2. Crop Match
        report.append("<b>2. Top Recommended Crops:</b><br/>");
        String topName = topRecommendedCrop != null ? topRecommendedCrop.getName() : "Tomato & Green Pepper";
        report.append("• <b>").append(topName).append(":</b> Highly recommended for current soil and climate conditions in Quezon City.<br/><br/>");

        // 3. Soil Amendments
        report.append("<b>3. Actionable Soil Care Steps:</b><br/>");
        if (n < 100) {
            report.append("• Apply organic vermicompost or fermented plant juice (FPJ) to boost Nitrogen.<br/>");
        }
        if (p < 50) {
            report.append("• Apply steamed bone meal or fermented fruit juice (FFJ) for root development.<br/>");
        }
        if (k < 100) {
            report.append("• Apply banana peel liquid tea or wood ash to supply Potassium.<br/>");
        }
        if (ph < 5.5) {
            report.append("• Top-dress 1 tablespoon of agricultural lime per pot.<br/>");
        } else if (ph > 7.5) {
            report.append("• Mix in rich organic compost to lower alkaline pH.<br/>");
        } else if (n >= 100 && p >= 50 && k >= 100) {
            report.append("• Your soil nutrient levels are well-balanced! Maintain regular organic top-dressing.<br/>");
        }

        report.append("<br/><b>4. Watering Guidance:</b><br/>");
        if (moisture < 35) {
            report.append("• Water twice daily in early morning (6:30 AM) and late afternoon (5:00 PM).");
        } else if (moisture > 80) {
            report.append("• Pause irrigation until the top 1-2 inches of soil feel dry to the touch.");
        } else {
            report.append("• Maintain regular morning watering schedules.");
        }

        return report.toString();
    }

    private void updateExecutiveSummary(double moisture, double ph, double n, double p, double k) {
        int optimalCount = 0;
        if (moisture >= 35 && moisture <= 80) optimalCount++;
        if (ph >= 5.5 && ph <= 7.5) optimalCount++;
        if (n >= 100 && n <= 250) optimalCount++;
        if (p >= 50 && p <= 150) optimalCount++;
        if (k >= 100 && k <= 300) optimalCount++;

        if (tvSummaryBadge != null) {
            if (optimalCount >= 4) {
                tvSummaryBadge.setText("🟢 Optimal Health (" + optimalCount + "/5)");
                tvSummaryBadge.setBackgroundColor(android.graphics.Color.parseColor("#2E7D32"));
            } else if (optimalCount >= 2) {
                tvSummaryBadge.setText("🟡 Fair Condition (" + optimalCount + "/5)");
                tvSummaryBadge.setBackgroundColor(android.graphics.Color.parseColor("#F57C00"));
            } else {
                tvSummaryBadge.setText("🔴 Action Needed (" + optimalCount + "/5)");
                tvSummaryBadge.setBackgroundColor(android.graphics.Color.parseColor("#D32F2F"));
            }
        }

        StringBuilder findings = new StringBuilder();
        findings.append("• Moisture: ").append(moisture < 35 ? "Low (" + moisture + "%). Needs thorough watering." : moisture > 80 ? "High (" + moisture + "%). Ensure soil drainage." : "Optimal (" + moisture + "%). Healthy range.").append("\n");
        findings.append("• Soil pH: ").append(ph < 5.5 ? "Acidic (" + ph + "). Add agricultural lime." : ph > 7.5 ? "Alkaline (" + ph + "). Add organic compost." : "Optimal neutral (" + ph + "). Ideal for crops.").append("\n");
        findings.append("• NPK Status: ")
                .append(n < 100 ? "N Deficient, " : n > 250 ? "N High, " : "N Optimal, ")
                .append(p < 50 ? "P Deficient, " : p > 150 ? "P High, " : "P Optimal, ")
                .append(k < 100 ? "K Deficient." : k > 300 ? "K High." : "K Optimal.");

        if (tvSummaryKeyFindings != null) {
            tvSummaryKeyFindings.setText(findings.toString());
        }

        if (tvSummaryTopCropName != null) {
            String cropText = topRecommendedCrop != null ? topRecommendedCrop.getEmoji() + " " + topRecommendedCrop.getName() : "🌱 General Vegetable Crop";
            tvSummaryTopCropName.setText(cropText);
        }

        if (tvSummaryActionNeeded != null) {
            if (ph < 5.5) {
                tvSummaryActionNeeded.setText("Add Agricultural Lime");
            } else if (ph > 7.5) {
                tvSummaryActionNeeded.setText("Add Organic Compost");
            } else if (n < 100) {
                tvSummaryActionNeeded.setText("Apply Organic Nitrogen");
            } else if (moisture < 35) {
                tvSummaryActionNeeded.setText("Increase Irrigation");
            } else if (moisture > 80) {
                tvSummaryActionNeeded.setText("Improve Soil Drainage");
            } else {
                tvSummaryActionNeeded.setText("Maintain Current Care");
            }
        }
    }

    private void displayFormattedAiText(TextView textView, String rawText) {
        if (rawText == null || textView == null) return;
        String htmlText = rawText.replaceAll("\\*\\*(.*?)\\*\\*", "<b>$1</b>")
                                 .replace("\n", "<br/>");
        textView.setText(android.text.Html.fromHtml(htmlText, android.text.Html.FROM_HTML_MODE_LEGACY));
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
