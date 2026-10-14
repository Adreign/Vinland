package com.vinland.app.calendar;

import android.graphics.Color;
import android.graphics.Typeface;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
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
import com.google.android.material.chip.Chip;
import com.google.android.material.chip.ChipGroup;
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

    private ChipGroup chipGroupCrop;
    private LinearLayout layoutMonths, layoutForecastDays;
    private ProgressBar progressForecast;
    private HorizontalScrollView scrollForecast;
    private MaterialCardView cardAdjustedAdvice;
    private TextView tvAdjustedAdvice;

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
        chipGroupCrop = findViewById(R.id.chipGroupCrop);
        layoutMonths = findViewById(R.id.layoutMonths);
        layoutForecastDays = findViewById(R.id.layoutForecastDays);
        progressForecast = findViewById(R.id.progressForecast);
        scrollForecast = findViewById(R.id.scrollForecast);
        cardAdjustedAdvice = findViewById(R.id.cardAdjustedAdvice);
        tvAdjustedAdvice = findViewById(R.id.tvAdjustedAdvice);

        tvBack.setOnClickListener(v -> finish());

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
            String displayName = crop.getEmoji() != null ? crop.getEmoji() + " " + crop.getName() : crop.getName();
            chip.setText(displayName);
            chip.setTag(crop);
            chip.setCheckable(true);
            chip.setCheckedIconVisible(false);
            chipGroupCrop.addView(chip);
        }

        if (chipGroupCrop.getChildCount() > 0) {
            Chip firstChip = (Chip) chipGroupCrop.getChildAt(0);
            firstChip.setChecked(true);
            selectedCrop = (Crop) firstChip.getTag();
        }

        chipGroupCrop.setOnCheckedStateChangeListener((group, checkedIds) -> {
            if (!checkedIds.isEmpty()) {
                Chip checked = group.findViewById(checkedIds.get(0));
                if (checked != null && checked.getTag() instanceof Crop) {
                    selectedCrop = (Crop) checked.getTag();
                    renderMonths();
                    updateAdjustedAdvice();
                }
            }
        });

        renderMonths();
    }

    private void renderMonths() {
        layoutMonths.removeAllViews();
        if (selectedCrop == null) return;

        List<String> tips = selectedCrop.getMonthlyTips();
        int currentMonth = Calendar.getInstance().get(Calendar.MONTH);

        for (int i = 0; i < 12; i++) {
            boolean isCurrent = (i == currentMonth);
            String tip = (tips != null && i < tips.size()) ? tips.get(i) : "Regular care and monitoring month for " + selectedCrop.getName() + ".";
            layoutMonths.addView(buildMonthCard(MONTH_NAMES[i], tip, isCurrent));
        }
    }

    private MaterialCardView buildMonthCard(String monthName, String tip, boolean isCurrent) {
        MaterialCardView card = new MaterialCardView(this);
        LinearLayout.LayoutParams cardParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        cardParams.bottomMargin = dpToPx(10);
        card.setLayoutParams(cardParams);
        card.setRadius(dpToPx(14));
        card.setCardElevation(isCurrent ? dpToPx(2) : 0);
        card.setCardBackgroundColor(isCurrent ? Color.parseColor("#2E7D32") : Color.WHITE);

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
        tvMonth.setTextColor(isCurrent ? Color.WHITE : Color.parseColor("#212121"));
        labelRow.addView(tvMonth);

        if (isCurrent) {
            TextView tvBadge = new TextView(this);
            tvBadge.setText("  \u2022 Now");
            tvBadge.setTextSize(12);
            tvBadge.setTypeface(null, Typeface.BOLD);
            tvBadge.setTextColor(Color.parseColor("#C8E6C9"));
            labelRow.addView(tvBadge);
        }

        TextView tvTip = new TextView(this);
        tvTip.setText(tip);
        tvTip.setTextSize(13f);
        tvTip.setTextColor(isCurrent ? Color.parseColor("#E8F5E9") : Color.parseColor("#666666"));
        tvTip.setPadding(0, dpToPx(4), 0, 0);

        row.addView(labelRow);
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
        card.setRadius(dpToPx(14));
        card.setCardElevation(dpToPx(1));
        card.setCardBackgroundColor(Color.WHITE);

        LinearLayout col = new LinearLayout(this);
        col.setOrientation(LinearLayout.VERTICAL);
        col.setGravity(Gravity.CENTER_HORIZONTAL);
        col.setPadding(dpToPx(10), dpToPx(12), dpToPx(10), dpToPx(12));

        TextView tvDay = new TextView(this);
        tvDay.setText(label);
        tvDay.setTextSize(12);
        tvDay.setTypeface(null, Typeface.BOLD);
        tvDay.setTextColor(Color.parseColor("#212121"));

        TextView tvIcon = new TextView(this);
        tvIcon.setText(iconForCondition(condition));
        tvIcon.setTextSize(22);
        tvIcon.setPadding(0, dpToPx(4), 0, dpToPx(4));

        TextView tvTemp = new TextView(this);
        tvTemp.setText(maxTemp + "\u00b0/" + minTemp + "\u00b0");
        tvTemp.setTextSize(12);
        tvTemp.setTextColor(Color.parseColor("#424242"));

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

    private int dpToPx(int dp) {
        float density = getResources().getDisplayMetrics().density;
        return Math.round(dp * density);
    }
}
