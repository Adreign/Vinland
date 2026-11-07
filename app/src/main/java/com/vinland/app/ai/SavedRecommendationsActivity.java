package com.vinland.app.ai;

import android.content.Intent;
import android.os.Bundle;
import android.text.format.DateUtils;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.vinland.app.R;
import com.vinland.app.ai.direct.SavedRecommendations;
import com.vinland.app.ai.model.AiRecommendationResponse;

import java.util.Collections;
import java.util.List;

/** Lists every AI answer saved on this phone. Tap one to see its full advice; delete one or all. */
public class SavedRecommendationsActivity extends AppCompatActivity {

    private SavedRecommendations store;
    private String uid;
    private TextView tvCount;
    private View cardEmpty, cardClearAll;
    private LinearLayout layoutSaved;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_saved_recommendations);

        store = new SavedRecommendations(this);
        FirebaseUser user = FirebaseAuth.getInstance().getCurrentUser();
        uid = user != null ? user.getUid() : null;

        tvCount = findViewById(R.id.tvCount);
        cardEmpty = findViewById(R.id.cardEmpty);
        cardClearAll = findViewById(R.id.cardClearAll);
        layoutSaved = findViewById(R.id.layoutSaved);

        findViewById(R.id.tvBack).setOnClickListener(v -> finish());
        findViewById(R.id.btnClearAll).setOnClickListener(v -> confirmClearAll());
        findViewById(R.id.btnGetRecommendations).setOnClickListener(v -> {
            startActivity(new Intent(this, AiRecommendationActivity.class));
            finish();
        });
    }

    @Override
    protected void onResume() {
        super.onResume();
        render(); // picks up answers saved while this screen was in the background
    }

    private void render() {
        List<AiRecommendationResponse> items =
                uid != null ? store.list(uid) : Collections.<AiRecommendationResponse>emptyList();

        tvCount.setText(items.size() == 1 ? "1 saved" : items.size() + " saved");
        cardEmpty.setVisibility(items.isEmpty() ? View.VISIBLE : View.GONE);
        cardClearAll.setVisibility(items.isEmpty() ? View.GONE : View.VISIBLE);

        layoutSaved.removeAllViews();
        LayoutInflater inflater = LayoutInflater.from(this);
        for (AiRecommendationResponse item : items) {
            layoutSaved.addView(buildItem(inflater, item));
        }
    }

    private View buildItem(LayoutInflater inflater, AiRecommendationResponse data) {
        View card = inflater.inflate(R.layout.item_saved_recommendation, layoutSaved, false);

        ((TextView) card.findViewById(R.id.tvDate)).setText(DateUtils.formatDateTime(this, data.generatedAt,
                DateUtils.FORMAT_SHOW_DATE | DateUtils.FORMAT_SHOW_TIME | DateUtils.FORMAT_ABBREV_MONTH));
        TextView tvSoil = card.findViewById(R.id.tvSoilStatus);
        tvSoil.setText(AiRecommendationViews.soilLabel(data.soilStatus));
        AiRecommendationViews.tint(tvSoil, AiRecommendationViews.soilColor(data.soilStatus));
        ((TextView) card.findViewById(R.id.tvSummary)).setText(data.summary);

        int count = data.recommendations.size();
        String closedLabel = (count == 1 ? "1 recommendation" : count + " recommendations") + " · Show ▾";
        TextView tvToggle = card.findViewById(R.id.tvToggle);
        tvToggle.setText(closedLabel);

        LinearLayout details = card.findViewById(R.id.layoutDetails);
        LinearLayout recs = card.findViewById(R.id.layoutRecs);
        TextView tvMissing = card.findViewById(R.id.tvMissing);

        card.findViewById(R.id.layoutHeader).setOnClickListener(v -> {
            boolean open = details.getVisibility() == View.VISIBLE;
            if (!open && recs.getChildCount() == 0) {
                // Build the full cards only the first time they're opened.
                AiRecommendationViews.addRecommendationCards(recs, data.recommendations);
                if (!data.missingData.isEmpty()) {
                    tvMissing.setText("📋 To improve these suggestions:\n"
                            + AiRecommendationViews.bulletList(data.missingData));
                    tvMissing.setVisibility(View.VISIBLE);
                }
            }
            details.setVisibility(open ? View.GONE : View.VISIBLE);
            tvToggle.setText(open ? closedLabel : "Hide ▴");
        });

        card.findViewById(R.id.btnDelete).setOnClickListener(v -> new AlertDialog.Builder(this)
                .setTitle("Delete this recommendation?")
                .setMessage("It will be removed from your saved list.")
                .setNegativeButton("Cancel", null)
                .setPositiveButton("Delete", (d, w) -> {
                    store.delete(uid, data.generatedAt);
                    render();
                })
                .show());
        return card;
    }

    private void confirmClearAll() {
        if (uid == null) return;
        new AlertDialog.Builder(this)
                .setTitle("Delete all saved recommendations?")
                .setMessage("This can't be undone.")
                .setNegativeButton("Cancel", null)
                .setPositiveButton("Delete all", (d, w) -> {
                    store.clear(uid);
                    render();
                })
                .show();
    }
}
