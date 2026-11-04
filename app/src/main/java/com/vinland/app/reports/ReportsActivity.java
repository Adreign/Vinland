package com.vinland.app.reports;

import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;

import com.google.android.material.card.MaterialCardView;
import com.google.android.material.chip.ChipGroup;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.Query;
import com.vinland.app.R;
import com.vinland.app.data.SoilLog;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;

public class ReportsActivity extends AppCompatActivity {

    private TextView tvTimeframeLabel, tvAvgMoisture, tvAvgPh, tvTotalLogs, tvEmptyState;
    private ChipGroup chipGroupTimeframe;
    private LinearLayout layoutSoilLogs;
    private ProgressBar progressBar;

    private FirebaseAuth mAuth;
    private FirebaseFirestore db;

    private static final int TIMEFRAME_WEEKLY = 1;
    private static final int TIMEFRAME_MONTHLY = 2;
    private static final int TIMEFRAME_YEARLY = 3;

    private int activeTimeframe = TIMEFRAME_WEEKLY;

    private final List<SoilLog> soilLogsList = new ArrayList<>();
    private final SimpleDateFormat dateFormat = new SimpleDateFormat("MMM dd, yyyy  hh:mm a", Locale.US);

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_reports);

        mAuth = FirebaseAuth.getInstance();
        db = FirebaseFirestore.getInstance();

        TextView tvBack = findViewById(R.id.tvBack);
        chipGroupTimeframe = findViewById(R.id.chipGroupTimeframe);
        tvTimeframeLabel = findViewById(R.id.tvTimeframeLabel);
        tvAvgMoisture = findViewById(R.id.tvAvgMoisture);
        tvAvgPh = findViewById(R.id.tvAvgPh);
        tvTotalLogs = findViewById(R.id.tvTotalLogs);
        tvEmptyState = findViewById(R.id.tvEmptyState);
        layoutSoilLogs = findViewById(R.id.layoutSoilLogs);
        progressBar = findViewById(R.id.progressBar);

        tvBack.setOnClickListener(v -> finish());

        chipGroupTimeframe.setOnCheckedStateChangeListener((group, checkedIds) -> {
            if (!checkedIds.isEmpty()) {
                int checkedId = checkedIds.get(0);
                if (checkedId == R.id.chipWeekly) activeTimeframe = TIMEFRAME_WEEKLY;
                else if (checkedId == R.id.chipMonthly) activeTimeframe = TIMEFRAME_MONTHLY;
                else if (checkedId == R.id.chipYearly) activeTimeframe = TIMEFRAME_YEARLY;
                loadReportData();
            }
        });

        loadReportData();
    }

    private long getCutoffTimestamp() {
        long now = System.currentTimeMillis();
        switch (activeTimeframe) {
            case TIMEFRAME_WEEKLY:
                tvTimeframeLabel.setText("Showing data for past 7 days");
                return now - (7L * 24 * 60 * 60 * 1000);
            case TIMEFRAME_MONTHLY:
                tvTimeframeLabel.setText("Showing data for past 30 days");
                return now - (30L * 24 * 60 * 60 * 1000);
            case TIMEFRAME_YEARLY:
                tvTimeframeLabel.setText("Showing data for past 365 days");
                return now - (365L * 24 * 60 * 60 * 1000);
            default:
                return 0;
        }
    }

    private void loadReportData() {
        if (mAuth.getCurrentUser() == null) return;
        String uid = mAuth.getCurrentUser().getUid();

        progressBar.setVisibility(View.VISIBLE);
        long cutoff = getCutoffTimestamp();

        // Fetch soil logs
        db.collection("users").document(uid).collection("soil_logs")
                .whereGreaterThanOrEqualTo("timestamp", cutoff)
                .orderBy("timestamp", Query.Direction.DESCENDING)
                .get()
                .addOnSuccessListener(soilSnap -> {
                    soilLogsList.clear();
                    for (DocumentSnapshot doc : soilSnap.getDocuments()) {
                        SoilLog log = doc.toObject(SoilLog.class);
                        if (log != null) soilLogsList.add(log);
                    }
                    progressBar.setVisibility(View.GONE);
                    updateUI();
                })
                .addOnFailureListener(e -> {
                    progressBar.setVisibility(View.GONE);
                    updateUI();
                });
    }

    private void updateUI() {
        double totalMoisture = 0;
        double totalPh = 0;
        for (SoilLog log : soilLogsList) {
            totalMoisture += log.getMoisture();
            totalPh += log.getPh();
        }

        double avgMoisture = soilLogsList.isEmpty() ? 0 : totalMoisture / soilLogsList.size();
        double avgPh = soilLogsList.isEmpty() ? 0 : totalPh / soilLogsList.size();

        tvAvgMoisture.setText(soilLogsList.isEmpty() ? "--%" : String.format(Locale.US, "%.0f%%", avgMoisture));
        tvAvgPh.setText(soilLogsList.isEmpty() ? "--" : String.format(Locale.US, "%.1f", avgPh));
        tvTotalLogs.setText(String.valueOf(soilLogsList.size()));

        renderSoilLogs();
        tvEmptyState.setVisibility(soilLogsList.isEmpty() ? View.VISIBLE : View.GONE);
    }

    private void renderSoilLogs() {
        layoutSoilLogs.removeAllViews();
        for (SoilLog log : soilLogsList) {
            layoutSoilLogs.addView(buildSoilLogCard(log));
        }
    }

    private MaterialCardView buildSoilLogCard(SoilLog log) {
        MaterialCardView card = new MaterialCardView(this);
        LinearLayout.LayoutParams cardParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        cardParams.bottomMargin = dpToPx(10);
        card.setLayoutParams(cardParams);
        card.setRadius(dpToPx(14));
        card.setCardElevation(0);
        card.setCardBackgroundColor(android.graphics.Color.WHITE);

        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.VERTICAL);
        row.setPadding(dpToPx(16), dpToPx(12), dpToPx(16), dpToPx(12));

        // Header row with Title and Simple Delete text link
        LinearLayout headerRow = new LinearLayout(this);
        headerRow.setOrientation(LinearLayout.HORIZONTAL);
        headerRow.setGravity(Gravity.CENTER_VERTICAL);

        TextView tvTitle = new TextView(this);
        tvTitle.setText(log.getCropName() + " Soil Test");
        tvTitle.setTextSize(14);
        tvTitle.setTypeface(null, android.graphics.Typeface.BOLD);
        tvTitle.setTextColor(android.graphics.Color.parseColor("#212121"));
        tvTitle.setLayoutParams(new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1));

        TextView tvDelete = new TextView(this);
        tvDelete.setText("Delete");
        tvDelete.setTextSize(12.5f);
        tvDelete.setTextColor(android.graphics.Color.parseColor("#D32F2F"));
        tvDelete.setPadding(dpToPx(8), dpToPx(4), dpToPx(8), dpToPx(4));
        tvDelete.setOnClickListener(v -> confirmDeleteSoilLog(log));

        headerRow.addView(tvTitle);
        headerRow.addView(tvDelete);
        row.addView(headerRow);

        TextView tvDetails = new TextView(this);
        tvDetails.setText(String.format(Locale.US, "💧 Moisture: %.0f%%   |   ⚗️ pH: %.1f   |   🌿 NPK: %.0f-%.0f-%.0f",
                log.getMoisture(), log.getPh(), log.getNitrogen(), log.getPhosphorus(), log.getPotassium()));
        tvDetails.setTextSize(12.5f);
        tvDetails.setTextColor(android.graphics.Color.parseColor("#424242"));
        tvDetails.setPadding(0, dpToPx(4), 0, dpToPx(4));

        TextView tvDate = new TextView(this);
        tvDate.setText("📅 " + dateFormat.format(new Date(log.getTimestamp())));
        tvDate.setTextSize(11);
        tvDate.setTextColor(android.graphics.Color.parseColor("#9E9E9E"));

        row.addView(tvDetails);
        row.addView(tvDate);
        card.addView(row);

        card.setOnLongClickListener(v -> {
            confirmDeleteSoilLog(log);
            return true;
        });

        return card;
    }

    private void confirmDeleteSoilLog(SoilLog log) {
        new AlertDialog.Builder(this)
                .setTitle("Delete Soil Log")
                .setMessage("Are you sure you want to delete this soil log for " + log.getCropName() + "?")
                .setPositiveButton("Delete", (dialog, which) -> deleteSoilLog(log))
                .setNegativeButton("Cancel", (dialog, which) -> dialog.dismiss())
                .show();
    }

    private void deleteSoilLog(SoilLog log) {
        if (mAuth.getCurrentUser() == null || log == null || log.getId() == null) return;
        String uid = mAuth.getCurrentUser().getUid();

        db.collection("users").document(uid).collection("soil_logs").document(log.getId())
                .delete()
                .addOnSuccessListener(aVoid -> {
                    Toast.makeText(this, "Soil log deleted", Toast.LENGTH_SHORT).show();
                    loadReportData();
                })
                .addOnFailureListener(e -> Toast.makeText(this, "Could not delete: " + e.getMessage(), Toast.LENGTH_SHORT).show());
    }

    private int dpToPx(int dp) {
        float density = getResources().getDisplayMetrics().density;
        return Math.round(dp * density);
    }
}
