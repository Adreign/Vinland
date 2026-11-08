package com.vinland.app.ai.direct;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * Checks Claude's JSON before it reaches the screen:
 * drops items naming a crop that is not in the database, drops empty and duplicate items,
 * shortens long text, keeps at most 5, and sorts by priority.
 */
public final class RecommendationValidator {

    private RecommendationValidator() {
    }

    static final int MAX_RECOMMENDATIONS = 5;
    private static final List<String> PRIORITIES = Arrays.asList("high", "medium", "low");
    private static final List<String> CATEGORIES = Arrays.asList("crop", "soil", "watering", "general");
    private static final List<String> SOIL_STATUSES = Arrays.asList("optimal", "fair", "action_needed", "unknown");

    /** The cleaned answer, or null when nothing usable is left (the caller retries once). */
    public static class Result {
        public final String status;
        public final String soilStatus;
        public final String summary;
        /** Maps with keys title, reason, priority, suggested_action, category, crop. */
        public final List<Map<String, Object>> recommendations;
        public final List<String> missingData;

        Result(String status, String soilStatus, String summary,
               List<Map<String, Object>> recommendations, List<String> missingData) {
            this.status = status;
            this.soilStatus = soilStatus;
            this.summary = summary;
            this.recommendations = recommendations;
            this.missingData = missingData;
        }
    }

    /** Trims, collapses whitespace and cuts at a word boundary. */
    static String clip(String text, int max) {
        String clean = text.replaceAll("\\s+", " ").trim();
        if (clean.length() <= max) return clean;
        String cut = clean.substring(0, max - 1);
        int lastSpace = cut.lastIndexOf(' ');
        return (lastSpace > max * 0.6 ? cut.substring(0, lastSpace) : cut) + "…";
    }

    public static Result validate(String json, List<String> cropNames) {
        JSONObject out;
        try {
            out = new JSONObject(json);
        } catch (JSONException | NullPointerException e) {
            return null;
        }

        String status = out.optString("status");
        String soilStatus = out.optString("soil_status");
        if (!status.equals("ok") && !status.equals("insufficient_data")) return null;
        if (!SOIL_STATUSES.contains(soilStatus)) return null;

        Map<String, String> known = new HashMap<>();
        for (String n : cropNames) known.put(n.trim().toLowerCase(Locale.ROOT), n);

        List<Map<String, Object>> kept = new ArrayList<>();
        Set<String> seenTitles = new HashSet<>();
        JSONArray recs = out.optJSONArray("recommendations");
        for (int i = 0; recs != null && i < recs.length(); i++) {
            JSONObject rec = recs.optJSONObject(i);
            if (rec == null) continue;
            String title = clip(rec.optString("title"), 80);
            String reason = clip(rec.optString("reason"), 300);
            String action = clip(rec.optString("suggested_action"), 300);
            String priority = rec.optString("priority");
            String category = rec.optString("category");
            if (!PRIORITIES.contains(priority) || !CATEGORIES.contains(category)) continue;

            String crop = null;
            String rawCrop = rec.isNull("crop") ? "" : rec.optString("crop").trim();
            if (!rawCrop.isEmpty()) {
                crop = known.get(rawCrop.toLowerCase(Locale.ROOT));
                if (crop == null) continue; // unknown / invented crop
            }
            if (category.equals("crop") && crop == null) continue;
            if (title.isEmpty() || reason.isEmpty() || action.isEmpty()) continue;
            if (!seenTitles.add(title.toLowerCase(Locale.ROOT))) continue;

            Map<String, Object> m = new HashMap<>();
            m.put("title", title);
            m.put("reason", reason);
            m.put("priority", priority);
            m.put("suggested_action", action);
            m.put("category", category);
            m.put("crop", crop);
            kept.add(m);
        }
        kept.sort((a, b) -> PRIORITIES.indexOf((String) a.get("priority")) - PRIORITIES.indexOf((String) b.get("priority")));
        List<Map<String, Object>> recommendations =
                new ArrayList<>(kept.subList(0, Math.min(MAX_RECOMMENDATIONS, kept.size())));

        List<String> missing = new ArrayList<>();
        JSONArray rawMissing = out.optJSONArray("missing_data");
        for (int i = 0; rawMissing != null && i < rawMissing.length() && missing.size() < 5; i++) {
            String m = clip(rawMissing.optString(i), 160);
            if (!m.isEmpty()) missing.add(m);
        }
        String summary = clip(out.optString("summary"), 300);

        if (status.equals("ok") && recommendations.isEmpty()) return null;
        if (status.equals("insufficient_data") && recommendations.isEmpty() && missing.isEmpty()) return null;
        if (summary.isEmpty()) return null;
        return new Result(status, soilStatus, summary, recommendations, missing);
    }

    /** The JSON schema Claude's answer must follow (structured outputs). */
    static Map<String, Object> schema() {
        Map<String, Object> rec = object(
                new String[]{"title", "reason", "priority", "suggested_action", "category", "crop"},
                new Object[]{str(), str(), enumOf(PRIORITIES), str(), enumOf(CATEGORIES),
                        mapOf("type", Arrays.asList("string", "null"))});
        return object(
                new String[]{"status", "soil_status", "summary", "recommendations", "missing_data"},
                new Object[]{enumOf(Arrays.asList("ok", "insufficient_data")), enumOf(SOIL_STATUSES), str(),
                        mapOf("type", "array", "items", rec), mapOf("type", "array", "items", str())});
    }

    private static Map<String, Object> object(String[] keys, Object[] types) {
        Map<String, Object> props = new HashMap<>();
        for (int i = 0; i < keys.length; i++) props.put(keys[i], types[i]);
        Map<String, Object> m = mapOf("type", "object", "properties", props, "required", Arrays.asList(keys));
        m.put("additionalProperties", false);
        return m;
    }

    private static Map<String, Object> str() {
        return mapOf("type", "string");
    }

    private static Map<String, Object> enumOf(List<String> values) {
        return mapOf("type", "string", "enum", values);
    }

    private static Map<String, Object> mapOf(Object... kv) {
        Map<String, Object> m = new HashMap<>();
        for (int i = 0; i < kv.length; i += 2) m.put((String) kv[i], kv[i + 1]);
        return m;
    }
}
