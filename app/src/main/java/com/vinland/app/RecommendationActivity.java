package com.vinland.app;

import android.os.Bundle;
import android.text.TextUtils;
import android.view.Gravity;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.android.volley.Request;
import com.android.volley.RequestQueue;
import com.android.volley.toolbox.JsonObjectRequest;
import com.android.volley.toolbox.Volley;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.card.MaterialCardView;
import com.google.android.material.chip.Chip;
import com.google.android.material.chip.ChipGroup;
import com.google.android.material.textfield.TextInputEditText;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FirebaseFirestore;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class RecommendationActivity extends AppCompatActivity {

    private TextView tvBack, tvInputError, tvAiAdvice;
    private ChipGroup chipGroupCrop;
    private TextInputEditText etMoisture, etPh, etNitrogen, etPhosphorus, etPotassium;
    private MaterialButton btnAnalyze, btnSaveToHistory;
    private LinearLayout layoutResults, layoutParameterResults;
    private MaterialCardView cardAiInsights;
    private ProgressBar progressAiLoading;

    private FirebaseAuth mAuth;
    private FirebaseFirestore db;
    private RequestQueue requestQueue;

    private final Map<String, Crop> cropMap = new HashMap<>();
    private Crop selectedCropObj = null;

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
        chipGroupCrop = findViewById(R.id.chipGroupCrop);
        etMoisture = findViewById(R.id.etMoisture);
        etPh = findViewById(R.id.etPh);
        etNitrogen = findViewById(R.id.etNitrogen);
        etPhosphorus = findViewById(R.id.etPhosphorus);
        etPotassium = findViewById(R.id.etPotassium);
        btnAnalyze = findViewById(R.id.btnAnalyze);
        btnSaveToHistory = findViewById(R.id.btnSaveToHistory);
        layoutResults = findViewById(R.id.layoutResults);
        layoutParameterResults = findViewById(R.id.layoutParameterResults);
        cardAiInsights = findViewById(R.id.cardAiInsights);
        progressAiLoading = findViewById(R.id.progressAiLoading);
        tvAiAdvice = findViewById(R.id.tvAiAdvice);

        tvBack.setOnClickListener(v -> finish());
        btnAnalyze.setOnClickListener(v -> analyzeSoil());
        btnSaveToHistory.setOnClickListener(v -> saveReadingToHistory());

        CropDatabaseSeeder.seedIfEmpty(db, this::loadCropsFromFirestore);
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
            loadGardenerCrops();
        }).addOnFailureListener(e -> loadGardenerCrops());
    }

    private void loadGardenerCrops() {
        if (mAuth.getCurrentUser() == null) {
            setupCropChips(new ArrayList<>(cropMap.keySet()));
            return;
        }

        db.collection("users").document(mAuth.getCurrentUser().getUid())
                .get()
                .addOnSuccessListener(document -> {
                    List<String> crops = new ArrayList<>();
                    List<Object> rawCrops = (List<Object>) document.get("crops");
                    if (rawCrops != null) {
                        for (Object c : rawCrops) crops.add(c.toString());
                    }
                    if (crops.isEmpty()) {
                        crops.addAll(cropMap.keySet());
                    }
                    setupCropChips(crops);
                })
                .addOnFailureListener(e -> setupCropChips(new ArrayList<>(cropMap.keySet())));
    }

    private void setupCropChips(List<String> cropNames) {
        chipGroupCrop.removeAllViews();
        List<Crop> availableCrops = new ArrayList<>();

        for (String cName : cropNames) {
            Crop crop = cropMap.get(cName.toLowerCase());
            if (crop != null && !availableCrops.contains(crop)) {
                availableCrops.add(crop);
            }
        }

        if (availableCrops.isEmpty()) {
            availableCrops.addAll(cropMap.values());
        }

        for (Crop crop : availableCrops) {
            Chip chip = new Chip(this);
            String label = crop.getEmoji() != null ? crop.getEmoji() + " " + crop.getName() : crop.getName();
            chip.setText(label);
            chip.setTag(crop);
            chip.setCheckable(true);
            chip.setCheckedIconVisible(false);
            chip.setChipBackgroundColorResource(android.R.color.white);
            chipGroupCrop.addView(chip);
        }

        if (chipGroupCrop.getChildCount() > 0) {
            Chip firstChip = (Chip) chipGroupCrop.getChildAt(0);
            firstChip.setChecked(true);
            selectedCropObj = (Crop) firstChip.getTag();
        }

        chipGroupCrop.setOnCheckedStateChangeListener((group, checkedIds) -> {
            if (!checkedIds.isEmpty()) {
                Chip checked = group.findViewById(checkedIds.get(0));
                if (checked != null && checked.getTag() instanceof Crop) {
                    selectedCropObj = (Crop) checked.getTag();
                }
            }
        });
    }

    private void analyzeSoil() {
        if (selectedCropObj == null) {
            showInputError("Please select a crop first.");
            return;
        }

        String moistureStr = getText(etMoisture);
        String phStr = getText(etPh);
        String nStr = getText(etNitrogen);
        String pStr = getText(etPhosphorus);
        String kStr = getText(etPotassium);

        if (TextUtils.isEmpty(moistureStr) || TextUtils.isEmpty(phStr)
                || TextUtils.isEmpty(nStr) || TextUtils.isEmpty(pStr) || TextUtils.isEmpty(kStr)) {
            showInputError("Please fill in all five soil readings.");
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
        showResults(currentMoisture, currentPh, currentN, currentP, currentK);
        fetchGeminiAiRecommendation(currentMoisture, currentPh, currentN, currentP, currentK);
    }

    private void showResults(double moisture, double ph, double n, double p, double k) {
        layoutParameterResults.removeAllViews();

        addResultRow("💧 Moisture", moisture + "%", classifyMoisture(moisture));
        addResultRow("⚗️ pH Level", String.valueOf(ph), classifyPh(ph));
        addResultRow("🌿 Nitrogen (N)", n + " ppm", classifyNitrogen(n));
        addResultRow("🌿 Phosphorus (P)", p + " ppm", classifyPhosphorus(p));
        addResultRow("🌿 Potassium (K)", k + " ppm", classifyPotassium(k));

        layoutResults.setVisibility(View.VISIBLE);
    }

    private void fetchGeminiAiRecommendation(double moisture, double ph, double n, double p, double k) {
        progressAiLoading.setVisibility(View.VISIBLE);
        tvAiAdvice.setText("Consulting Gemini AI for personalized soil recommendations...");

        String cropName = selectedCropObj != null ? selectedCropObj.getName() : "Urban Vegetable";
        String prompt = "You are an expert AI agricultural agronomist for urban gardening in Quezon City, Philippines. "
                + "A gardener tested soil for " + cropName + " with these sensor readings: "
                + "Soil Moisture: " + moisture + "%, pH: " + ph + ", Nitrogen (N): " + n + " ppm, Phosphorus (P): " + p + " ppm, Potassium (K): " + k + " ppm. "
                + "Provide a concise, practical 2 to 3 sentence recommendation on what fertilizer, watering, or soil amendment actions they should take for Quezon City urban conditions.";

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
                            tvAiAdvice.setText("Soil parameters evaluated successfully via agronomic rules.");
                        } catch (JSONException e) {
                            tvAiAdvice.setText("Soil parameters evaluated successfully via agronomic rules.");
                        }
                    },
                    error -> {
                        progressAiLoading.setVisibility(View.GONE);
                        tvAiAdvice.setText("Soil parameters evaluated successfully via agronomic rules.");
                    }
            );

            requestQueue.add(request);

        } catch (JSONException e) {
            progressAiLoading.setVisibility(View.GONE);
            tvAiAdvice.setText("Soil parameters evaluated successfully via agronomic rules.");
        }
    }

    private void saveReadingToHistory() {
        if (mAuth.getCurrentUser() == null || selectedCropObj == null) return;
        String uid = mAuth.getCurrentUser().getUid();

        String logId = db.collection("users").document(uid).collection("soil_logs").document().getId();
        SoilLog log = new SoilLog(
                logId,
                selectedCropObj.getName(),
                currentMoisture,
                currentPh,
                currentN,
                currentP,
                currentK,
                "Logged Soil Test",
                System.currentTimeMillis()
        );

        db.collection("users").document(uid).collection("soil_logs").document(logId)
                .set(log)
                .addOnSuccessListener(unused -> Toast.makeText(this, "Soil reading saved to history!", Toast.LENGTH_SHORT).show())
                .addOnFailureListener(e -> Toast.makeText(this, "Could not save reading: " + e.getMessage(), Toast.LENGTH_SHORT).show());
    }

    private String[] classifyMoisture(double moisture) {
        double min = selectedCropObj.getMoistureMin();
        double max = selectedCropObj.getMoistureMax();
        String cropName = selectedCropObj.getName().toLowerCase();

        if (moisture < min) {
            return new String[]{"Too Dry", "Water your " + cropName + " soon \u2014 moisture is below the optimal " + min + "% threshold.", "#D32F2F"};
        } else if (moisture > max) {
            return new String[]{"Too Wet", "Hold off watering and check that your container has proper drainage.", "#F9A825"};
        }
        return new String[]{"Optimal", "Moisture level is in a healthy range for " + cropName + ".", "#2E7D32"};
    }

    private String[] classifyPh(double ph) {
        double min = selectedCropObj.getPhMin();
        double max = selectedCropObj.getPhMax();
        String cropName = selectedCropObj.getName().toLowerCase();

        if (ph < min) {
            return new String[]{"Too Acidic", "Mix in a small amount of garden lime before planting " + cropName + ".", "#D32F2F"};
        } else if (ph > max) {
            return new String[]{"Too Alkaline", "Add organic compost or sulfur to gradually lower pH for " + cropName + ".", "#F9A825"};
        }
        return new String[]{"Optimal", "pH is within the ideal range (" + min + "\u2013" + max + ") for " + cropName + ".", "#2E7D32"};
    }

    private String[] classifyNitrogen(double n) {
        double min = selectedCropObj.getNitrogenMin();
        double max = selectedCropObj.getNitrogenMax();

        if (n < min) {
            return new String[]{"Deficient", "Consider adding a nitrogen-rich fertilizer to support leaf growth.", "#D32F2F"};
        } else if (n > max) {
            return new String[]{"Excessive", "Reduce nitrogen fertilizer \u2014 too much can delay fruiting.", "#F9A825"};
        }
        return new String[]{"Optimal", "Nitrogen level supports healthy growth.", "#2E7D32"};
    }

    private String[] classifyPhosphorus(double p) {
        double min = selectedCropObj.getPhosphorusMin();
        double max = selectedCropObj.getPhosphorusMax();

        if (p < min) {
            return new String[]{"Deficient", "Add a phosphorus-rich fertilizer to support root and flower development.", "#D32F2F"};
        } else if (p > max) {
            return new String[]{"Excessive", "Reduce phosphorus input for now.", "#F9A825"};
        }
        return new String[]{"Optimal", "Phosphorus level supports root and flower development.", "#2E7D32"};
    }

    private String[] classifyPotassium(double k) {
        double min = selectedCropObj.getPotassiumMin();
        double max = selectedCropObj.getPotassiumMax();

        if (k < min) {
            return new String[]{"Deficient", "Add a potassium-rich fertilizer to improve fruit quality and disease resistance.", "#D32F2F"};
        } else if (k > max) {
            return new String[]{"Excessive", "Reduce potassium input for now.", "#F9A825"};
        }
        return new String[]{"Optimal", "Potassium level supports fruit quality and plant resilience.", "#2E7D32"};
    }

    private void addResultRow(String label, String reading, String[] statusData) {
        String status = statusData[0];
        String explanation = statusData[1];
        String color = statusData[2];

        com.google.android.material.card.MaterialCardView card = new com.google.android.material.card.MaterialCardView(this);
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
