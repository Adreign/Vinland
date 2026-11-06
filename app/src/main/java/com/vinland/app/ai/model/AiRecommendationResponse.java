package com.vinland.app.ai.model;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

/**
 * The full answer from the getRecommendations Cloud Function. The same shape is
 * stored in Firestore at users/{uid}/ai_recommendations/latest (the cache).
 */
public class AiRecommendationResponse {

    public static final String STATUS_OK = "ok";
    public static final String STATUS_INSUFFICIENT = "insufficient_data";

    public final String status;
    /** True when this came from the cache instead of a fresh AI call. */
    public final boolean cached;
    /** Epoch millis when the AI produced this. */
    public final long generatedAt;
    public final String model;
    /** "optimal", "fair", "action_needed" or "unknown". */
    public final String soilStatus;
    public final String summary;
    public final List<AiRecommendation> recommendations;
    public final List<String> missingData;
    public final String disclaimer;
    /** AI refreshes left today, or -1 when unknown (e.g. read from the local cache). */
    public final int remainingToday;

    public AiRecommendationResponse(String status, boolean cached, long generatedAt, String model,
                                    String soilStatus, String summary,
                                    List<AiRecommendation> recommendations, List<String> missingData,
                                    String disclaimer, int remainingToday) {
        this.status = status;
        this.cached = cached;
        this.generatedAt = generatedAt;
        this.model = model;
        this.soilStatus = soilStatus;
        this.summary = summary;
        this.recommendations = Collections.unmodifiableList(recommendations);
        this.missingData = Collections.unmodifiableList(missingData);
        this.disclaimer = disclaimer;
        this.remainingToday = remainingToday;
    }

    public boolean isInsufficientData() {
        return STATUS_INSUFFICIENT.equals(status);
    }

    /**
     * Parses the function result or the Firestore cache document.
     *
     * @param forceCached mark the result as cached (used when reading the Firestore cache)
     * @throws IllegalArgumentException when required fields are missing
     */
    public static AiRecommendationResponse fromMap(Map<?, ?> m, boolean forceCached) {
        if (m == null) throw new IllegalArgumentException("empty response");
        String status = Maps.string(m, "status", null);
        long generatedAt = Maps.number(m, "generatedAt", 0);
        if (status == null || generatedAt <= 0) throw new IllegalArgumentException("missing status");

        List<AiRecommendation> recs = new ArrayList<>();
        for (Object o : Maps.list(m, "recommendations")) {
            if (o instanceof Map) {
                AiRecommendation r = AiRecommendation.fromMap((Map<?, ?>) o);
                if (!r.title.isEmpty()) recs.add(r);
            }
        }

        Map<?, ?> quota = Maps.map(m, "quota");
        return new AiRecommendationResponse(
                status,
                forceCached || Maps.bool(m, "cached", false),
                generatedAt,
                Maps.string(m, "model", ""),
                Maps.string(m, "soilStatus", "unknown"),
                Maps.string(m, "summary", ""),
                recs,
                Maps.strings(m, "missingData"),
                Maps.string(m, "disclaimer",
                        "AI-generated suggestions. Not professional agronomic advice."),
                (int) Maps.number(quota, "remainingToday", -1));
    }
}
