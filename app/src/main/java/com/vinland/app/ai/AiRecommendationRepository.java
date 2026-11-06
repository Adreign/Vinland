package com.vinland.app.ai;

import com.vinland.app.ai.model.AiError;
import com.vinland.app.ai.model.AiRecommendationResponse;

/**
 * Where the UI gets AI recommendations from. All methods are asynchronous and
 * call back on the main thread; none of them block.
 */
public interface AiRecommendationRepository {

    interface Callback<T> {
        void onSuccess(T value);

        void onError(AiError error);
    }

    /**
     * Reads the last saved recommendation from Firestore (works offline from the
     * local Firestore cache). Calls back with null when there is none yet.
     * Never calls the AI.
     */
    void getCached(Callback<AiRecommendationResponse> callback);

    /**
     * Asks the Cloud Function for recommendations. With forceRefresh=false the
     * server returns its cached answer when your garden data hasn't changed;
     * with forceRefresh=true it asks the AI again (counts toward the daily limit).
     */
    void refresh(boolean forceRefresh, Callback<AiRecommendationResponse> callback);
}
