package com.vinland.app.notifications;

import android.graphics.Color;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;

import com.google.android.material.button.MaterialButton;
import com.google.android.material.card.MaterialCardView;
import com.google.android.material.chip.ChipGroup;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.snackbar.Snackbar;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.firestore.CollectionReference;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.Query;
import com.google.firebase.firestore.WriteBatch;
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
    private MaterialButton btnClearAll;

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
        btnClearAll = findViewById(R.id.btnClearAll);

        tvBack.setOnClickListener(v -> finish());
        btnClearAll.setOnClickListener(v -> confirmClearAll());

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
                                // The Firestore document ID is what we delete by.
                                item.setId(doc.getId());
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

    /** Notifications that match the selected category chip. */
    private List<NotificationItem> visibleNotifications() {
        List<NotificationItem> filteredList = new ArrayList<>();
        for (NotificationItem item : allNotificationsList) {
            String category = item.getResolvedCategory();
            if (selectedCategoryFilter.equals("all") || selectedCategoryFilter.equalsIgnoreCase(category)) {
                filteredList.add(item);
            }
        }
        return filteredList;
    }

    private CollectionReference notificationsRef() {
        return db.collection("users").document(mAuth.getCurrentUser().getUid())
                .collection("notifications");
    }

    /** Deletes one notification. "Undo" on the message at the bottom puts it back. */
    private void deleteNotification(NotificationItem item) {
        if (mAuth.getCurrentUser() == null || item.getId() == null) return;

        int position = allNotificationsList.indexOf(item);
        allNotificationsList.remove(item);
        renderNotifications();

        notificationsRef().document(item.getId()).delete()
                .addOnSuccessListener(unused -> {
                    Snackbar.make(layoutNotificationList, "Notification deleted", Snackbar.LENGTH_LONG)
                            .setAction("Undo", v -> restoreNotification(item, position))
                            .show();
                })
                .addOnFailureListener(e -> {
                    // Could not delete: show it again.
                    allNotificationsList.add(Math.min(position, allNotificationsList.size()), item);
                    renderNotifications();
                    Snackbar.make(layoutNotificationList, "Could not delete: " + e.getMessage(),
                            Snackbar.LENGTH_LONG).show();
                });
    }

    private void restoreNotification(NotificationItem item, int position) {
        // Same fields NotificationHelper saves.
        java.util.Map<String, Object> data = new java.util.HashMap<>();
        data.put("id", item.getId());
        data.put("title", item.getTitle());
        data.put("message", item.getMessage());
        data.put("category", item.getCategory());
        data.put("timestamp", item.getTimestamp());

        notificationsRef().document(item.getId()).set(data)
                .addOnSuccessListener(unused -> {
                    allNotificationsList.add(Math.min(position, allNotificationsList.size()), item);
                    renderNotifications();
                })
                .addOnFailureListener(e -> Snackbar.make(layoutNotificationList,
                        "Could not restore: " + e.getMessage(), Snackbar.LENGTH_LONG).show());
    }

    /** Asks first, then deletes every notification shown under the current filter. */
    private void confirmClearAll() {
        List<NotificationItem> toDelete = visibleNotifications();
        if (toDelete.isEmpty()) return;

        String what = selectedCategoryFilter.equals("all")
                ? "all " + toDelete.size() + " notifications"
                : "the " + toDelete.size() + " notifications in this category";

        new MaterialAlertDialogBuilder(this)
                .setTitle("Delete notifications?")
                .setMessage("This will permanently delete " + what + ".")
                .setNegativeButton("Cancel", null)
                .setPositiveButton("Delete", (dialog, which) -> clearAll(toDelete))
                .show();
    }

    private void clearAll(List<NotificationItem> toDelete) {
        if (mAuth.getCurrentUser() == null) return;

        progressBar.setVisibility(View.VISIBLE);
        btnClearAll.setEnabled(false);

        // Firestore allows up to 500 changes per batch, so split bigger lists.
        List<com.google.android.gms.tasks.Task<Void>> commits = new ArrayList<>();
        CollectionReference ref = notificationsRef();
        for (int start = 0; start < toDelete.size(); start += 500) {
            WriteBatch batch = db.batch();
            for (NotificationItem item : toDelete.subList(start, Math.min(start + 500, toDelete.size()))) {
                if (item.getId() != null) batch.delete(ref.document(item.getId()));
            }
            commits.add(batch.commit());
        }

        com.google.android.gms.tasks.Tasks.whenAll(commits)
                .addOnSuccessListener(unused -> {
                    progressBar.setVisibility(View.GONE);
                    btnClearAll.setEnabled(true);
                    allNotificationsList.removeAll(toDelete);
                    renderNotifications();
                    Snackbar.make(layoutNotificationList, "Notifications deleted", Snackbar.LENGTH_SHORT).show();
                })
                .addOnFailureListener(e -> {
                    progressBar.setVisibility(View.GONE);
                    btnClearAll.setEnabled(true);
                    Snackbar.make(layoutNotificationList, "Could not delete: " + e.getMessage(),
                            Snackbar.LENGTH_LONG).show();
                    loadNotifications(); // show what is really left
                });
    }

    private void renderNotifications() {
        layoutNotificationList.removeAllViews();

        List<NotificationItem> filteredList = visibleNotifications();

        if (filteredList.isEmpty()) {
            tvEmptyState.setVisibility(View.VISIBLE);
            btnClearAll.setVisibility(View.GONE);
            return;
        }

        tvEmptyState.setVisibility(View.GONE);
        btnClearAll.setVisibility(View.VISIBLE);

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

        // Pushes the delete button to the right edge.
        View spacer = new View(this);
        rowHeader.addView(spacer, new LinearLayout.LayoutParams(0, 1, 1f));

        TextView btnDelete = new TextView(this);
        btnDelete.setText("🗑");
        btnDelete.setTextSize(16f);
        btnDelete.setGravity(Gravity.CENTER);
        btnDelete.setContentDescription("Delete notification");
        android.util.TypedValue ripple = new android.util.TypedValue();
        getTheme().resolveAttribute(android.R.attr.selectableItemBackgroundBorderless, ripple, true);
        btnDelete.setBackgroundResource(ripple.resourceId);
        btnDelete.setOnClickListener(v -> deleteNotification(item));
        rowHeader.addView(btnDelete, new LinearLayout.LayoutParams(dpToPx(36), dpToPx(36)));

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
