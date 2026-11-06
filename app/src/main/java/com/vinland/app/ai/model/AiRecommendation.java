package com.vinland.app.ai.model;

import java.util.Map;

/** One AI recommendation, as returned by the getRecommendations Cloud Function. */
public class AiRecommendation {

    public final String title;
    public final String reason;
    /** "high", "medium" or "low". */
    public final String priority;
    public final String suggestedAction;
    /** "crop", "soil", "watering" or "general". */
    public final String category;
    /** Exact crop name from the crop database, or null. */
    public final String crop;

    public AiRecommendation(String title, String reason, String priority,
                            String suggestedAction, String category, String crop) {
        this.title = title;
        this.reason = reason;
        this.priority = priority;
        this.suggestedAction = suggestedAction;
        this.category = category;
        this.crop = crop;
    }

    static AiRecommendation fromMap(Map<?, ?> m) {
        return new AiRecommendation(
                Maps.string(m, "title", ""),
                Maps.string(m, "reason", ""),
                Maps.string(m, "priority", "medium"),
                Maps.string(m, "suggested_action", ""),
                Maps.string(m, "category", "general"),
                Maps.string(m, "crop", null));
    }
}
