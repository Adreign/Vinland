package com.vinland.app.notifications;

import android.graphics.Color;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;

import com.google.android.material.card.MaterialCardView;
import com.google.android.material.chip.ChipGroup;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.Query;
import com.vinland.app.R;
import com.vinland.app.data.NotificationItem;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;

public class NotificationHistoryActivity extends AppCompatActivity {

    private LinearLayout layoutNotificationList;
    private TextView tvEmptyState;
    private ProgressBar progressBar;
    private ChipGroup chipGroupCategory;

    private FirebaseAuth mAuth;
    private FirebaseFirestore db;

    private final List<NotificationItem> allNotificationsList = new ArrayList<>();
    private String selectedCategoryFilter = "all";

    private final SimpleDateFormat dateFormat = new SimpleDateFormat("MMM dd, yyyy  hh:mm a", Locale.US);

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_notification_history);

        mAuth = FirebaseAuth.getInstance();
        db = FirebaseFirestore.getInstance();

        TextView tvBack = findViewById(R.id.tvBack);
        layoutNotificationList = findViewById(R.id.layoutNotificationList);
        tvEmptyState = findViewById(R.id.tvEmptyState);
        progressBar = findViewById(R.id.progressBar);
        chipGroupCategory = findViewById(R.id.chipGroupCategory);

        tvBack.setOnClickListener(v -> finish());

        chipGroupCategory.setOnCheckedStateChangeListener((group, checkedIds) -> {
            if (checkedIds != null && !checkedIds.isEmpty()) {
                int checkedId = checkedIds.get(0);
                if (checkedId == R.id.chipRain) {
                    selectedCategoryFilter = "rain";
                } else if (checkedId == R.id.chipHeat) {
                    selectedCategoryFilter = "heat";
                } else if (checkedId == R.id.chipHumidity) {
                    selectedCategoryFilter = "humidity";
                } else if (checkedId == R.id.chipGeneral) {
                    selectedCategoryFilter = "general";
                } else {
                    selectedCategoryFilter = "all";
                }
                renderNotifications();
            }
        });

        loadNotifications();
    }

    private void loadNotifications() {
        if (mAuth.getCurrentUser() == null) {
            finish();
            return;
        }

        progressBar.setVisibility(View.VISIBLE);
        String uid = mAuth.getCurrentUser().getUid();

        db.collection("users").document(uid).collection("notifications")
                .orderBy("timestamp", Query.Direction.DESCENDING)
                .get()
                .addOnSuccessListener(querySnapshot -> {
                    progressBar.setVisibility(View.GONE);
                    allNotificationsList.clear();

                    if (!querySnapshot.isEmpty()) {
                        for (DocumentSnapshot doc : querySnapshot.getDocuments()) {
                            NotificationItem item = doc.toObject(NotificationItem.class);
                            if (item != null) {
                                allNotificationsList.add(item);
                            }
                        }
                    }

                    renderNotifications();
                })
                .addOnFailureListener(e -> {
                    progressBar.setVisibility(View.GONE);
                    tvEmptyState.setText("Could not load notifications: " + e.getMessage());
                    tvEmptyState.setVisibility(View.VISIBLE);
                });
    }

    private void renderNotifications() {
        layoutNotificationList.removeAllViews();

        List<NotificationItem> filteredList = new ArrayList<>();
        for (NotificationItem item : allNotificationsList) {
            String category = item.getResolvedCategory();
            if (selectedCategoryFilter.equals("all") || selectedCategoryFilter.equalsIgnoreCase(category)) {
                filteredList.add(item);
            }
        }

        if (filteredList.isEmpty()) {
            tvEmptyState.setVisibility(View.VISIBLE);
            return;
        }

        tvEmptyState.setVisibility(View.GONE);

        if (selectedCategoryFilter.equals("all")) {
            // Group and separate into distinct categories
            renderCategorySection("☔ Rain Advisories", "rain", filteredList);
            renderCategorySection("🌡️ Heat Warnings", "heat", filteredList);
            renderCategorySection("🌿 Humidity Alerts", "humidity", filteredList);
            renderCategorySection("🔔 General & Crop Advisories", "general", filteredList);
        } else {
            // Direct rendering for single category
            for (NotificationItem item : filteredList) {
                addNotificationCard(item);
            }
        }
    }

    private void renderCategorySection(String sectionTitle, String categoryKey, List<NotificationItem> fullList) {
        List<NotificationItem> categoryItems = new ArrayList<>();
        for (NotificationItem item : fullList) {
            if (item.getResolvedCategory().equalsIgnoreCase(categoryKey)) {
                categoryItems.add(item);
            }
        }

        if (categoryItems.isEmpty()) return;

        // Section Header
        TextView tvHeader = new TextView(this);
        tvHeader.setText(sectionTitle + " (" + categoryItems.size() + ")");
        tvHeader.setTextSize(14f);
        tvHeader.setTypeface(null, android.graphics.Typeface.BOLD);
        tvHeader.setTextColor(Color.parseColor("#1C2A20"));

        LinearLayout.LayoutParams headerParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
        );
        headerParams.topMargin = dpToPx(12);
        headerParams.bottomMargin = dpToPx(8);
        tvHeader.setLayoutParams(headerParams);
        layoutNotificationList.addView(tvHeader);

        for (NotificationItem item : categoryItems) {
            addNotificationCard(item);
        }
    }

    private void addNotificationCard(NotificationItem item) {
        String category = item.getResolvedCategory();

        String badgeText = "🔔 General";
        String textColor = "#2E7D32";
        String bgColor = "#E8F5E9";

        if ("rain".equalsIgnoreCase(category)) {
            badgeText = "☔ Rain Advisory";
            textColor = "#1565C0";
            bgColor = "#E3F2FD";
        } else if ("heat".equalsIgnoreCase(category)) {
            badgeText = "🌡️ Heat Warning";
            textColor = "#E65100";
            bgColor = "#FFF3E0";
        } else if ("humidity".equalsIgnoreCase(category)) {
            badgeText = "🌿 Humidity Alert";
            textColor = "#00796B";
            bgColor = "#E0F2F1";
        }

        MaterialCardView card = new MaterialCardView(this);
        LinearLayout.LayoutParams cardParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
        );
        cardParams.bottomMargin = dpToPx(12);
        card.setLayoutParams(cardParams);
        card.setCardBackgroundColor(Color.parseColor("#FFFFFF"));
        card.setRadius(dpToPx(20));
        card.setCardElevation(0);
        card.setStrokeColor(Color.parseColor("#E2E7DC"));
        card.setStrokeWidth(dpToPx(1));

        LinearLayout contentLayout = new LinearLayout(this);
        contentLayout.setOrientation(LinearLayout.VERTICAL);
        contentLayout.setPadding(dpToPx(16), dpToPx(14), dpToPx(16), dpToPx(14));

        // Category Tag & Timestamp Row
        LinearLayout rowHeader = new LinearLayout(this);
        rowHeader.setOrientation(LinearLayout.HORIZONTAL);
        rowHeader.setGravity(Gravity.CENTER_VERTICAL);

        TextView tvBadge = new TextView(this);
        tvBadge.setText(badgeText);
        tvBadge.setTextSize(11f);
        tvBadge.setTypeface(null, android.graphics.Typeface.BOLD);
        tvBadge.setTextColor(Color.parseColor(textColor));
        android.graphics.drawable.GradientDrawable pill = new android.graphics.drawable.GradientDrawable();
        pill.setColor(Color.parseColor(bgColor));
        pill.setCornerRadius(dpToPx(50));
        tvBadge.setBackground(pill);
        tvBadge.setPadding(dpToPx(8), dpToPx(3), dpToPx(8), dpToPx(3));
        rowHeader.addView(tvBadge);

        TextView tvTime = new TextView(this);
        tvTime.setText(dateFormat.format(new Date(item.getTimestamp())));
        tvTime.setTextSize(11f);
        tvTime.setTextColor(Color.parseColor("#5C6B5F"));
        LinearLayout.LayoutParams timeParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
        );
        timeParams.leftMargin = dpToPx(8);
        tvTime.setLayoutParams(timeParams);
        rowHeader.addView(tvTime);

        contentLayout.addView(rowHeader);

        // Title
        TextView tvTitle = new TextView(this);
        tvTitle.setText(item.getTitle() != null ? item.getTitle() : "Notification");
        tvTitle.setTextSize(15f);
        tvTitle.setTextColor(Color.parseColor("#1C2A20"));
        tvTitle.setTypeface(null, android.graphics.Typeface.BOLD);
        LinearLayout.LayoutParams titleParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
        );
        titleParams.topMargin = dpToPx(8);
        tvTitle.setLayoutParams(titleParams);
        contentLayout.addView(tvTitle);

        // Message
        TextView tvMessage = new TextView(this);
        tvMessage.setText(item.getMessage() != null ? item.getMessage() : "");
        tvMessage.setTextSize(13f);
        tvMessage.setTextColor(Color.parseColor("#2A3A2E"));
        LinearLayout.LayoutParams msgParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
        );
        msgParams.topMargin = dpToPx(4);
        tvMessage.setLayoutParams(msgParams);
        contentLayout.addView(tvMessage);

        card.addView(contentLayout);
        layoutNotificationList.addView(card);
    }

    private int dpToPx(int dp) {
        float density = getResources().getDisplayMetrics().density;
        return Math.round(dp * density);
    }
}
