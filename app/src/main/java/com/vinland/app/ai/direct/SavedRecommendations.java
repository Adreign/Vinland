package com.vinland.app.ai.direct;

import android.content.Context;
import android.content.SharedPreferences;

import com.vinland.app.ai.model.AiRecommendationResponse;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;

/**
 * History of AI answers, kept on the phone per signed-in user (newest first).
 * Every new answer from Claude is added automatically; the user can delete entries.
 */
public class SavedRecommendations {

    /** Oldest entries are dropped past this many. */
    static final int MAX_SAVED = 50;
    private static final Object LOCK = new Object();

    private final SharedPreferences prefs;

    public SavedRecommendations(Context context) {
        this.prefs = context.getApplicationContext()
                .getSharedPreferences("ai_recommendations", Context.MODE_PRIVATE);
    }

    private static String key(String uid) {
        return "saved_" + uid;
    }

    public void add(String uid, JSONObject doc) {
        synchronized (LOCK) {
            prefs.edit().putString(key(uid), addTo(prefs.getString(key(uid), null), doc, MAX_SAVED)).apply();
        }
    }

    public List<AiRecommendationResponse> list(String uid) {
        synchronized (LOCK) {
            return parse(prefs.getString(key(uid), null));
        }
    }

    /** Removes the entry made at {@code generatedAt}. */
    public void delete(String uid, long generatedAt) {
        synchronized (LOCK) {
            prefs.edit().putString(key(uid), removeFrom(prefs.getString(key(uid), null), generatedAt)).apply();
        }
    }

    public void clear(String uid) {
        synchronized (LOCK) {
            prefs.edit().remove(key(uid)).apply();
        }
    }

    // ------------------------------------------------- pure JSON helpers (unit tested)

    private static JSONArray read(String json) {
        if (json == null) return new JSONArray();
        try {
            return new JSONArray(json);
        } catch (JSONException e) {
            return new JSONArray(); // damaged history: start over
        }
    }

    /** Puts {@code doc} first, replaces an entry with the same time, and keeps at most {@code max}. */
    static String addTo(String json, JSONObject doc, int max) {
        long time = doc.optLong("generatedAt");
        JSONArray old = read(json);
        JSONArray out = new JSONArray();
        out.put(doc);
        for (int i = 0; i < old.length() && out.length() < max; i++) {
            JSONObject o = old.optJSONObject(i);
            if (o != null && o.optLong("generatedAt") != time) out.put(o);
        }
        return out.toString();
    }

    static String removeFrom(String json, long generatedAt) {
        JSONArray old = read(json);
        JSONArray out = new JSONArray();
        for (int i = 0; i < old.length(); i++) {
            JSONObject o = old.optJSONObject(i);
            if (o != null && o.optLong("generatedAt") != generatedAt) out.put(o);
        }
        return out.toString();
    }

    /** Skips entries that can't be read instead of failing the whole list. */
    static List<AiRecommendationResponse> parse(String json) {
        JSONArray arr = read(json);
        List<AiRecommendationResponse> out = new ArrayList<>();
        for (int i = 0; i < arr.length(); i++) {
            JSONObject o = arr.optJSONObject(i);
            if (o == null) continue;
            try {
                out.add(AiRecommendationResponse.fromMap(JsonMaps.toMap(o), true));
            } catch (IllegalArgumentException | JSONException ignored) {
                // skip a damaged entry
            }
        }
        return out;
    }
}
