package com.vinland.app.ai;

import android.os.Bundle;
import android.text.format.DateUtils;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;
import androidx.lifecycle.ViewModelProvider;

import com.google.android.material.button.MaterialButton;
import com.google.android.material.card.MaterialCardView;
import com.vinland.app.R;
import com.vinland.app.ai.model.AiError;
import com.vinland.app.ai.model.AiRecommendationResponse;

/** Shows AI recommendations: cached result instantly, refresh button, last-updated time, AI note. */
public class AiRecommendationActivity extends AppCompatActivity {

    private TextView tvLastUpdated, btnRefresh, tvDisclaimer, tvBanner, tvSoilStatus, tvSummary;
    private TextView tvMessageIcon, tvMessageTitle, tvMessageBody, tvMissing, tvQuota, tvRecsHeader;
    private ProgressBar progressRefresh;
    private LinearLayout layoutLoading, layoutContent, layoutRecommendations;
    private MaterialCardView cardMessage, cardMissing;
    private MaterialButton btnRetry;

    private AiRecommendationViewModel viewModel;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_ai_recommendation);

        tvLastUpdated = findViewById(R.id.tvLastUpdated);
        btnRefresh = findViewById(R.id.btnRefresh);
        progressRefresh = findViewById(R.id.progressRefresh);
        tvDisclaimer = findViewById(R.id.tvDisclaimer);
        tvBanner = findViewById(R.id.tvBanner);
        layoutLoading = findViewById(R.id.layoutLoading);
        cardMessage = findViewById(R.id.cardMessage);
        tvMessageIcon = findViewById(R.id.tvMessageIcon);
        tvMessageTitle = findViewById(R.id.tvMessageTitle);
        tvMessageBody = findViewById(R.id.tvMessageBody);
        btnRetry = findViewById(R.id.btnRetry);
        layoutContent = findViewById(R.id.layoutContent);
        tvSoilStatus = findViewById(R.id.tvSoilStatus);
        tvSummary = findViewById(R.id.tvSummary);
        tvRecsHeader = findViewById(R.id.tvRecsHeader);
        layoutRecommendations = findViewById(R.id.layoutRecommendations);
        cardMissing = findViewById(R.id.cardMissing);
        tvMissing = findViewById(R.id.tvMissing);
        tvQuota = findViewById(R.id.tvQuota);

        findViewById(R.id.tvBack).setOnClickListener(v -> finish());
        findViewById(R.id.btnSavedRecommendations).setOnClickListener(v ->
                startActivity(new android.content.Intent(this, SavedRecommendationsActivity.class)));

        viewModel = new ViewModelProvider(this,
                new AiRecommendationViewModel.Factory(new com.vinland.app.ai.direct.DirectClaudeRepository(this)))
                .get(AiRecommendationViewModel.class);

        btnRefresh.setOnClickListener(v -> viewModel.refresh());
        btnRetry.setOnClickListener(v -> viewModel.retry());
        viewModel.getState().observe(this, this::render);
        viewModel.start();
    }

    private void render(AiUiState state) {
        btnRefresh.setVisibility(state.refreshing ? View.INVISIBLE : View.VISIBLE);
        progressRefresh.setVisibility(state.refreshing ? View.VISIBLE : View.GONE);
        btnRefresh.setEnabled(!state.refreshing);

        layoutLoading.setVisibility(state.status == AiUiState.Status.LOADING ? View.VISIBLE : View.GONE);
        cardMessage.setVisibility(View.GONE);
        layoutContent.setVisibility(View.GONE);
        tvBanner.setVisibility(View.GONE);
        tvQuota.setVisibility(View.GONE);

        AiRecommendationResponse data = state.data;
        if (data != null) {
            tvDisclaimer.setText("🤖 " + data.disclaimer);
            tvLastUpdated.setText("Updated " + DateUtils.getRelativeTimeSpanString(
                    data.generatedAt, System.currentTimeMillis(), DateUtils.MINUTE_IN_MILLIS)
                    + (data.cached ? " · saved" : ""));
            if (data.remainingToday >= 0) {
                tvQuota.setText("AI refreshes left today: " + data.remainingToday);
                tvQuota.setVisibility(View.VISIBLE);
            }
        } else {
            tvLastUpdated.setText(state.status == AiUiState.Status.LOADING ? "Loading…" : "");
        }

        if (state.error != null && data != null) {
            tvBanner.setText(bannerIcon(state.error) + " " + state.error.message);
            tvBanner.setVisibility(View.VISIBLE);
        }

        switch (state.status) {
            case LOADING:
                break;
            case ERROR:
                showMessage(errorIcon(state.error), errorTitle(state.error), state.error.message, true);
                break;
            case INSUFFICIENT_DATA:
                showMessage("🌱", "Not enough garden data yet", AiRecommendationViews.bulletList(data.missingData.isEmpty()
                        ? java.util.Collections.singletonList(data.summary) : data.missingData), true);
                btnRetry.setText("Check again");
                break;
            case EMPTY:
                showMessage("👍", "Nothing to change right now", data.summary, true);
                btnRetry.setText("Check again");
                break;
            case CONTENT:
                showContent(data);
                break;
        }
    }

    private void showMessage(String icon, String title, String body, boolean showRetry) {
        cardMessage.setVisibility(View.VISIBLE);
        tvMessageIcon.setText(icon);
        tvMessageTitle.setText(title);
        tvMessageBody.setText(body);
        btnRetry.setText("Try again");
        btnRetry.setVisibility(showRetry ? View.VISIBLE : View.GONE);
    }

    private void showContent(AiRecommendationResponse data) {
        layoutContent.setVisibility(View.VISIBLE);
        tvSummary.setText(data.summary);
        tvSoilStatus.setText(AiRecommendationViews.soilLabel(data.soilStatus));
        AiRecommendationViews.tint(tvSoilStatus, AiRecommendationViews.soilColor(data.soilStatus));

        layoutRecommendations.removeAllViews();
        AiRecommendationViews.addRecommendationCards(layoutRecommendations, data.recommendations);

        if (!data.missingData.isEmpty()) {
            tvMissing.setText(AiRecommendationViews.bulletList(data.missingData));
            cardMissing.setVisibility(View.VISIBLE);
        } else {
            cardMissing.setVisibility(View.GONE);
        }
    }

    // ------------------------------------------------------------- labels

    private static String bannerIcon(AiError e) {
        return e.type == AiError.Type.OFFLINE ? "📶" : e.isRateLimited() ? "⏳" : "⚠️";
    }

    private static String errorIcon(AiError e) {
        if (e.type == AiError.Type.OFFLINE) return "📶";
        if (e.isRateLimited()) return "⏳";
        if (e.type == AiError.Type.BUSY) return "🕐";
        return "⚠️";
    }

    private static String errorTitle(AiError e) {
        switch (e.type) {
            case OFFLINE:
                return "You're offline";
            case COOLDOWN:
            case DAILY_LIMIT:
                return "Limit reached";
            case BUSY:
                return "AI is busy";
            case NOT_SIGNED_IN:
                return "Please sign in";
            default:
                return "Couldn't load recommendations";
        }
    }
}
