package com.vinland.app.ai.direct;

import android.content.Context;
import android.content.SharedPreferences;
import android.net.ConnectivityManager;
import android.net.NetworkCapabilities;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import com.google.android.gms.tasks.Tasks;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.DocumentReference;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.Query;
import com.google.firebase.firestore.QueryDocumentSnapshot;
import com.google.firebase.firestore.QuerySnapshot;
import com.google.android.gms.tasks.Task;
import com.vinland.app.BuildConfig;
import com.vinland.app.ai.AiRecommendationRepository;
import com.vinland.app.ai.model.AiError;
import com.vinland.app.ai.model.AiRecommendationResponse;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

/**
 * AI recommendations without a server: the app reads the garden data from Firestore,
 * asks Claude directly, and keeps the last answer on the phone.
 *
 * Simple by design (fine for a capstone): the Claude key is built into the app, so
 * keep a low spending limit on your Anthropic account.
 */
public class DirectClaudeRepository implements AiRecommendationRepository {

    private static final String TAG = "DirectClaude";
    private static final String DISCLAIMER =
            "AI-generated suggestions based on your garden data. Not professional agronomic, medical, or legal advice.";

    /** Cost safety: minimum seconds between two AI calls, and max AI calls per day on this phone. */
    static final int COOLDOWN_SECONDS = 60;
    static final int DAILY_CAP = 10;
    /** An unchanged garden reuses the saved answer for this long. */
    static final long CACHE_MAX_AGE_MS = 24L * 60 * 60 * 1000;

    private static final ExecutorService EXECUTOR = Executors.newSingleThreadExecutor();
    private static final Handler MAIN = new Handler(Looper.getMainLooper());

    private final Context app;
    private final SharedPreferences prefs;
    private final FirebaseFirestore db;
    private final SavedRecommendations saved;

    public DirectClaudeRepository(Context context) {
        this.app = context.getApplicationContext();
        this.prefs = app.getSharedPreferences("ai_recommendations", Context.MODE_PRIVATE);
        this.db = FirebaseFirestore.getInstance();
        this.saved = new SavedRecommendations(app);
    }

    private static String currentUid() {
        FirebaseUser user = FirebaseAuth.getInstance().getCurrentUser();
        return user != null ? user.getUid() : null;
    }

    // ------------------------------------------------------------------ cache

    @Override
    public void getCached(Callback<AiRecommendationResponse> callback) {
        String uid = currentUid();
        AiRecommendationResponse cached = null;
        if (uid != null) {
            JSONObject doc = readCache(uid);
            if (doc != null) {
                try {
                    cached = AiRecommendationResponse.fromMap(JsonMaps.toMap(doc), true);
                } catch (IllegalArgumentException | JSONException e) {
                    cached = null; // a damaged cache is the same as no cache
                }
            }
        }
        callback.onSuccess(cached);
    }

    private JSONObject readCache(String uid) {
        String json = prefs.getString("cache_" + uid, null);
        if (json == null) return null;
        try {
            return new JSONObject(json);
        } catch (JSONException e) {
            return null;
        }
    }

    // ---------------------------------------------------------------- refresh

    @Override
    public void refresh(boolean forceRefresh, Callback<AiRecommendationResponse> callback) {
        String uid = currentUid();
        if (uid == null) {
            callback.onError(AiError.notSignedIn());
            return;
        }
        if (!isOnline()) {
            callback.onError(AiError.offline());
            return;
        }
        if (BuildConfig.ANTHROPIC_API_KEY.isEmpty()) {
            callback.onError(new AiError(AiError.Type.UNKNOWN,
                    "No Claude API key. Add ANTHROPIC_API_KEY to secrets.properties and rebuild the app.", 0));
            return;
        }
        EXECUTOR.execute(() -> {
            try {
                AiRecommendationResponse result = run(uid, forceRefresh);
                MAIN.post(() -> callback.onSuccess(result));
            } catch (ClaudeClient.ClaudeException e) {
                MAIN.post(() -> callback.onError(e.error));
            } catch (Exception e) {
                Log.e(TAG, "refresh failed", e);
                MAIN.post(() -> callback.onError(new AiError(AiError.Type.UNKNOWN,
                        "Something went wrong. Please try again.", 0)));
            }
        });
    }

    /** Runs on the background thread. */
    private AiRecommendationResponse run(String uid, boolean force) throws Exception {
        long now = System.currentTimeMillis();
        GardenPrompt.Built prompt = GardenPrompt.build(loadRaw(uid), now);
        String fingerprint = sha256(ClaudeClient.MODEL + prompt.cropBlock + prompt.userBlock);
        JSONObject cache = readCache(uid);

        // 1. Same garden data as last time and still fresh: reuse it, no AI call.
        if (!force && cache != null && fingerprint.equals(cache.optString("fingerprint"))
                && now - cache.optLong("generatedAt") < CACHE_MAX_AGE_MS) {
            return respond(cache, true, uid, now);
        }

        // 2. Nothing to base advice on: say so for free instead of asking the AI to guess.
        if (!prompt.hasReading || prompt.cropNames.isEmpty()) {
            JSONArray missing = new JSONArray();
            if (!prompt.hasReading) {
                missing.put("No soil reading yet. Connect your sensor or save a reading on the Soil Monitoring screen.");
            }
            if (prompt.cropNames.isEmpty()) missing.put("The crop database is empty.");
            JSONObject doc = new JSONObject()
                    .put("status", "insufficient_data")
                    .put("generatedAt", now)
                    .put("model", "none")
                    .put("soilStatus", "unknown")
                    .put("summary", "There isn't enough garden data yet to give grounded recommendations.")
                    .put("recommendations", new JSONArray())
                    .put("missingData", missing)
                    .put("disclaimer", DISCLAIMER)
                    .put("fingerprint", fingerprint);
            saveCache(uid, doc);
            return respond(doc, false, uid, now);
        }

        // 3. Paid call: check the local limits first.
        AiError limited = checkLimits(uid, now);
        if (limited != null) {
            if (cache != null && !force) return respond(cache, true, uid, now);
            throw new ClaudeClient.ClaudeException(limited);
        }
        recordCall(uid, now);

        ClaudeClient claude = new ClaudeClient(BuildConfig.ANTHROPIC_API_KEY);
        for (int attempt = 1; attempt <= 2; attempt++) { // one retry on an unusable answer
            String json = claude.generate(prompt.cropBlock, prompt.userBlock);
            RecommendationValidator.Result v = RecommendationValidator.validate(json, prompt.cropNames);
            if (v == null) {
                Log.w(TAG, "unusable AI answer, attempt " + attempt);
                continue;
            }
            JSONArray recs = new JSONArray();
            for (Map<String, Object> r : v.recommendations) {
                JSONObject o = new JSONObject();
                for (Map.Entry<String, Object> e : r.entrySet()) {
                    o.put(e.getKey(), e.getValue() != null ? e.getValue() : JSONObject.NULL);
                }
                recs.put(o);
            }
            JSONObject doc = new JSONObject()
                    .put("status", v.status)
                    .put("generatedAt", now)
                    .put("model", ClaudeClient.MODEL)
                    .put("soilStatus", v.soilStatus)
                    .put("summary", v.summary)
                    .put("recommendations", recs)
                    .put("missingData", new JSONArray(v.missingData))
                    .put("disclaimer", DISCLAIMER)
                    .put("fingerprint", fingerprint);
            saveCache(uid, doc);
            saved.add(uid, doc); // keep it in the Saved Recommendations history
            return respond(doc, false, uid, now);
        }
        throw new ClaudeClient.ClaudeException(AiError.invalidResponse());
    }

    private AiRecommendationResponse respond(JSONObject doc, boolean cached, String uid, long now)
            throws JSONException {
        Map<String, Object> m = JsonMaps.toMap(doc);
        m.put("cached", cached);
        Map<String, Object> quota = new HashMap<>();
        quota.put("remainingToday", remainingToday(uid, now));
        m.put("quota", quota);
        return AiRecommendationResponse.fromMap(m, false);
    }

    private void saveCache(String uid, JSONObject doc) {
        prefs.edit().putString("cache_" + uid, doc.toString()).apply();
    }

    // ----------------------------------------------------------------- limits

    private AiError checkLimits(String uid, long now) {
        long last = prefs.getLong("last_call_" + uid, 0);
        long wait = COOLDOWN_SECONDS - (now - last) / 1000;
        if (wait > 0) {
            return new AiError(AiError.Type.COOLDOWN,
                    "Please wait " + wait + " seconds before refreshing again.", (int) wait);
        }
        if (remainingToday(uid, now) <= 0) {
            return new AiError(AiError.Type.DAILY_LIMIT,
                    "You've used today's AI refreshes. Your last recommendations are still shown. Try again tomorrow.", 0);
        }
        return null;
    }

    private int remainingToday(String uid, long now) {
        String today = GardenPrompt.manilaDate(now);
        int used = today.equals(prefs.getString("day_" + uid, "")) ? prefs.getInt("count_" + uid, 0) : 0;
        return Math.max(0, DAILY_CAP - used);
    }

    private void recordCall(String uid, long now) {
        String today = GardenPrompt.manilaDate(now);
        int used = DAILY_CAP - remainingToday(uid, now);
        prefs.edit()
                .putLong("last_call_" + uid, now)
                .putString("day_" + uid, today)
                .putInt("count_" + uid, used + 1)
                .apply();
    }

    // ------------------------------------------------------------- Firestore

    /** Reads everything the prompt needs. A collection that fails to load is treated as empty. */
    private GardenPrompt.RawData loadRaw(String uid) {
        DocumentReference userRef = db.collection("users").document(uid);
        GardenPrompt.RawData raw = new GardenPrompt.RawData();
        raw.user = getDoc(userRef.get());
        raw.soilLogs = getDocs(userRef.collection("soil_logs")
                .orderBy("timestamp", Query.Direction.DESCENDING).limit(GardenPrompt.MAX_SOIL_LOGS).get());
        raw.harvestLogs = getDocs(userRef.collection("harvest_logs")
                .orderBy("timestamp", Query.Direction.DESCENDING).limit(GardenPrompt.MAX_HARVEST_LOGS).get());
        raw.crops = getDocs(db.collection("crops").limit(GardenPrompt.MAX_CROPS * 2L).get());
        raw.adminRules = getDocs(db.collection("recommendations").limit(GardenPrompt.MAX_ADMIN_RULES).get());

        Object deviceId = raw.user != null ? raw.user.get("sensorDeviceId") : null;
        if (deviceId instanceof String) {
            String id = ((String) deviceId).trim();
            if (!id.isEmpty() && !id.contains("/") && id.length() <= 100) {
                raw.sensor = getDoc(db.collection("sensor_devices").document(id).get());
            }
        }
        return raw;
    }

    private static Map<String, Object> getDoc(Task<DocumentSnapshot> task) {
        try {
            DocumentSnapshot snap = Tasks.await(task, 20, TimeUnit.SECONDS);
            return snap.exists() ? snap.getData() : null;
        } catch (Exception e) {
            Log.w(TAG, "could not read a document", e);
            return null;
        }
    }

    private static List<Map<String, Object>> getDocs(Task<QuerySnapshot> task) {
        List<Map<String, Object>> out = new ArrayList<>();
        try {
            for (QueryDocumentSnapshot d : Tasks.await(task, 20, TimeUnit.SECONDS)) out.add(d.getData());
        } catch (Exception e) {
            Log.w(TAG, "could not read a collection", e);
        }
        return out;
    }

    // ---------------------------------------------------------------- helpers

    private boolean isOnline() {
        ConnectivityManager cm = (ConnectivityManager) app.getSystemService(Context.CONNECTIVITY_SERVICE);
        if (cm == null) return true;
        NetworkCapabilities caps = cm.getNetworkCapabilities(cm.getActiveNetwork());
        return caps != null && caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET);
    }

    private static String sha256(String s) throws Exception {
        byte[] hash = MessageDigest.getInstance("SHA-256").digest(s.getBytes("UTF-8"));
        StringBuilder hex = new StringBuilder();
        for (byte b : hash) hex.append(String.format("%02x", b));
        return hex.toString();
    }
}
