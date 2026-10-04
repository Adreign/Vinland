package com.vinland.app.notifications;

import android.graphics.Color;
import android.os.Bundle;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;

import com.google.android.material.card.MaterialCardView;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.Query;
import com.vinland.app.R;
import com.vinland.app.data.NotificationItem;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

public class NotificationHistoryActivity extends AppCompatActivity {

    private LinearLayout layoutNotificationList;
    private TextView tvEmptyState;
    private ProgressBar progressBar;

    private FirebaseAuth mAuth;
    private FirebaseFirestore db;

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

        tvBack.setOnClickListener(v -> finish());

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
                    layoutNotificationList.removeAllViews();

                    if (querySnapshot.isEmpty()) {
                        tvEmptyState.setVisibility(View.VISIBLE);
                        return;
                    }

                    tvEmptyState.setVisibility(View.GONE);
                    for (DocumentSnapshot doc : querySnapshot.getDocuments()) {
                        NotificationItem item = doc.toObject(NotificationItem.class);
                        if (item != null) {
                            addNotificationCard(item);
                        }
                    }
                })
                .addOnFailureListener(e -> {
                    progressBar.setVisibility(View.GONE);
                    tvEmptyState.setText("Could not load notifications: " + e.getMessage());
                    tvEmptyState.setVisibility(View.VISIBLE);
                });
    }

    private void addNotificationCard(NotificationItem item) {
        MaterialCardView card = new MaterialCardView(this);
        LinearLayout.LayoutParams cardParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
        );
        cardParams.bottomMargin = dpToPx(12);
        card.setLayoutParams(cardParams);
        card.setCardBackgroundColor(Color.parseColor("#FFFFFF"));
        card.setRadius(dpToPx(16));
        card.setCardElevation(2f);
        card.setStrokeWidth(0);

        LinearLayout contentLayout = new LinearLayout(this);
        contentLayout.setOrientation(LinearLayout.VERTICAL);
        contentLayout.setPadding(dpToPx(16), dpToPx(16), dpToPx(16), dpToPx(16));

        TextView tvTitle = new TextView(this);
        tvTitle.setText(item.getTitle() != null ? item.getTitle() : "Notification");
        tvTitle.setTextSize(15f);
        tvTitle.setTextColor(Color.parseColor("#212121"));
        tvTitle.setTypeface(null, android.graphics.Typeface.BOLD);

        TextView tvTime = new TextView(this);
        tvTime.setText(dateFormat.format(new Date(item.getTimestamp())));
        tvTime.setTextSize(11f);
        tvTime.setTextColor(Color.parseColor("#757575"));
        LinearLayout.LayoutParams timeParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
        );
        timeParams.topMargin = dpToPx(2);
        tvTime.setLayoutParams(timeParams);

        TextView tvMessage = new TextView(this);
        tvMessage.setText(item.getMessage() != null ? item.getMessage() : "");
        tvMessage.setTextSize(13);
        tvMessage.setTextColor(Color.parseColor("#424242"));
        LinearLayout.LayoutParams msgParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
        );
        msgParams.topMargin = dpToPx(8);
        tvMessage.setLayoutParams(msgParams);

        contentLayout.addView(tvTitle);
        contentLayout.addView(tvTime);
        contentLayout.addView(tvMessage);

        card.addView(contentLayout);
        layoutNotificationList.addView(card);
    }

    private int dpToPx(int dp) {
        float density = getResources().getDisplayMetrics().density;
        return Math.round(dp * density);
    }
}
