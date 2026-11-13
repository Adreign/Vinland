package com.vinland.app.planting;

import android.content.Intent;
import android.graphics.Color;
import android.graphics.Typeface;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.InputType;
import android.view.Gravity;
import android.view.View;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;

import com.google.android.material.button.MaterialButton;
import com.google.android.material.card.MaterialCardView;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.progressindicator.LinearProgressIndicator;
import com.google.android.material.snackbar.Snackbar;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.firestore.DocumentReference;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.WriteBatch;
import com.vinland.app.R;
import com.vinland.app.calendar.PlantingCalendarActivity;

import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/** Shows each active planting: day X of Y, growth stage, progress and today's tasks. */
public class MyPlantingsActivity extends AppCompatActivity {

    private static final ExecutorService EXECUTOR = Executors.newSingleThreadExecutor();
    private static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("MMM d, yyyy", Locale.US);

    private final Handler main = new Handler(Looper.getMainLooper());

    private LinearLayout layoutPlantings;
    private ProgressBar progressBar;
    private MaterialCardView cardEmpty;
    private TextView tvError, tvSubtitle;

    private FirebaseAuth mAuth;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_my_plantings);

        mAuth = FirebaseAuth.getInstance();

        layoutPlantings = findViewById(R.id.layoutPlantings);
        progressBar = findViewById(R.id.progressBar);
        cardEmpty = findViewById(R.id.cardEmpty);
        tvError = findViewById(R.id.tvError);
        tvSubtitle = findViewById(R.id.tvSubtitle);

        findViewById(R.id.tvBack).setOnClickListener(v -> finish());
        findViewById(R.id.btnOpenCalendar).setOnClickListener(v ->
                startActivity(new Intent(this, PlantingCalendarActivity.class)));
    }

    @Override
    protected void onResume() {
        super.onResume();
        load(); // also refreshes after starting a planting in the calendar
    }

    private void load() {
        if (mAuth.getCurrentUser() == null) {
            finish();
            return;
        }
        String uid = mAuth.getCurrentUser().getUid();
        progressBar.setVisibility(View.VISIBLE);
        tvError.setVisibility(View.GONE);

        EXECUTOR.execute(() -> {
            try {
                List<PlantingRepository.Item> items = PlantingRepository.loadToday(uid);
                main.post(() -> show(items));
            } catch (Exception e) {
                main.post(() -> {
                    progressBar.setVisibility(View.GONE);
                    tvError.setText("Could not load your plantings. Check your internet connection and try again.");
                    tvError.setVisibility(View.VISIBLE);
                });
            }
        });
    }

    private void show(List<PlantingRepository.Item> items) {
        if (isFinishing()) return;
        progressBar.setVisibility(View.GONE);
        layoutPlantings.removeAllViews();
        cardEmpty.setVisibility(items.isEmpty() ? View.VISIBLE : View.GONE);
        tvSubtitle.setText(items.isEmpty()
                ? "Today's tasks for what you're growing"
                : items.size() + (items.size() == 1 ? " active planting" : " active plantings")
                + " · " + PlantingRepository.today().format(DATE));
        for (PlantingRepository.Item item : items) {
            layoutPlantings.addView(buildCard(item));
        }
    }

    private View buildCard(PlantingRepository.Item item) {
        Planting p = item.planting;
        PlantingAdvisor.Advice a = item.advice;

        MaterialCardView card = new MaterialCardView(this);
        LinearLayout.LayoutParams cardParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        cardParams.bottomMargin = dp(14);
        card.setLayoutParams(cardParams);
        card.setCardBackgroundColor(Color.WHITE);
        card.setRadius(dp(20));
        card.setCardElevation(0);
        card.setStrokeColor(Color.parseColor("#E2E7DC"));
        card.setStrokeWidth(dp(1));

        LinearLayout col = new LinearLayout(this);
        col.setOrientation(LinearLayout.VERTICAL);
        col.setPadding(dp(16), dp(16), dp(16), dp(12));

        // Crop name + stage badge
        LinearLayout titleRow = new LinearLayout(this);
        titleRow.setOrientation(LinearLayout.HORIZONTAL);
        titleRow.setGravity(Gravity.CENTER_VERTICAL);

        TextView tvName = text((p.getEmoji() != null ? p.getEmoji() + " " : "") + p.getCropName(), 17, "#1C2A20", true);
        titleRow.addView(tvName, new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));

        TextView tvStage = text(a.stage.emoji + " " + a.stage.label, 11.5f, "#2E7D32", true);
        android.graphics.drawable.GradientDrawable pill = new android.graphics.drawable.GradientDrawable();
        pill.setColor(Color.parseColor("#E8F5E9"));
        pill.setCornerRadius(dp(50));
        tvStage.setBackground(pill);
        tvStage.setPadding(dp(10), dp(4), dp(10), dp(4));
        titleRow.addView(tvStage);
        col.addView(titleRow);

        // Day X of Y + progress
        String dayLine = "Day " + a.dayNumber + " of " + a.totalDays;
        if (a.dayNumber <= a.totalDays) {
            dayLine += " · " + a.daysLeft() + (a.daysLeft() == 1 ? " day" : " days") + " to harvest";
        }
        TextView tvDay = text(dayLine, 13, "#2A3A2E", false);
        tvDay.setPadding(0, dp(8), 0, dp(6));
        col.addView(tvDay);

        LinearProgressIndicator bar = new LinearProgressIndicator(this);
        bar.setMax(100);
        bar.setProgressCompat(a.progress, false);
        bar.setIndicatorColor(Color.parseColor("#2E7D32"));
        bar.setTrackColor(Color.parseColor("#E2E7DC"));
        bar.setTrackCornerRadius(dp(4));
        bar.setTrackThickness(dp(8));
        col.addView(bar);

        TextView tvDates = text("Planted " + java.time.LocalDate.parse(p.getStartDate()).format(DATE)
                + "  ·  Expected harvest " + a.harvestDate.format(DATE), 11.5f, "#5C6B5F", false);
        tvDates.setPadding(0, dp(6), 0, 0);
        col.addView(tvDates);

        // Today's tasks
        TextView tvTasksTitle = text("Today's tasks", 14, "#1C2A20", true);
        tvTasksTitle.setPadding(0, dp(14), 0, dp(4));
        col.addView(tvTasksTitle);

        for (String task : a.tasks) {
            TextView tvTask = text(task, 13.5f, "#2A3A2E", false);
            tvTask.setPadding(0, dp(4), 0, dp(4));
            tvTask.setLineSpacing(dp(2), 1f);
            col.addView(tvTask);
        }

        // Actions
        LinearLayout actions = new LinearLayout(this);
        actions.setOrientation(LinearLayout.HORIZONTAL);
        actions.setGravity(Gravity.END);
        actions.setPadding(0, dp(8), 0, 0);

        MaterialButton btnStop = new MaterialButton(this, null, android.R.attr.borderlessButtonStyle);
        btnStop.setText("Stop");
        btnStop.setTextColor(Color.parseColor("#5C6B5F"));
        btnStop.setOnClickListener(v -> confirmStop(p));
        actions.addView(btnStop);

        MaterialButton btnHarvest = new MaterialButton(this);
        btnHarvest.setText("🧺 Harvested");
        btnHarvest.setBackgroundTintList(android.content.res.ColorStateList.valueOf(Color.parseColor("#1F4D32")));
        btnHarvest.setTextColor(Color.WHITE);
        btnHarvest.setOnClickListener(v -> askHarvest(p, a));
        LinearLayout.LayoutParams hp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        hp.leftMargin = dp(8);
        actions.addView(btnHarvest, hp);

        col.addView(actions);
        card.addView(col);
        return card;
    }

    /** Asks how much was harvested (optional), then saves a harvest log and ends the planting. */
    private void askHarvest(Planting p, PlantingAdvisor.Advice a) {
        EditText etKg = new EditText(this);
        etKg.setHint("Amount in kg (optional)");
        etKg.setInputType(InputType.TYPE_CLASS_NUMBER | InputType.TYPE_NUMBER_FLAG_DECIMAL);
        FrameLayout box = new FrameLayout(this);
        box.setPadding(dp(24), dp(8), dp(24), 0);
        box.addView(etKg);

        new MaterialAlertDialogBuilder(this)
                .setTitle("Harvested " + p.getCropName() + "?")
                .setMessage("This saves it to your harvest log and stops the daily tips for this planting.")
                .setView(box)
                .setNegativeButton("Cancel", null)
                .setPositiveButton("Save harvest", (d, w) -> {
                    double kg = 0;
                    try {
                        kg = Double.parseDouble(etKg.getText().toString().trim());
                    } catch (NumberFormatException ignored) {
                    }
                    saveHarvest(p, a, kg);
                })
                .show();
    }

    private void saveHarvest(Planting p, PlantingAdvisor.Advice a, double kg) {
        String uid = mAuth.getCurrentUser().getUid();
        FirebaseFirestore db = FirebaseFirestore.getInstance();
        long now = System.currentTimeMillis();

        DocumentReference logRef = db.collection("users").document(uid).collection("harvest_logs").document();
        Map<String, Object> log = new HashMap<>();
        log.put("id", logRef.getId());
        log.put("cropName", p.getCropName());
        log.put("quantityKg", kg);
        log.put("notes", "Planted " + p.getStartDate() + ", harvested on day " + a.dayNumber
                + " of " + a.totalDays + ".");
        log.put("timestamp", now);
        log.put("plantingId", p.getId());

        Map<String, Object> end = new HashMap<>();
        end.put("status", Planting.HARVESTED);
        end.put("endedAt", now);

        WriteBatch batch = db.batch();
        batch.set(logRef, log);
        batch.update(PlantingRepository.plantings(uid).document(p.getId()), end);
        batch.commit()
                .addOnSuccessListener(u -> {
                    Snackbar.make(layoutPlantings, "🧺 Harvest saved. Great job!", Snackbar.LENGTH_LONG).show();
                    load();
                })
                .addOnFailureListener(e ->
                        Snackbar.make(layoutPlantings, "Could not save: " + e.getMessage(), Snackbar.LENGTH_LONG).show());
    }

    private void confirmStop(Planting p) {
        new MaterialAlertDialogBuilder(this)
                .setTitle("Stop tracking " + p.getCropName() + "?")
                .setMessage("Use this if the planting didn't work out. Daily tips for it will stop.")
                .setNegativeButton("Cancel", null)
                .setPositiveButton("Stop", (d, w) -> {
                    Map<String, Object> end = new HashMap<>();
                    end.put("status", Planting.STOPPED);
                    end.put("endedAt", System.currentTimeMillis());
                    PlantingRepository.plantings(mAuth.getCurrentUser().getUid()).document(p.getId())
                            .update(end)
                            .addOnSuccessListener(u -> load())
                            .addOnFailureListener(e -> Snackbar.make(layoutPlantings,
                                    "Could not stop: " + e.getMessage(), Snackbar.LENGTH_LONG).show());
                })
                .show();
    }

    private TextView text(String s, float size, String color, boolean bold) {
        TextView tv = new TextView(this);
        tv.setText(s);
        tv.setTextSize(size);
        tv.setTextColor(Color.parseColor(color));
        if (bold) tv.setTypeface(null, Typeface.BOLD);
        return tv;
    }

    private int dp(int v) {
        return Math.round(v * getResources().getDisplayMetrics().density);
    }
}
