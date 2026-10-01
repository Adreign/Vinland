package com.vinland.app.reports;

import android.os.Bundle;
import android.text.TextUtils;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.AutoCompleteTextView;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;

import com.google.android.material.button.MaterialButton;
import com.google.android.material.card.MaterialCardView;
import com.google.android.material.chip.ChipGroup;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.Query;
import com.vinland.app.R;
import com.vinland.app.data.Crop;
import com.vinland.app.data.HarvestLog;
import com.vinland.app.data.SoilLog;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;

public class ReportsActivity extends AppCompatActivity {

    private TextView tvTimeframeLabel, tvTotalHarvest, tvAvgMoisture, tvAvgPh, tvTotalLogs, tvEmptyState;
    private ChipGroup chipGroupTimeframe;
    private MaterialButton btnTabSoil, btnTabHarvest;
    private LinearLayout layoutSoilLogs, layoutHarvestLogs;
    private ProgressBar progressBar;

    private FirebaseAuth mAuth;
    private FirebaseFirestore db;

    private static final int TIMEFRAME_WEEKLY = 1;
    private static final int TIMEFRAME_MONTHLY = 2;
    private static final int TIMEFRAME_YEARLY = 3;

    private int activeTimeframe = TIMEFRAME_WEEKLY;
    private boolean showingSoilTab = true;

    private final List<SoilLog> soilLogsList = new ArrayList<>();
    private final List<HarvestLog> harvestLogsList = new ArrayList<>();

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
        tvTotalHarvest = findViewById(R.id.tvTotalHarvest);
        tvAvgMoisture = findViewById(R.id.tvAvgMoisture);
        tvAvgPh = findViewById(R.id.tvAvgPh);
        tvTotalLogs = findViewById(R.id.tvTotalLogs);
        tvEmptyState = findViewById(R.id.tvEmptyState);
        btnTabSoil = findViewById(R.id.btnTabSoil);
        btnTabHarvest = findViewById(R.id.btnTabHarvest);
        MaterialButton btnLogHarvest = findViewById(R.id.btnLogHarvest);
        layoutSoilLogs = findViewById(R.id.layoutSoilLogs);
        layoutHarvestLogs = findViewById(R.id.layoutHarvestLogs);
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

        btnTabSoil.setOnClickListener(v -> switchTab(true));
        btnTabHarvest.setOnClickListener(v -> switchTab(false));
        btnLogHarvest.setOnClickListener(v -> showAddHarvestDialog());

        loadReportData();
    }

    private void switchTab(boolean showSoil) {
        showingSoilTab = showSoil;
        if (showSoil) {
            btnTabSoil.setBackgroundTintList(android.content.res.ColorStateList.valueOf(android.graphics.Color.parseColor("#E8F5E9")));
            btnTabSoil.setTextColor(android.graphics.Color.parseColor("#2E7D32"));
            btnTabHarvest.setBackgroundTintList(null);
            btnTabHarvest.setTextColor(android.graphics.Color.parseColor("#666666"));

            layoutSoilLogs.setVisibility(View.VISIBLE);
            layoutHarvestLogs.setVisibility(View.GONE);
        } else {
            btnTabHarvest.setBackgroundTintList(android.content.res.ColorStateList.valueOf(android.graphics.Color.parseColor("#E8F5E9")));
            btnTabHarvest.setTextColor(android.graphics.Color.parseColor("#2E7D32"));
            btnTabSoil.setBackgroundTintList(null);
            btnTabSoil.setTextColor(android.graphics.Color.parseColor("#666666"));

            layoutHarvestLogs.setVisibility(View.VISIBLE);
            layoutSoilLogs.setVisibility(View.GONE);
        }

        updateEmptyState();
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

                    // Fetch harvest logs
                    db.collection("users").document(uid).collection("harvest_logs")
                            .whereGreaterThanOrEqualTo("timestamp", cutoff)
                            .orderBy("timestamp", Query.Direction.DESCENDING)
                            .get()
                            .addOnSuccessListener(harvestSnap -> {
                                harvestLogsList.clear();
                                for (DocumentSnapshot doc : harvestSnap.getDocuments()) {
                                    HarvestLog log = doc.toObject(HarvestLog.class);
                                    if (log != null) harvestLogsList.add(log);
                                }
                                progressBar.setVisibility(View.GONE);
                                updateUI();
                            })
                            .addOnFailureListener(e -> {
                                progressBar.setVisibility(View.GONE);
                                updateUI();
                            });
                })
                .addOnFailureListener(e -> {
                    progressBar.setVisibility(View.GONE);
                    updateUI();
                });
    }

    private void updateUI() {
        // Calculate Analytics
        double totalHarvestKg = 0;
        for (HarvestLog log : harvestLogsList) {
            totalHarvestKg += log.getQuantityKg();
        }

        double totalMoisture = 0;
        double totalPh = 0;
        for (SoilLog log : soilLogsList) {
            totalMoisture += log.getMoisture();
            totalPh += log.getPh();
        }

        double avgMoisture = soilLogsList.isEmpty() ? 0 : totalMoisture / soilLogsList.size();
        double avgPh = soilLogsList.isEmpty() ? 0 : totalPh / soilLogsList.size();

        tvTotalHarvest.setText(String.format(Locale.US, "%.1f kg", totalHarvestKg));
        tvAvgMoisture.setText(soilLogsList.isEmpty() ? "--%" : String.format(Locale.US, "%.0f%%", avgMoisture));
        tvAvgPh.setText(soilLogsList.isEmpty() ? "--" : String.format(Locale.US, "%.1f", avgPh));
        tvTotalLogs.setText(String.valueOf(soilLogsList.size() + harvestLogsList.size()));

        renderSoilLogs();
        renderHarvestLogs();
        updateEmptyState();
    }

    private void updateEmptyState() {
        boolean isEmpty = showingSoilTab ? soilLogsList.isEmpty() : harvestLogsList.isEmpty();
        tvEmptyState.setVisibility(isEmpty ? View.VISIBLE : View.GONE);
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

    private void renderHarvestLogs() {
        layoutHarvestLogs.removeAllViews();
        for (HarvestLog log : harvestLogsList) {
            layoutHarvestLogs.addView(buildHarvestLogCard(log));
        }
    }

    private MaterialCardView buildHarvestLogCard(HarvestLog log) {
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
        tvTitle.setText("🌾 " + log.getCropName() + " — " + String.format(Locale.US, "%.1f kg", log.getQuantityKg()));
        tvTitle.setTextSize(14);
        tvTitle.setTypeface(null, android.graphics.Typeface.BOLD);
        tvTitle.setTextColor(android.graphics.Color.parseColor("#2E7D32"));
        tvTitle.setLayoutParams(new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1));

        TextView tvDelete = new TextView(this);
        tvDelete.setText("Delete");
        tvDelete.setTextSize(12.5f);
        tvDelete.setTextColor(android.graphics.Color.parseColor("#D32F2F"));
        tvDelete.setPadding(dpToPx(8), dpToPx(4), dpToPx(8), dpToPx(4));
        tvDelete.setOnClickListener(v -> confirmDeleteHarvestLog(log));

        headerRow.addView(tvTitle);
        headerRow.addView(tvDelete);
        row.addView(headerRow);

        if (!TextUtils.isEmpty(log.getNotes())) {
            TextView tvNotes = new TextView(this);
            tvNotes.setText("📝 " + log.getNotes());
            tvNotes.setTextSize(12);
            tvNotes.setTextColor(android.graphics.Color.parseColor("#666666"));
            tvNotes.setPadding(0, dpToPx(2), 0, dpToPx(2));
            row.addView(tvNotes);
        }

        TextView tvDate = new TextView(this);
        tvDate.setText("📅 " + dateFormat.format(new Date(log.getTimestamp())));
        tvDate.setTextSize(11);
        tvDate.setTextColor(android.graphics.Color.parseColor("#9E9E9E"));
        row.addView(tvDate);

        card.addView(row);

        card.setOnLongClickListener(v -> {
            confirmDeleteHarvestLog(log);
            return true;
        });

        return card;
    }

    private void confirmDeleteHarvestLog(HarvestLog log) {
        new AlertDialog.Builder(this)
                .setTitle("Delete Harvest Log")
                .setMessage("Are you sure you want to delete this harvest log for " + log.getCropName() + "?")
                .setPositiveButton("Delete", (dialog, which) -> deleteHarvestLog(log))
                .setNegativeButton("Cancel", (dialog, which) -> dialog.dismiss())
                .show();
    }

    private void deleteHarvestLog(HarvestLog log) {
        if (mAuth.getCurrentUser() == null || log == null || log.getId() == null) return;
        String uid = mAuth.getCurrentUser().getUid();

        db.collection("users").document(uid).collection("harvest_logs").document(log.getId())
                .delete()
                .addOnSuccessListener(aVoid -> {
                    Toast.makeText(this, "Harvest log deleted", Toast.LENGTH_SHORT).show();
                    loadReportData();
                })
                .addOnFailureListener(e -> Toast.makeText(this, "Could not delete: " + e.getMessage(), Toast.LENGTH_SHORT).show());
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

    private void showAddHarvestDialog() {
        View dialogView = LayoutInflater.from(this).inflate(R.layout.dialog_add_harvest, null);
        AutoCompleteTextView actvCropName = dialogView.findViewById(R.id.actvDialogCropName);
        EditText etQuantity = dialogView.findViewById(R.id.etDialogQuantity);
        EditText etNotes = dialogView.findViewById(R.id.etDialogNotes);

        // Fetch crop names dynamically from Firestore "crops" collection for autocomplete suggestions
        db.collection("crops").get().addOnSuccessListener(querySnapshot -> {
            List<String> cropNames = new ArrayList<>();
            for (DocumentSnapshot doc : querySnapshot.getDocuments()) {
                Crop crop = doc.toObject(Crop.class);
                if (crop != null && crop.getName() != null) {
                    cropNames.add(crop.getName());
                }
            }
            if (cropNames.isEmpty()) {
                cropNames.add("Tomato");
                cropNames.add("Green Pepper");
            }
            ArrayAdapter<String> adapter = new ArrayAdapter<>(
                    this,
                    android.R.layout.simple_dropdown_item_1line,
                    cropNames
            );
            actvCropName.setAdapter(adapter);
        });

        new AlertDialog.Builder(this)
                .setView(dialogView)
                .setPositiveButton("Save Log", (dialog, which) -> {
                    String cropName = actvCropName.getText().toString().trim();
                    String qtyStr = etQuantity.getText().toString().trim();
                    String notes = etNotes.getText().toString().trim();

                    if (TextUtils.isEmpty(cropName) || TextUtils.isEmpty(qtyStr)) {
                        Toast.makeText(this, "Please select/enter crop name and harvested weight.", Toast.LENGTH_SHORT).show();
                        return;
                    }

                    double qty;
                    try {
                        qty = Double.parseDouble(qtyStr);
                    } catch (NumberFormatException e) {
                        Toast.makeText(this, "Please enter a valid yield weight.", Toast.LENGTH_SHORT).show();
                        return;
                    }

                    saveHarvestLog(cropName, qty, notes);
                })
                .setNegativeButton("Cancel", (dialog, which) -> dialog.dismiss())
                .show();
    }

    private void saveHarvestLog(String cropName, double quantityKg, String notes) {
        if (mAuth.getCurrentUser() == null) return;
        String uid = mAuth.getCurrentUser().getUid();

        String logId = db.collection("users").document(uid).collection("harvest_logs").document().getId();
        HarvestLog log = new HarvestLog(logId, cropName, quantityKg, notes, System.currentTimeMillis());

        db.collection("users").document(uid).collection("harvest_logs").document(logId)
                .set(log)
                .addOnSuccessListener(unused -> {
                    Toast.makeText(this, "Harvest log saved!", Toast.LENGTH_SHORT).show();
                    loadReportData();
                })
                .addOnFailureListener(e -> Toast.makeText(this, "Failed to save harvest log: " + e.getMessage(), Toast.LENGTH_SHORT).show());
    }

    private int dpToPx(int dp) {
        float density = getResources().getDisplayMetrics().density;
        return Math.round(dp * density);
    }
}
