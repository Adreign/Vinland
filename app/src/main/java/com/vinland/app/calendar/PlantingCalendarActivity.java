package com.vinland.app.calendar;

import android.graphics.Color;
import android.graphics.Typeface;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.AutoCompleteTextView;
import android.widget.HorizontalScrollView;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;

import com.android.volley.Request;
import com.android.volley.RequestQueue;
import com.android.volley.toolbox.JsonObjectRequest;
import com.android.volley.toolbox.Volley;
import com.google.android.material.card.MaterialCardView;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FirebaseFirestore;
import com.vinland.app.R;
import com.vinland.app.data.Crop;
import com.vinland.app.data.CropDatabaseSeeder;
import com.vinland.app.weather.WeatherConfig;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public class PlantingCalendarActivity extends AppCompatActivity {

    private AutoCompleteTextView actvCrop;
    private LinearLayout layoutMonths, layoutForecastDays;
    private ProgressBar progressForecast;
    private HorizontalScrollView scrollForecast;
    private MaterialCardView cardAdjustedAdvice;
    private TextView tvAdjustedAdvice;
    private MaterialCardView cardPlantingStatus;
    private TextView tvPlantingHeadline, tvPlantingDetail, tvYearLegend;
    private LinearLayout layoutYearStrip;
    private com.google.android.material.button.MaterialButton btnStartPlanting;

    private FirebaseAuth mAuth;
    private FirebaseFirestore db;
    private RequestQueue requestQueue;

    private final Map<String, Crop> cropMap = new HashMap<>();
    private Crop selectedCrop = null;

    private final List<double[]> forecastDays = new ArrayList<>();
    private final List<String> forecastConditions = new ArrayList<>();
    private final List<String> forecastDayLabels = new ArrayList<>();

    private static final String[] MONTH_NAMES = {
            "January", "February", "March", "April", "May", "June",
            "July", "August", "September", "October", "November", "December"
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_plantingcalendar);

        mAuth = FirebaseAuth.getInstance();
        db = FirebaseFirestore.getInstance();
        requestQueue = Volley.newRequestQueue(this);

        TextView tvBack = findViewById(R.id.tvBack);
        actvCrop = findViewById(R.id.actvCrop);
        layoutMonths = findViewById(R.id.layoutMonths);
        layoutForecastDays = findViewById(R.id.layoutForecastDays);
        progressForecast = findViewById(R.id.progressForecast);
        scrollForecast = findViewById(R.id.scrollForecast);
        cardAdjustedAdvice = findViewById(R.id.cardAdjustedAdvice);
        tvAdjustedAdvice = findViewById(R.id.tvAdjustedAdvice);
        cardPlantingStatus = findViewById(R.id.cardPlantingStatus);
        tvPlantingHeadline = findViewById(R.id.tvPlantingHeadline);
        tvPlantingDetail = findViewById(R.id.tvPlantingDetail);
        tvYearLegend = findViewById(R.id.tvYearLegend);
        layoutYearStrip = findViewById(R.id.layoutYearStrip);
        btnStartPlanting = findViewById(R.id.btnStartPlanting);

        tvBack.setOnClickListener(v -> finish());
        btnStartPlanting.setOnClickListener(v -> confirmStartPlanting());

        CropDatabaseSeeder.seedIfEmpty(db, () -> {
            loadCropsFromFirestore();
            fetchForecast();
        });
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
                    List<String> userCrops = new ArrayList<>();
                    List<Object> rawCrops = (List<Object>) document.get("crops");
                    if (rawCrops != null) {
                        for (Object c : rawCrops) userCrops.add(c.toString());
                    }
                    if (userCrops.isEmpty()) {
                        userCrops.addAll(cropMap.keySet());
                    }
                    setupCropChips(userCrops);
                })
                .addOnFailureListener(e -> setupCropChips(new ArrayList<>(cropMap.keySet())));
    }

    private void setupCropChips(List<String> cropNames) {
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

        List<String> labels = new ArrayList<>();
        for (Crop crop : availableCrops) {
            labels.add(crop.getEmoji() != null ? crop.getEmoji() + " " + crop.getName() : crop.getName());
        }

        actvCrop.setAdapter(new ArrayAdapter<>(this,
                android.R.layout.simple_list_item_1, labels));

        if (!availableCrops.isEmpty()) {
            selectedCrop = availableCrops.get(0);
            actvCrop.setText(labels.get(0), false); // false = don't filter the list
        }

        actvCrop.setOnItemClickListener((parent, view, position, id) -> {
            selectedCrop = availableCrops.get(position);
            renderMonths();
            updateAdjustedAdvice();
        });

        renderMonths();
    }

    private void renderMonths() {
        layoutMonths.removeAllViews();
        if (selectedCrop == null) {
            cardPlantingStatus.setVisibility(View.GONE);
            return;
        }

        PlantingSchedule schedule = new PlantingSchedule(selectedCrop);
        renderPlantingStatus(schedule);

        List<String> tips = selectedCrop.getMonthlyTips();
        int currentMonth = Calendar.getInstance().get(Calendar.MONTH);

        for (int i = 0; i < 12; i++) {
            boolean isCurrent = (i == currentMonth);
            String tip = (tips != null && i < tips.size() && tips.get(i) != null && !tips.get(i).trim().isEmpty())
                    ? tips.get(i)
                    : "Regular care and monitoring month for " + selectedCrop.getName() + ".";
            layoutMonths.addView(buildMonthCard(MONTH_NAMES[i], tip, isCurrent, stageTags(schedule, i + 1)));
        }
    }

    /** "🌱 Plant · 🧺 Harvest" style label for a month (1-12), or "" if nothing happens that month. */
    private String stageTags(PlantingSchedule schedule, int month) {
        List<String> tags = new ArrayList<>();
        if (schedule.isPlantMonth(month)) tags.add("🌱 Plant");
        if (schedule.isGrowingMonth(month)) tags.add("🌿 Growing");
        if (schedule.isHarvestMonth(month)) tags.add("🧺 Harvest");
        return android.text.TextUtils.join("  ·  ", tags);
    }

    /** Fills the "plant now?" banner and the 12-month strip for the picked crop. */
    private void renderPlantingStatus(PlantingSchedule schedule) {
        cardPlantingStatus.setVisibility(View.VISIBLE);
        layoutYearStrip.removeAllViews();
        String name = selectedCrop.getName();
        String emoji = selectedCrop.getEmoji() != null ? selectedCrop.getEmoji() + " " : "";

        if (!schedule.hasData()) {
            tvPlantingHeadline.setText(emoji + "No planting schedule yet");
            tvPlantingDetail.setText("Planting months and days to harvest haven't been set for " + name
                    + ". An admin can add them in the Vinland web admin under Crop Management.");
            layoutYearStrip.setVisibility(View.GONE);
            tvYearLegend.setVisibility(View.GONE);
            btnStartPlanting.setVisibility(View.GONE); // no days to harvest = no daily plan
            return;
        }
        layoutYearStrip.setVisibility(View.VISIBLE);
        tvYearLegend.setVisibility(View.VISIBLE);
        btnStartPlanting.setText("🌱 Start Planting " + name);
        btnStartPlanting.setVisibility(View.VISIBLE);

        int month = Calendar.getInstance().get(Calendar.MONTH) + 1;
        int days = schedule.getDaysToHarvest();
        if (schedule.isPlantMonth(month)) {
            tvPlantingHeadline.setText(emoji + "Good time to plant " + name + " 🌱");
            tvPlantingDetail.setText("If you plant this month, expect your first harvest around "
                    + MONTH_NAMES[schedule.harvestMonthFor(month) - 1]
                    + " (about " + days + " days).");
        } else {
            int next = schedule.nextPlantMonthAfter(month);
            tvPlantingHeadline.setText(emoji + "Not the best month to plant " + name);
            tvPlantingDetail.setText("Next planting window starts in " + MONTH_NAMES[next - 1]
                    + ". " + name + " takes about " + days + " days to reach harvest.");
        }

        for (int m = 1; m <= 12; m++) {
            layoutYearStrip.addView(buildYearCell(m, schedule, m == month));
        }
    }

    /** One small month cell in the year strip: first letter + stage icon, coloured by stage. */
    private View buildYearCell(int month, PlantingSchedule schedule, boolean isCurrent) {
        LinearLayout cell = new LinearLayout(this);
        cell.setOrientation(LinearLayout.VERTICAL);
        cell.setGravity(Gravity.CENTER_HORIZONTAL);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f);
        lp.setMargins(dpToPx(1), 0, dpToPx(1), 0);
        cell.setLayoutParams(lp);
        cell.setPadding(0, dpToPx(4), 0, dpToPx(4));

        android.graphics.drawable.GradientDrawable bg = new android.graphics.drawable.GradientDrawable();
        bg.setCornerRadius(dpToPx(8));
        String icon;
        if (schedule.isPlantMonth(month)) {
            bg.setColor(Color.parseColor("#C8E6C9"));
            icon = "🌱";
        } else if (schedule.isHarvestMonth(month)) {
            bg.setColor(Color.parseColor("#FFE0B2"));
            icon = "🧺";
        } else if (schedule.isGrowingMonth(month)) {
            bg.setColor(Color.parseColor("#EEF5EA"));
            icon = "🌿";
        } else {
            bg.setColor(Color.parseColor("#F3F5EF"));
            icon = " ";
        }
        if (isCurrent) bg.setStroke(dpToPx(2), Color.parseColor("#1F4D32"));
        cell.setBackground(bg);

        TextView tvLetter = new TextView(this);
        tvLetter.setText(MONTH_NAMES[month - 1].substring(0, 1));
        tvLetter.setTextSize(11);
        tvLetter.setTypeface(null, Typeface.BOLD);
        tvLetter.setTextColor(Color.parseColor("#1C2A20"));
        tvLetter.setGravity(Gravity.CENTER);

        TextView tvIcon = new TextView(this);
        tvIcon.setText(icon);
        tvIcon.setTextSize(10);
        tvIcon.setGravity(Gravity.CENTER);

        cell.addView(tvLetter);
        cell.addView(tvIcon);
        String tags = stageTags(schedule, month);
        cell.setContentDescription(MONTH_NAMES[month - 1] + (tags.isEmpty() ? "" : ": " + tags));
        return cell;
    }

    private MaterialCardView buildMonthCard(String monthName, String tip, boolean isCurrent, String stages) {
        MaterialCardView card = new MaterialCardView(this);
        LinearLayout.LayoutParams cardParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        cardParams.bottomMargin = dpToPx(10);
        card.setLayoutParams(cardParams);
        card.setRadius(dpToPx(20));
        card.setCardElevation(0);
        card.setCardBackgroundColor(isCurrent ? Color.parseColor("#1F4D32") : Color.WHITE);
        card.setStrokeColor(Color.parseColor("#E2E7DC"));
        card.setStrokeWidth(isCurrent ? 0 : dpToPx(1));

        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.VERTICAL);
        row.setPadding(dpToPx(16), dpToPx(14), dpToPx(16), dpToPx(14));

        LinearLayout labelRow = new LinearLayout(this);
        labelRow.setOrientation(LinearLayout.HORIZONTAL);
        labelRow.setGravity(Gravity.CENTER_VERTICAL);

        TextView tvMonth = new TextView(this);
        tvMonth.setText(monthName);
        tvMonth.setTextSize(14);
        tvMonth.setTypeface(null, Typeface.BOLD);
        tvMonth.setTextColor(isCurrent ? Color.WHITE : Color.parseColor("#1C2A20"));
        labelRow.addView(tvMonth);

        if (isCurrent) {
            TextView tvBadge = new TextView(this);
            tvBadge.setText("  \u2022 Now");
            tvBadge.setTextSize(12);
            tvBadge.setTypeface(null, Typeface.BOLD);
            tvBadge.setTextColor(Color.parseColor("#CDE8D0"));
            labelRow.addView(tvBadge);
        }

        TextView tvTip = new TextView(this);
        tvTip.setText(tip);
        tvTip.setTextSize(13f);
        tvTip.setTextColor(isCurrent ? Color.parseColor("#E8F5E9") : Color.parseColor("#5C6B5F"));
        tvTip.setPadding(0, dpToPx(4), 0, 0);

        row.addView(labelRow);
        if (!stages.isEmpty()) {
            TextView tvStages = new TextView(this);
            tvStages.setText(stages);
            tvStages.setTextSize(12f);
            tvStages.setTypeface(null, Typeface.BOLD);
            tvStages.setTextColor(isCurrent ? Color.parseColor("#CDE8D0") : Color.parseColor("#2E7D32"));
            tvStages.setPadding(0, dpToPx(4), 0, 0);
            row.addView(tvStages);
        }
        row.addView(tvTip);
        card.addView(row);

        return card;
    }

    private void fetchForecast() {
        String url = "https://api.openweathermap.org/data/2.5/forecast"
                + "?q=" + WeatherConfig.CITY_QUERY
                + "&appid=" + WeatherConfig.API_KEY
                + "&units=metric";

        JsonObjectRequest request = new JsonObjectRequest(
                Request.Method.GET,
                url,
                null,
                this::processForecast,
                error -> progressForecast.setVisibility(View.GONE)
        );

        requestQueue.add(request);
    }

    private void processForecast(JSONObject response) {
        try {
            JSONArray list = response.getJSONArray("list");

            Map<String, double[]> dayStats = new LinkedHashMap<>();
            Map<String, String> dayCondition = new LinkedHashMap<>();
            SimpleDateFormat dayKeyFormat = new SimpleDateFormat("yyyy-MM-dd", Locale.US);
            SimpleDateFormat dayLabelFormat = new SimpleDateFormat("EEE", Locale.US);

            for (int i = 0; i < list.length(); i++) {
                JSONObject entry = list.getJSONObject(i);
                long timestamp = entry.getLong("dt") * 1000L;
                String dayKey = dayKeyFormat.format(new java.util.Date(timestamp));

                double temp = entry.getJSONObject("main").getDouble("temp");
                double pop = entry.optDouble("pop", 0.0) * 100.0;
                String condition = entry.getJSONArray("weather").getJSONObject(0).getString("main");

                double[] stats = dayStats.get(dayKey);
                if (stats == null) {
                    dayStats.put(dayKey, new double[]{temp, temp, pop});
                    dayCondition.put(dayKey, condition);
                } else {
                    stats[0] = Math.max(stats[0], temp);
                    stats[1] = Math.min(stats[1], temp);
                    if (pop > stats[2]) {
                        stats[2] = pop;
                        dayCondition.put(dayKey, condition);
                    }
                }
            }

            forecastDays.clear();
            forecastConditions.clear();
            forecastDayLabels.clear();

            int count = 0;
            for (Map.Entry<String, double[]> entry : dayStats.entrySet()) {
                if (count >= 5) break;
                forecastDays.add(entry.getValue());
                forecastConditions.add(dayCondition.get(entry.getKey()));
                java.util.Date date = dayKeyFormat.parse(entry.getKey());
                forecastDayLabels.add(count == 0 ? "Today" : dayLabelFormat.format(date));
                count++;
            }

            renderForecastStrip();
            updateAdjustedAdvice();

        } catch (JSONException | java.text.ParseException e) {
            progressForecast.setVisibility(View.GONE);
        }
    }

    private void renderForecastStrip() {
        layoutForecastDays.removeAllViews();

        for (int i = 0; i < forecastDays.size(); i++) {
            double[] stats = forecastDays.get(i);
            String condition = forecastConditions.get(i);
            layoutForecastDays.addView(buildForecastDayCard(
                    forecastDayLabels.get(i),
                    (int) Math.round(stats[0]),
                    (int) Math.round(stats[1]),
                    (int) Math.round(stats[2]),
                    condition
            ));
        }

        progressForecast.setVisibility(View.GONE);
        scrollForecast.setVisibility(View.VISIBLE);
    }

    private MaterialCardView buildForecastDayCard(String label, int maxTemp, int minTemp, int rainChance, String condition) {
        MaterialCardView card = new MaterialCardView(this);
        LinearLayout.LayoutParams cardParams = new LinearLayout.LayoutParams(dpToPx(78), LinearLayout.LayoutParams.WRAP_CONTENT);
        cardParams.rightMargin = dpToPx(10);
        card.setLayoutParams(cardParams);
        card.setRadius(dpToPx(18));
        card.setCardElevation(0);
        card.setCardBackgroundColor(Color.WHITE);
        card.setStrokeColor(Color.parseColor("#E2E7DC"));
        card.setStrokeWidth(dpToPx(1));

        LinearLayout col = new LinearLayout(this);
        col.setOrientation(LinearLayout.VERTICAL);
        col.setGravity(Gravity.CENTER_HORIZONTAL);
        col.setPadding(dpToPx(10), dpToPx(12), dpToPx(10), dpToPx(12));

        TextView tvDay = new TextView(this);
        tvDay.setText(label);
        tvDay.setTextSize(12);
        tvDay.setTypeface(null, Typeface.BOLD);
        tvDay.setTextColor(Color.parseColor("#1C2A20"));

        TextView tvIcon = new TextView(this);
        tvIcon.setText(iconForCondition(condition));
        tvIcon.setTextSize(22);
        tvIcon.setPadding(0, dpToPx(4), 0, dpToPx(4));

        TextView tvTemp = new TextView(this);
        tvTemp.setText(maxTemp + "\u00b0/" + minTemp + "\u00b0");
        tvTemp.setTextSize(12);
        tvTemp.setTextColor(Color.parseColor("#2A3A2E"));

        TextView tvRain = new TextView(this);
        tvRain.setText("\ud83d\udca7 " + rainChance + "%");
        tvRain.setTextSize(11);
        tvRain.setTextColor(Color.parseColor("#1976D2"));
        tvRain.setPadding(0, dpToPx(2), 0, 0);

        col.addView(tvDay);
        col.addView(tvIcon);
        col.addView(tvTemp);
        col.addView(tvRain);
        card.addView(col);

        return card;
    }

    private String iconForCondition(String condition) {
        if (condition == null) return "\u2600\ufe0f";
        switch (condition) {
            case "Thunderstorm": return "\u26c8\ufe0f";
            case "Rain":
            case "Drizzle": return "\ud83c\udf27\ufe0f";
            case "Clouds": return "\u2601\ufe0f";
            case "Clear": return "\u2600\ufe0f";
            default: return "\ud83c\udf24\ufe0f";
        }
    }

    private void updateAdjustedAdvice() {
        if (forecastDays.isEmpty() || selectedCrop == null) {
            cardAdjustedAdvice.setVisibility(View.GONE);
            return;
        }

        int rainyDayCount = 0;
        double maxTempThisWeek = Double.MIN_VALUE;
        for (double[] stats : forecastDays) {
            if (stats[2] >= 50) rainyDayCount++;
            maxTempThisWeek = Math.max(maxTempThisWeek, stats[0]);
        }

        String cropName = selectedCrop.getName().toLowerCase();
        String advice;
        if (rainyDayCount >= 3) {
            advice = "Rain is likely on " + rainyDayCount + " of the next 5 days. "
                    + "Consider delaying transplanting " + cropName
                    + " seedlings until conditions dry out, and check drainage on existing plants.";
        } else if (rainyDayCount >= 1) {
            advice = "Some rain expected this week. Hold off watering on rainy days and watch for waterlogging.";
        } else if (maxTempThisWeek >= 34) {
            advice = "Hot, dry week ahead (up to " + Math.round(maxTempThisWeek)
                    + "\u00b0C). Water " + cropName + " more frequently and consider light afternoon shade.";
        } else {
            advice = "Conditions look favorable this week for regular " + cropName + " care.";
        }

        tvAdjustedAdvice.setText(advice);
        cardAdjustedAdvice.setVisibility(View.VISIBLE);
    }

    // ------------------------------------------------------------ Start Planting

    /** Asks to start today (or another day, for crops planted earlier), then saves the planting. */
    private void confirmStartPlanting() {
        if (selectedCrop == null || mAuth.getCurrentUser() == null) return;
        PlantingSchedule schedule = new PlantingSchedule(selectedCrop);
        if (!schedule.hasData()) return;

        java.time.LocalDate today = com.vinland.app.planting.PlantingRepository.today();
        int days = schedule.getDaysToHarvest();
        String name = selectedCrop.getName();

        StringBuilder msg = new StringBuilder("You'll get daily tips for " + name + " for about " + days
                + " days, until harvest around "
                + today.plusDays(days).format(java.time.format.DateTimeFormatter.ofPattern("MMMM d", Locale.US)) + ".");
        if (!schedule.isPlantMonth(today.getMonthValue())) {
            msg.append("\n\n⚠️ This isn't the best month to plant ").append(name)
                    .append(", so growth may be slower or the harvest smaller.");
        }

        new com.google.android.material.dialog.MaterialAlertDialogBuilder(this)
                .setTitle("Start planting " + name + " today?")
                .setMessage(msg.toString())
                .setNegativeButton("Cancel", null)
                .setNeutralButton("Planted earlier", (d, w) -> pickEarlierDate(today))
                .setPositiveButton("Start today", (d, w) -> savePlanting(today))
                .show();
    }

    private void pickEarlierDate(java.time.LocalDate today) {
        android.app.DatePickerDialog dialog = new android.app.DatePickerDialog(this,
                (view, y, m, d) -> savePlanting(java.time.LocalDate.of(y, m + 1, d)),
                today.getYear(), today.getMonthValue() - 1, today.getDayOfMonth());
        // Only past dates, up to a year back.
        java.time.ZoneId zone = com.vinland.app.planting.PlantingRepository.MANILA;
        dialog.getDatePicker().setMaxDate(today.atStartOfDay(zone).toInstant().toEpochMilli());
        dialog.getDatePicker().setMinDate(today.minusYears(1).atStartOfDay(zone).toInstant().toEpochMilli());
        dialog.show();
    }

    private void savePlanting(java.time.LocalDate start) {
        if (selectedCrop == null || mAuth.getCurrentUser() == null) return;
        PlantingSchedule schedule = new PlantingSchedule(selectedCrop);
        com.vinland.app.planting.Planting planting = new com.vinland.app.planting.Planting(
                selectedCrop.getName(), selectedCrop.getEmoji(), schedule.getDaysToHarvest(), start.toString());

        btnStartPlanting.setEnabled(false);
        com.vinland.app.planting.PlantingRepository.plantings(mAuth.getCurrentUser().getUid())
                .add(planting)
                .addOnSuccessListener(ref -> {
                    btnStartPlanting.setEnabled(true);
                    com.google.android.material.snackbar.Snackbar.make(btnStartPlanting,
                                    "🌱 " + selectedCrop.getName() + " added to My Plantings",
                                    com.google.android.material.snackbar.Snackbar.LENGTH_LONG)
                            .setAction("View", v -> startActivity(new android.content.Intent(this,
                                    com.vinland.app.planting.MyPlantingsActivity.class)))
                            .show();
                })
                .addOnFailureListener(e -> {
                    btnStartPlanting.setEnabled(true);
                    android.widget.Toast.makeText(this, "Could not start planting: " + e.getMessage(),
                            android.widget.Toast.LENGTH_LONG).show();
                });
    }

    private int dpToPx(int dp) {
        float density = getResources().getDisplayMetrics().density;
        return Math.round(dp * density);
    }
}
