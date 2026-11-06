package com.vinland.app.ai;

import com.vinland.app.ai.model.AiError;
import com.vinland.app.ai.model.AiRecommendationResponse;

/** Everything the AI recommendations screen needs to draw itself. Immutable. */
public final class AiUiState {

    public enum Status {
        /** Nothing to show yet, first load in progress. */
        LOADING,
        /** Recommendations to show. */
        CONTENT,
        /** The AI answered but had nothing to recommend. */
        EMPTY,
        /** Not enough garden data; {@link #data} explains what is missing. */
        INSUFFICIENT_DATA,
        /** Failed and nothing cached to fall back on. */
        ERROR
    }

    public final Status status;
    /** The latest result (possibly cached). Null only for LOADING / ERROR. */
    public final AiRecommendationResponse data;
    /** The last error, shown as a banner when {@link #data} is still visible. */
    public final AiError error;
    /** A refresh is in progress while older data is shown. */
    public final boolean refreshing;

    private AiUiState(Status status, AiRecommendationResponse data, AiError error, boolean refreshing) {
        this.status = status;
        this.data = data;
        this.error = error;
        this.refreshing = refreshing;
    }

    public static AiUiState loading() {
        return new AiUiState(Status.LOADING, null, null, true);
    }

    public static AiUiState error(AiError error) {
        return new AiUiState(Status.ERROR, null, error, false);
    }

    /** Status follows from the data: content, empty or insufficient. */
    public static AiUiState of(AiRecommendationResponse data, AiError error, boolean refreshing) {
        Status status;
        if (data.isInsufficientData() && data.recommendations.isEmpty()) {
            status = Status.INSUFFICIENT_DATA;
        } else if (data.recommendations.isEmpty()) {
            status = Status.EMPTY;
        } else {
            status = Status.CONTENT;
        }
        return new AiUiState(status, data, error, refreshing);
    }
}
