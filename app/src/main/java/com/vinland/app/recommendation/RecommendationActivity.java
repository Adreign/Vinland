package com.vinland.app.recommendation;

import android.content.Intent;
import android.content.res.ColorStateList;
import android.graphics.Color;
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

import com.google.android.material.button.MaterialButton;
import com.google.android.material.card.MaterialCardView;
import com.google.android.material.chip.Chip;
import com.google.android.material.chip.ChipGroup;
import com.google.android.material.textfield.TextInputEditText;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.ListenerRegistration;
import com.google.firebase.firestore.SetOptions;
import com.google.firebase.firestore.WriteBatch;
import com.vinland.app.R;
import com.vinland.app.data.Crop;
import com.vinland.app.data.CropDatabaseSeeder;
import com.vinland.app.data.SoilLog;


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
    private LinearLayout layoutResults, layoutParameterResults, layoutRecommendedCrops;
    private ProgressBar progressAiLoading;

    private FirebaseAuth mAuth;
    private FirebaseFirestore db;

    private ListenerRegistration sensorListenerRegistration;
    private String activeSensorDeviceId = "VINLAND-ESP32-01";

    private final Map<String, Crop> cropMap = new HashMap<>();
    private Crop topRecommendedCrop = null;

    private double currentMoisture, currentPh, currentN, currentP, currentK;

    private List<CropScorer.Match> lastRanking = new ArrayList<>();

    // Which option the user picked at the top of the screen
    private static final int MODE_NONE = 0;
    private static final int MODE_NEW_SOIL = 1;
    private static final int MODE_MONITOR_CROPS = 2;
    private int mode = MODE_NONE;

    private MaterialCardView cardModeNewSoil, cardModeMonitorCrops, cardCropPicker, cardSensorInput;
    private TextView tvModeNewSoilCheck, tvModeMonitorCheck, tvCropPickerStatus;
    private TextView tvInputHeader, tvInputSubheader, tvCropsSectionTitle, tvSummaryCropLabel;
    private ChipGroup chipGroupCrops;

    // Monitor Crops: crops checked last time (saved on the user's profile), and the latest results
    private final List<String> preselectedCrops = new ArrayList<>();
    private boolean cropsLoaded = false, userLoaded = false;
    private List<CropHealthChecker.Result> lastHealthResults = new ArrayList<>();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_recommendation);

        mAuth = FirebaseAuth.getInstance();
        db = FirebaseFirestore.getInstance();

        tvBack = findViewById(R.id.tvBack);
        tvInputError = findViewById(R.id.tvInputError);
        etMoisture = findViewById(R.id.etMoisture);
        etPh = findViewById(R.id.etPh);
        etNitrogen = findViewById(R.id.etNitrogen);
        etPhosphorus = findViewById(R.id.etPhosphorus);
        etPotassium = findViewById(R.id.etPotassium);
        btnAnalyze = findViewById(R.id.btnAnalyze);
        btnSaveToHistory = findViewById(R.id.btnSaveToHistory);
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
        View btnOpenAi = findViewById(R.id.btnOpenAiRecommendations);
        btnOpenAi.setOnClickListener(v ->
                startActivity(new Intent(this, com.vinland.app.ai.AiRecommendationActivity.class)));
        tvChangeDevice.setOnClickListener(v -> showChangeDeviceDialog());

        cardModeNewSoil = findViewById(R.id.cardModeNewSoil);
        cardModeMonitorCrops = findViewById(R.id.cardModeMonitorCrops);
        cardCropPicker = findViewById(R.id.cardCropPicker);
        cardSensorInput = findViewById(R.id.cardSensorInput);
        tvModeNewSoilCheck = findViewById(R.id.tvModeNewSoilCheck);
        tvModeMonitorCheck = findViewById(R.id.tvModeMonitorCheck);
        tvCropPickerStatus = findViewById(R.id.tvCropPickerStatus);
        tvInputHeader = findViewById(R.id.tvInputHeader);
        tvInputSubheader = findViewById(R.id.tvInputSubheader);
        tvCropsSectionTitle = findViewById(R.id.tvCropsSectionTitle);
        tvSummaryCropLabel = findViewById(R.id.tvSummaryCropLabel);
        chipGroupCrops = findViewById(R.id.chipGroupCrops);

        cardModeNewSoil.setOnClickListener(v -> setMode(MODE_NEW_SOIL));
        cardModeMonitorCrops.setOnClickListener(v -> setMode(MODE_MONITOR_CROPS));

        setFieldsEditable(false);
        loadUserSavedDeviceId();
        CropDatabaseSeeder.seedIfEmpty(db, this::loadCropsFromFirestore);
    }

    /** Switches between "New Soil" and "Monitor Crops / Plants". */
    private void setMode(int newMode) {
        if (mode == newMode) return;
        mode = newMode;
        boolean newSoil = mode == MODE_NEW_SOIL;

        styleModeCard(cardModeNewSoil, tvModeNewSoilCheck, newSoil);
        styleModeCard(cardModeMonitorCrops, tvModeMonitorCheck, !newSoil);

        cardCropPicker.setVisibility(newSoil ? View.GONE : View.VISIBLE);
        cardSensorInput.setVisibility(View.VISIBLE);

        if (newSoil) {
            tvInputHeader.setText("Test your new soil");
            tvInputSubheader.setText("Insert the NPK sensor into the new soil and wait for the live reading, then tap the button.");
            btnAnalyze.setText("Recommend crops");
            tvCropsSectionTitle.setText("Recommended crops for your soil");
            tvSummaryCropLabel.setText("Best crop match");
        } else {
            tvInputHeader.setText("Check your crops' soil");
            tvInputSubheader.setText("Insert the NPK sensor into the soil near your plants and wait for the live reading, then tap the button.");
            btnAnalyze.setText("Check my crops");
            tvCropsSectionTitle.setText("Your crops' soil health");
            tvSummaryCropLabel.setText("Needs most attention");
        }

        // Old results belong to the other option, so hide them.
        layoutResults.setVisibility(View.GONE);
        hideInputError();
    }

    private void styleModeCard(MaterialCardView card, TextView check, boolean selected) {
        card.setStrokeColor(Color.parseColor(selected ? "#2E7D32" : "#E2E7DC"));
        card.setStrokeWidth(dpToPx(selected ? 2 : 1));
        card.setCardBackgroundColor(Color.parseColor(selected ? "#F1F8EE" : "#FFFFFF"));
        check.setVisibility(selected ? View.VISIBLE : View.INVISIBLE);
    }

    private void loadUserSavedDeviceId() {
        if (mAuth.getCurrentUser() == null) {
            userLoaded = true;
            buildCropChips();
            listenToLiveSensorData();
            return;
        }
        db.collection("users").document(mAuth.getCurrentUser().getUid())
                .get()
                .addOnSuccessListener(document -> {
                    if (document.exists() && document.contains("sensorDeviceId")) {
                        String savedId = document.getString("sensorDeviceId");
                        if (!TextUtils.isEmpty(savedId)) {
                            activeSensorDeviceId = savedId;
                        }
                    }
                    // Pre-check the crops monitored last time, or else the crops on the user's profile.
                    Object saved = document.get("monitoredCrops");
                    if (!(saved instanceof List) || ((List<?>) saved).isEmpty()) {
                        saved = document.get("crops");
                    }
                    if (saved instanceof List) {
                        for (Object o : (List<?>) saved) {
                            if (o != null) preselectedCrops.add(o.toString().toLowerCase(Locale.ROOT));
                        }
                    }
                    userLoaded = true;
                    buildCropChips();
                    listenToLiveSensorData();
                })
                .addOnFailureListener(e -> {
                    userLoaded = true;
                    buildCropChips();
                    listenToLiveSensorData();
                });
    }

    /** Fills the crop picker once both the crop list and the user's profile have loaded. */
    private void buildCropChips() {
        if (!cropsLoaded || !userLoaded) return;
        chipGroupCrops.removeAllViews();

        List<Crop> crops = new ArrayList<>(cropMap.values());
        crops.sort((a, b) -> a.getName().compareToIgnoreCase(b.getName()));

        ColorStateList chipBg = new ColorStateList(
                new int[][]{{android.R.attr.state_checked}, {}},
                new int[]{Color.parseColor("#DCEED6"), Color.parseColor("#F6F8F3")});
        ColorStateList chipStroke = new ColorStateList(
                new int[][]{{android.R.attr.state_checked}, {}},
                new int[]{Color.parseColor("#2E7D32"), Color.parseColor("#E2E7DC")});

        for (Crop crop : crops) {
            Chip chip = new Chip(this);
            String emoji = crop.getEmoji() != null ? crop.getEmoji() + " " : "";
            chip.setText(emoji + crop.getName());
            chip.setTag(crop.getName().toLowerCase(Locale.ROOT));
            chip.setCheckable(true);
            chip.setChipBackgroundColor(chipBg);
            chip.setChipStrokeColor(chipStroke);
            chip.setChipStrokeWidth(dpToPx(1));
            chip.setCheckedIconTint(ColorStateList.valueOf(Color.parseColor("#2E7D32")));
            chip.setTextColor(Color.parseColor("#1C2A20"));
            chip.setChecked(preselectedCrops.contains(crop.getName().toLowerCase(Locale.ROOT)));
            chipGroupCrops.addView(chip);
        }

        if (crops.isEmpty()) {
            tvCropPickerStatus.setText("No crops found in the database.");
            tvCropPickerStatus.setVisibility(View.VISIBLE);
        } else {
            tvCropPickerStatus.setVisibility(View.GONE);
        }
    }

    private List<Crop> getSelectedCrops() {
        List<Crop> selected = new ArrayList<>();
        for (int i = 0; i < chipGroupCrops.getChildCount(); i++) {
            View child = chipGroupCrops.getChildAt(i);
            if (child instanceof Chip && ((Chip) child).isChecked()) {
                Crop crop = cropMap.get((String) child.getTag());
                if (crop != null) selected.add(crop);
            }
        }
        return selected;
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
                        listenToLiveSensorData();
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

    @Override
    protected void onDestroy() {
        super.onDestroy();
    }

    private void loadCropsFromFirestore() {
        db.collection("crops").get().addOnSuccessListener(querySnapshot -> {
            cropMap.clear();
            for (DocumentSnapshot doc : querySnapshot.getDocuments()) {
                Crop crop = doc.toObject(Crop.class);
                if (crop != null) {
                    cropMap.put(crop.getName().toLowerCase(Locale.ROOT), crop);
                }
            }
            cropsLoaded = true;
            buildCropChips();
        }).addOnFailureListener(e -> {
            Toast.makeText(this, "Could not load crops database", Toast.LENGTH_SHORT).show();
            tvCropPickerStatus.setText("Could not load crops. Check your internet and reopen this screen.");
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
            showInputError("Waiting for live ESP32 soil readings...");
            return;
        }

        try {
            currentMoisture = Double.parseDouble(moistureStr);
            currentPh = Double.parseDouble(phStr);
            currentN = Double.parseDouble(nStr);
            currentP = Double.parseDouble(pStr);
            currentK = Double.parseDouble(kStr);
        } catch (NumberFormatException e) {
            showInputError("Invalid sensor readings format.");
            return;
        }

        if (mode == MODE_MONITOR_CROPS) {
            List<Crop> selected = getSelectedCrops();
            if (selected.isEmpty()) {
                showInputError("Choose at least one crop or plant to monitor.");
                return;
            }
            hideInputError();
            monitorSelectedCrops(selected);
            return;
        }

        hideInputError();
        evaluateRecommendedCrops(currentMoisture, currentPh, currentN, currentP, currentK);
        updateExecutiveSummary(currentMoisture, currentPh, currentN, currentP, currentK);
        showGeneralParameterResults(currentMoisture, currentPh, currentN, currentP, currentK);
        fetchLlmRecommendation(currentMoisture, currentPh, currentN, currentP, currentK);
    }

    // ---------------------------------------------------------------------
    // Option 2: Monitor Crops / Plants
    // ---------------------------------------------------------------------

    private void monitorSelectedCrops(List<Crop> selected) {
        lastHealthResults = new ArrayList<>();
        for (Crop crop : selected) {
            lastHealthResults.add(CropHealthChecker.check(crop, currentMoisture, currentPh, currentN, currentP, currentK));
        }
        // Worst first, so the crop that needs help is on top.
        lastHealthResults.sort((a, b) -> Integer.compare(a.score, b.score));

        layoutRecommendedCrops.removeAllViews();
        for (CropHealthChecker.Result r : lastHealthResults) {
            addCropHealthCard(layoutRecommendedCrops, r);
        }

        // Shared soil summary, then the crop-specific parts.
        updateExecutiveSummary(currentMoisture, currentPh, currentN, currentP, currentK);
        CropHealthChecker.Result worst = lastHealthResults.get(0);
        if (worst.score >= 80) {
            tvSummaryCropLabel.setText("Crop status");
            tvSummaryTopCropName.setText("✅ All crops healthy");
            tvSummaryActionNeeded.setText("Maintain Current Care");
        } else {
            tvSummaryCropLabel.setText("Needs most attention");
            tvSummaryTopCropName.setText(emojiOf(worst.crop) + " " + worst.crop.getName());
            tvSummaryActionNeeded.setText(firstProblemAction(worst));
        }

        showGeneralParameterResults(currentMoisture, currentPh, currentN, currentP, currentK);
        progressAiLoading.setVisibility(View.GONE);
        displayFormattedAiText(tvAiAdvice, buildMonitoringReport(lastHealthResults)
                + "<br/><br/><i>Tip: save this reading, then open AI Garden Recommendations for personalized advice.</i>");

        layoutResults.setVisibility(View.VISIBLE);
        rememberMonitoredCrops(selected);
    }

    private String firstProblemAction(CropHealthChecker.Result r) {
        List<CropHealthChecker.ParamCheck> problems = r.problems();
        if (problems.isEmpty()) return "Maintain Current Care";
        CropHealthChecker.ParamCheck c = problems.get(0);
        return (c.level == CropHealthChecker.Level.LOW ? "Raise " : "Lower ") + c.label;
    }

    private String buildMonitoringReport(List<CropHealthChecker.Result> results) {
        StringBuilder sb = new StringBuilder("<b>🌿 Crop Monitoring Report</b><br/>");
        for (CropHealthChecker.Result r : results) {
            sb.append("<br/><b>").append(emojiOf(r.crop)).append(" ").append(r.crop.getName())
                    .append(" — ").append(r.statusLabel()).append(" (").append(r.score).append("%)</b><br/>");
            List<CropHealthChecker.ParamCheck> problems = r.problems();
            if (problems.isEmpty()) {
                sb.append("• Soil is within this crop's ideal range. Keep your current care.<br/>");
            }
            for (CropHealthChecker.ParamCheck c : problems) {
                sb.append("• <b>").append(c.label).append(c.level == CropHealthChecker.Level.LOW ? " is low" : " is high")
                        .append("</b> (").append(c.valueText()).append(", ideal ").append(c.rangeText()).append("): ")
                        .append(c.tip).append("<br/>");
            }
        }
        return sb.toString();
    }

    private void addCropHealthCard(LinearLayout container, CropHealthChecker.Result r) {
        MaterialCardView card = new MaterialCardView(this);
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        params.bottomMargin = dpToPx(10);
        card.setLayoutParams(params);
        card.setRadius(dpToPx(14));
        card.setCardElevation(0);
        card.setStrokeColor(Color.parseColor("#E2E7DC"));
        card.setStrokeWidth(dpToPx(1));
        card.setCardBackgroundColor(Color.WHITE);

        LinearLayout col = new LinearLayout(this);
        col.setOrientation(LinearLayout.VERTICAL);
        col.setPadding(dpToPx(16), dpToPx(14), dpToPx(16), dpToPx(14));

        // Header: emoji, name, status
        LinearLayout header = new LinearLayout(this);
        header.setOrientation(LinearLayout.HORIZONTAL);
        header.setGravity(Gravity.CENTER_VERTICAL);

        TextView tvEmoji = new TextView(this);
        tvEmoji.setText(emojiOf(r.crop));
        tvEmoji.setTextSize(24);
        tvEmoji.setPadding(0, 0, dpToPx(10), 0);
        header.addView(tvEmoji);

        TextView tvName = new TextView(this);
        tvName.setText(r.crop.getName());
        tvName.setTextSize(15);
        tvName.setTypeface(null, android.graphics.Typeface.BOLD);
        tvName.setTextColor(Color.parseColor("#1C2A20"));
        tvName.setLayoutParams(new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1));
        header.addView(tvName);

        String statusColor = r.score >= 80 ? "#2E7D32" : r.score >= 60 ? "#F57C00" : "#D32F2F";
        TextView tvStatus = new TextView(this);
        tvStatus.setText(r.statusLabel() + " · " + r.score + "%");
        tvStatus.setTextSize(12);
        tvStatus.setTypeface(null, android.graphics.Typeface.BOLD);
        tvStatus.setTextColor(Color.WHITE);
        tvStatus.setBackgroundResource(R.drawable.bg_badge_pill);
        tvStatus.setBackgroundTintList(ColorStateList.valueOf(Color.parseColor(statusColor)));
        tvStatus.setPadding(dpToPx(10), dpToPx(4), dpToPx(10), dpToPx(4));
        header.addView(tvStatus);
        col.addView(header);

        // One line per soil parameter
        for (CropHealthChecker.ParamCheck c : r.checks) {
            if (c.level == CropHealthChecker.Level.UNKNOWN) continue;

            LinearLayout line = new LinearLayout(this);
            line.setOrientation(LinearLayout.HORIZONTAL);
            line.setPadding(0, dpToPx(8), 0, 0);

            TextView tvParam = new TextView(this);
            tvParam.setText(c.label + "  " + c.valueText());
            tvParam.setTextSize(13);
            tvParam.setTextColor(Color.parseColor("#1C2A20"));
            tvParam.setLayoutParams(new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1));
            line.addView(tvParam);

            String levelText;
            String levelColor;
            switch (c.level) {
                case LOW: levelText = "Low"; levelColor = "#D32F2F"; break;
                case HIGH: levelText = "High"; levelColor = "#F57C00"; break;
                default: levelText = "OK"; levelColor = "#2E7D32"; break;
            }
            TextView tvLevel = new TextView(this);
            tvLevel.setText(levelText + "  (ideal " + c.rangeText() + ")");
            tvLevel.setTextSize(12);
            tvLevel.setTypeface(null, android.graphics.Typeface.BOLD);
            tvLevel.setTextColor(Color.parseColor(levelColor));
            line.addView(tvLevel);
            col.addView(line);

            if (!c.tip.isEmpty()) {
                TextView tvTip = new TextView(this);
                tvTip.setText("→ " + c.tip);
                tvTip.setTextSize(12);
                tvTip.setTextColor(Color.parseColor("#5C6B5F"));
                tvTip.setPadding(0, dpToPx(2), 0, 0);
                col.addView(tvTip);
            }
        }

        card.addView(col);
        container.addView(card);
    }

    /** Remembers the picked crops so they are pre-selected next time. */
    private void rememberMonitoredCrops(List<Crop> selected) {
        if (mAuth.getCurrentUser() == null) return;
        List<String> names = new ArrayList<>();
        for (Crop c : selected) names.add(c.getName());
        Map<String, Object> update = new HashMap<>();
        update.put("monitoredCrops", names);
        db.collection("users").document(mAuth.getCurrentUser().getUid())
                .set(update, SetOptions.merge());
    }

    private static String emojiOf(Crop crop) {
        return crop.getEmoji() != null ? crop.getEmoji() : "🌱";
    }

    private void evaluateRecommendedCrops(double moisture, double ph, double n, double p, double k) {
        if (layoutRecommendedCrops == null) return;
        layoutRecommendedCrops.removeAllViews();
        topRecommendedCrop = null;

        lastRanking = CropScorer.rank(cropMap.values(), moisture, ph, n, p, k);

        int count = 0;
        for (CropScorer.Match match : lastRanking) {
            // Always show the top 2; show up to 4 if they're reasonable matches.
            if (count >= 4 || (count >= 2 && match.score < 60)) break;
            String reason = match.issues.isEmpty()
                    ? "All soil parameters are within this crop's range."
                    : TextUtils.join("; ", match.issues) + ".";
            addCropMatchCard(layoutRecommendedCrops, match.crop, match.score, reason);
            if (count == 0) topRecommendedCrop = match.crop;
            count++;
        }

        layoutResults.setVisibility(View.VISIBLE);
    }

    private void addCropMatchCard(LinearLayout container, Crop crop, int score, String reason) {
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

        String matchText = score >= 80 ? "⭐ Excellent match · " + score + "%"
                : score >= 60 ? "👍 Good match · " + score + "%"
                : "⚠️ Needs soil adjustment · " + score + "%";
        TextView tvMatch = new TextView(this);
        tvMatch.setText(matchText);
        tvMatch.setTextSize(12);
        tvMatch.setTextColor(android.graphics.Color.parseColor("#2E7D32"));
        tvMatch.setTypeface(null, android.graphics.Typeface.BOLD);

        textCol.addView(tvName);
        textCol.addView(tvMatch);

        if (!TextUtils.isEmpty(reason)) {
            TextView tvReason = new TextView(this);
            tvReason.setText(reason);
            tvReason.setTextSize(12);
            tvReason.setTextColor(android.graphics.Color.parseColor("#666666"));
            tvReason.setPadding(0, dpToPx(2), 0, 0);
            textCol.addView(tvReason);
        }
        row.addView(textCol);

        card.addView(row);
        container.addView(card);
    }

    private void showGeneralParameterResults(double moisture, double ph, double n, double p, double k) {
        layoutParameterResults.removeAllViews();

        addResultRow("💧 Moisture", moisture + "%", classifyGeneralMoisture(moisture));
        addResultRow("⚗️ pH Level", String.valueOf(ph), classifyGeneralPh(ph));
        addResultRow("🌿 Nitrogen (N)", n + " ppm", classifyGeneralNutrient(n, 5.0, 80.0, "Nitrogen"));
        addResultRow("🌿 Phosphorus (P)", p + " ppm", classifyGeneralNutrient(p, 5.0, 60.0, "Phosphorus"));
        addResultRow("🌿 Potassium (K)", k + " ppm", classifyGeneralNutrient(k, 15.0, 150.0, "Potassium"));
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

    /**
     * Shows the built-in (offline, rule-based) soil report. Personalized AI advice now
     * lives on its own screen, powered by a Cloud Function: see ai/AiRecommendationActivity.
     */
    private void fetchLlmRecommendation(double moisture, double ph, double n, double p, double k) {
        progressAiLoading.setVisibility(View.GONE);
        String html = generateLocalAgronomistReport(moisture, ph, n, p, k)
                + "<br/><br/><i>Tip: save this reading, then open AI Garden Recommendations for personalized advice.</i>";
        displayFormattedAiText(tvAiAdvice, html);
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
        report.append("Nitrogen ").append(n < 5.0 ? "Deficient" : n > 80.0 ? "High" : "Optimal").append(" (").append(n).append(" ppm), ");
        report.append("Phosphorus ").append(p < 5.0 ? "Deficient" : p > 60.0 ? "High" : "Optimal").append(" (").append(p).append(" ppm), ");
        report.append("Potassium ").append(k < 15.0 ? "Deficient" : k > 150.0 ? "High" : "Optimal").append(" (").append(k).append(" ppm).<br/><br/>");

        // 2. Crop Match
        report.append("<b>2. Top Recommended Crops:</b><br/>");
        String topName = topRecommendedCrop != null ? topRecommendedCrop.getName() : "Urban Vegetable Crop";
        report.append("• <b>").append(topName).append(":</b> Highly recommended for current soil and climate conditions in Quezon City.<br/><br/>");

        // 3. Soil Amendments
        report.append("<b>3. Actionable Soil Care Steps:</b><br/>");
        if (n < 5.0) {
            report.append("• Apply organic vermicompost or fermented plant juice (FPJ) to boost Nitrogen.<br/>");
        }
        if (p < 5.0) {
            report.append("• Apply steamed bone meal or fermented fruit juice (FFJ) for root development.<br/>");
        }
        if (k < 15.0) {
            report.append("• Apply banana peel liquid tea or wood ash to supply Potassium.<br/>");
        }
        if (ph < 5.5) {
            report.append("• Top-dress 1 tablespoon of agricultural lime per pot.<br/>");
        } else if (ph > 7.5) {
            report.append("• Mix in rich organic compost to lower alkaline pH.<br/>");
        } else if (n >= 5.0 && p >= 5.0 && k >= 15.0) {
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
        if (moisture >= 30 && moisture <= 80) optimalCount++;
        if (ph >= 5.5 && ph <= 7.5) optimalCount++;
        if (n >= 5.0 && n <= 80.0) optimalCount++;
        if (p >= 5.0 && p <= 60.0) optimalCount++;
        if (k >= 15.0 && k <= 150.0) optimalCount++;

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
                .append(n < 5.0 ? "N Deficient, " : n > 80.0 ? "N High, " : "N Optimal, ")
                .append(p < 5.0 ? "P Deficient, " : p > 60.0 ? "P High, " : "P Optimal, ")
                .append(k < 15.0 ? "K Deficient." : k > 150.0 ? "K High." : "K Optimal.");

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
            } else if (n < 5.0) {
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

        if (mode == MODE_MONITOR_CROPS) {
            saveMonitoringToHistory(uid);
            return;
        }

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

    /** Saves one history entry per monitored crop, all with the same reading. */
    private void saveMonitoringToHistory(String uid) {
        if (lastHealthResults.isEmpty()) return;
        long now = System.currentTimeMillis();
        WriteBatch batch = db.batch();
        for (CropHealthChecker.Result r : lastHealthResults) {
            String logId = db.collection("users").document(uid).collection("soil_logs").document().getId();
            SoilLog log = new SoilLog(logId, r.crop.getName(), currentMoisture, currentPh,
                    currentN, currentP, currentK,
                    "Crop Monitoring: " + r.statusLabel() + " (" + r.score + "%)", now);
            batch.set(db.collection("users").document(uid).collection("soil_logs").document(logId), log);
        }
        int count = lastHealthResults.size();
        batch.commit()
                .addOnSuccessListener(unused -> Toast.makeText(this,
                        count == 1 ? "Saved to history!" : "Saved " + count + " crops to history!", Toast.LENGTH_SHORT).show())
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
