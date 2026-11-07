package com.vinland.app.recommendation;

import android.content.Context;
import android.content.SharedPreferences;
import android.text.TextUtils;

import com.android.volley.DefaultRetryPolicy;
import com.android.volley.Request;
import com.android.volley.RequestQueue;
import com.android.volley.toolbox.JsonObjectRequest;
import com.google.android.gms.tasks.Task;
import com.google.android.gms.tasks.Tasks;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.Query;
import com.google.firebase.firestore.QuerySnapshot;
import com.vinland.app.data.Crop;
import com.vinland.app.weather.WeatherConfig;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collection;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * LLM-powered crop & soil-care recommendation engine.
 *
 * Flow: gather context (live readings, crop database, admin rules from the web panel,
 * the user's recent soil/harvest logs, current season) → ask an LLM for strict JSON →
 * validate it against the crop database → hand back a {@link RecommendationResult}.
 *
 * Providers are tried in order OpenAI → Groq → Gemini, skipping any without a key.
 * If all fail, {@link Callback#onError} fires and the caller keeps its offline result.
 */
public class LlmRecommendationEngine {

    // Model choices for recommendations. Change here if a provider retires a model.
    static final String OPENAI_MODEL = "gpt-4o-mini";
    static final String GROQ_MODEL = "llama-3.3-70b-versatile";
    static final String GEMINI_MODEL = "gemini-2.5-flash";

    private static final int TIMEOUT_MS = 30000;
    private static final int MAX_CROPS = 4;

    public interface Callback {
        void onResult(RecommendationResult result);

        void onError(String reason);
    }

    private final Context context;
    private final RequestQueue queue;
    private final FirebaseFirestore db;
    private final FirebaseAuth auth;

    public LlmRecommendationEngine(Context context, RequestQueue queue, FirebaseFirestore db, FirebaseAuth auth) {
        this.context = context.getApplicationContext();
        this.queue = queue;
        this.db = db;
        this.auth = auth;
    }

    /** Cancels in-flight LLM requests (call from onDestroy). */
    public void cancel() {
        queue.cancelAll(this);
    }

    public boolean hasAnyProvider() {
        return !buildProviders().isEmpty();
    }

    public void recommend(double moisture, double ph, double n, double p, double k,
                          Collection<Crop> crops, List<CropScorer.Match> ranked, Callback cb) {
        List<Provider> providers = buildProviders();
        if (providers.isEmpty()) {
            cb.onError("No AI API key configured");
            return;
        }
        if (crops.isEmpty()) {
            cb.onError("Crop database is empty");
            return;
        }

        gatherFirestoreContext(extra -> {
            String userPrompt;
            try {
                userPrompt = buildContext(moisture, ph, n, p, k, ranked, extra).toString(2);
            } catch (JSONException e) {
                cb.onError("Could not build prompt");
                return;
            }
            tryProvider(providers, 0, SYSTEM_PROMPT, userPrompt, crops, cb, "");
        });
    }

    // ---------------------------------------------------------------- prompt

    static final String SYSTEM_PROMPT =
            "You are Vinland AI, the crop recommendation engine of an urban-gardening app for Quezon City, Philippines "
                    + "(tropical climate; dry season roughly November-May, wet season June-October; gardeners use containers, "
                    + "rooftops and small plots).\n\n"
                    + "You receive JSON with live ESP32 soil sensor readings, the app's crop database (with each crop's "
                    + "optimal ranges and a pre-computed matchScore), admin-defined recommendation rules, the user's recent "
                    + "soil readings and harvests, and today's date/season.\n\n"
                    + "RULES:\n"
                    + "1. Only recommend crops listed in cropDatabase, using their exact name. Never invent crops.\n"
                    + "2. Treat each crop's ranges as authoritative. Use matchScore as a strong prior; you may re-rank "
                    + "using season, the month's tip, trends in recent readings, and the user's harvest history.\n"
                    + "3. Apply any adminRules whose condition matches the current readings, and follow their advice.\n"
                    + "4. Prefer organic, locally available amendments (vermicompost, FPJ, FFJ, FAA, banana peel tea, "
                    + "Cal-Phos eggshell tea, wood ash, agricultural lime, carbonized rice hull, coco peat). Give quantities "
                    + "per pot or per square meter.\n"
                    + "5. Be concise and practical. No disclaimers.\n\n"
                    + "Respond with a single JSON object only, matching exactly this shape:\n"
                    + "{\n"
                    + "  \"soilStatus\": \"Optimal\" | \"Fair\" | \"Action Needed\",\n"
                    + "  \"summary\": \"1-2 sentence plain-language soil verdict\",\n"
                    + "  \"recommendedCrops\": [ {\"name\": \"exact crop name\", \"suitability\": 0-100, "
                    + "\"reason\": \"one sentence why it fits now\", \"adjustments\": \"one sentence on what to fix for this crop\"} ],\n"
                    + "  \"topAction\": \"most important next step, max 5 words\",\n"
                    + "  \"amendments\": [\"specific step with quantity\", ...],\n"
                    + "  \"watering\": \"watering guidance for the current season and moisture\",\n"
                    + "  \"report\": \"120-220 word agronomist report; use **bold** headings and '• ' bullets, newlines allowed\"\n"
                    + "}\n"
                    + "List 1-" + MAX_CROPS + " crops, best first, and at most 4 amendments.";

    private JSONObject buildContext(double moisture, double ph, double n, double p, double k,
                                    List<CropScorer.Match> ranked, FirestoreExtras extra) throws JSONException {
        Calendar cal = Calendar.getInstance();
        int month = cal.get(Calendar.MONTH); // 0-based
        boolean wet = month >= Calendar.JUNE && month <= Calendar.OCTOBER;

        JSONObject root = new JSONObject();
        root.put("date", new SimpleDateFormat("yyyy-MM-dd (MMMM)", Locale.US).format(cal.getTime()));
        root.put("season", wet ? "wet / rainy season" : "dry season");

        JSONObject readings = new JSONObject();
        readings.put("moisturePercent", moisture);
        readings.put("ph", ph);
        readings.put("nitrogenPpm", n);
        readings.put("phosphorusPpm", p);
        readings.put("potassiumPpm", k);
        root.put("liveReadings", readings);

        JSONArray cropsJson = new JSONArray();
        for (CropScorer.Match m : ranked) {
            Crop c = m.crop;
            JSONObject o = new JSONObject();
            o.put("name", c.getName());
            if (!TextUtils.isEmpty(c.getCategory())) o.put("category", c.getCategory());
            if (!TextUtils.isEmpty(c.getDescription())) o.put("description", c.getDescription());
            o.put("phRange", c.getPhMin() + "-" + c.getPhMax());
            o.put("moistureRange", c.getMoistureMin() + "-" + c.getMoistureMax() + "%");
            o.put("nitrogenRange", c.getNitrogenMin() + "-" + c.getNitrogenMax() + " ppm");
            o.put("phosphorusRange", c.getPhosphorusMin() + "-" + c.getPhosphorusMax() + " ppm");
            o.put("potassiumRange", c.getPotassiumMin() + "-" + c.getPotassiumMax() + " ppm");
            o.put("matchScore", m.score);
            if (!m.issues.isEmpty()) o.put("outOfRange", new JSONArray(m.issues));
            List<String> tips = c.getMonthlyTips();
            if (tips != null && tips.size() == 12) o.put("tipThisMonth", tips.get(month));
            cropsJson.put(o);
        }
        root.put("cropDatabase", cropsJson);

        if (extra.adminRules.length() > 0) root.put("adminRules", extra.adminRules);
        if (extra.recentSoilLogs.length() > 0) root.put("recentSoilReadings", extra.recentSoilLogs);
        if (extra.recentHarvests.length() > 0) root.put("recentHarvests", extra.recentHarvests);
        return root;
    }

    // ------------------------------------------------------- firestore context

    private static class FirestoreExtras {
        JSONArray adminRules = new JSONArray();
        JSONArray recentSoilLogs = new JSONArray();
        JSONArray recentHarvests = new JSONArray();
    }

    private interface ExtrasCallback {
        void onReady(FirestoreExtras extras);
    }

    private void gatherFirestoreContext(ExtrasCallback cb) {
        List<Task<QuerySnapshot>> tasks = new ArrayList<>();
        Task<QuerySnapshot> rulesTask = db.collection("recommendations").get();
        tasks.add(rulesTask);

        Task<QuerySnapshot> soilTask = null;
        Task<QuerySnapshot> harvestTask = null;
        if (auth.getCurrentUser() != null) {
            String uid = auth.getCurrentUser().getUid();
            soilTask = db.collection("users").document(uid).collection("soil_logs")
                    .orderBy("timestamp", Query.Direction.DESCENDING).limit(5).get();
            harvestTask = db.collection("users").document(uid).collection("harvest_logs")
                    .orderBy("timestamp", Query.Direction.DESCENDING).limit(10).get();
            tasks.add(soilTask);
            tasks.add(harvestTask);
        }

        final Task<QuerySnapshot> fSoil = soilTask;
        final Task<QuerySnapshot> fHarvest = harvestTask;
        // whenAllComplete never fails; each task is checked individually so one
        // missing permission doesn't block the recommendation.
        Tasks.whenAllComplete(tasks).addOnCompleteListener(done -> {
            FirestoreExtras extras = new FirestoreExtras();
            SimpleDateFormat dateFmt = new SimpleDateFormat("yyyy-MM-dd", Locale.US);
            try {
                if (rulesTask.isSuccessful() && rulesTask.getResult() != null) {
                    for (DocumentSnapshot d : rulesTask.getResult().getDocuments()) {
                        JSONObject r = new JSONObject();
                        putIfPresent(r, "title", d.getString("title"));
                        putIfPresent(r, "parameter", d.getString("parameter"));
                        putIfPresent(r, "condition", d.getString("condition"));
                        putIfPresent(r, "status", d.getString("status"));
                        putIfPresent(r, "advice", d.getString("advice"));
                        putIfPresent(r, "severity", d.getString("severity"));
                        Object crops = d.get("applicableCrops");
                        if (crops instanceof List) r.put("applicableCrops", new JSONArray((List<?>) crops));
                        if (r.length() > 0) extras.adminRules.put(r);
                    }
                }
                if (fSoil != null && fSoil.isSuccessful() && fSoil.getResult() != null) {
                    for (DocumentSnapshot d : fSoil.getResult().getDocuments()) {
                        JSONObject r = new JSONObject();
                        Long ts = d.getLong("timestamp");
                        if (ts != null) r.put("date", dateFmt.format(new Date(ts)));
                        putIfPresent(r, "crop", d.getString("cropName"));
                        putNum(r, "moisture", d.getDouble("moisture"));
                        putNum(r, "ph", d.getDouble("ph"));
                        putNum(r, "n", d.getDouble("nitrogen"));
                        putNum(r, "p", d.getDouble("phosphorus"));
                        putNum(r, "k", d.getDouble("potassium"));
                        extras.recentSoilLogs.put(r);
                    }
                }
                if (fHarvest != null && fHarvest.isSuccessful() && fHarvest.getResult() != null) {
                    for (DocumentSnapshot d : fHarvest.getResult().getDocuments()) {
                        JSONObject r = new JSONObject();
                        Long ts = d.getLong("timestamp");
                        if (ts != null) r.put("date", dateFmt.format(new Date(ts)));
                        putIfPresent(r, "crop", d.getString("cropName"));
                        putNum(r, "kg", d.getDouble("quantityKg"));
                        putIfPresent(r, "notes", d.getString("notes"));
                        extras.recentHarvests.put(r);
                    }
                }
            } catch (JSONException ignored) {
                // Context is best-effort; send whatever was collected.
            }
            cb.onReady(extras);
        });
    }

    private static void putIfPresent(JSONObject o, String key, String value) throws JSONException {
        if (!TextUtils.isEmpty(value)) o.put(key, value);
    }

    private static void putNum(JSONObject o, String key, Double value) throws JSONException {
        if (value != null) o.put(key, value);
    }

    // --------------------------------------------------------------- providers

    private static class Provider {
        final String label;
        final String kind; // "openai" (OpenAI-compatible) or "gemini"
        final String url;
        final String model;
        final String key;

        Provider(String label, String kind, String url, String model, String key) {
            this.label = label;
            this.kind = kind;
            this.url = url;
            this.model = model;
            this.key = key;
        }
    }

    private List<Provider> buildProviders() {
        List<Provider> list = new ArrayList<>();
        String openAi = trim(WeatherConfig.OPENAI_API_KEY);
        if (openAi.startsWith("sk-")) {
            list.add(new Provider("OpenAI · " + OPENAI_MODEL, "openai",
                    "https://api.openai.com/v1/chat/completions", OPENAI_MODEL, openAi));
        }
        String groq = trim(WeatherConfig.GROQ_API_KEY);
        if (groq.startsWith("gsk_")) {
            list.add(new Provider("Groq · " + GROQ_MODEL, "openai",
                    "https://api.groq.com/openai/v1/chat/completions", GROQ_MODEL, groq));
        }
        String gemini = geminiKey();
        if (gemini.startsWith("AIza")) {
            list.add(new Provider("Gemini · " + GEMINI_MODEL, "gemini",
                    "https://generativelanguage.googleapis.com/v1beta/models/" + GEMINI_MODEL + ":generateContent",
                    GEMINI_MODEL, gemini));
        }
        return list;
    }

    private String geminiKey() {
        SharedPreferences prefs = context.getSharedPreferences("VinlandPrefs", Context.MODE_PRIVATE);
        String custom = trim(prefs.getString("gemini_api_key", ""));
        return !TextUtils.isEmpty(custom) ? custom : trim(WeatherConfig.GEMINI_API_KEY);
    }

    private static String trim(String s) {
        return s == null ? "" : s.trim();
    }

    private void tryProvider(List<Provider> providers, int index, String system, String user,
                             Collection<Crop> crops, Callback cb, String lastError) {
        if (index >= providers.size()) {
            cb.onError(TextUtils.isEmpty(lastError) ? "All AI providers failed" : lastError);
            return;
        }
        Provider pr = providers.get(index);
        JSONObject body;
        try {
            body = "gemini".equals(pr.kind) ? geminiBody(system, user) : openAiBody(pr.model, system, user);
        } catch (JSONException e) {
            tryProvider(providers, index + 1, system, user, crops, cb, "Bad request body");
            return;
        }

        JsonObjectRequest req = new JsonObjectRequest(Request.Method.POST, pr.url, body,
                response -> {
                    String text = "gemini".equals(pr.kind) ? extractGeminiText(response) : extractOpenAiText(response);
                    RecommendationResult result = parseResult(text, crops);
                    if (result == null) {
                        tryProvider(providers, index + 1, system, user, crops, cb, pr.label + ": invalid JSON");
                        return;
                    }
                    result.provider = pr.label;
                    cb.onResult(result);
                },
                error -> {
                    String msg = pr.label + " error";
                    if (error.networkResponse != null) msg += " (HTTP " + error.networkResponse.statusCode + ")";
                    tryProvider(providers, index + 1, system, user, crops, cb, msg);
                }) {
            @Override
            public Map<String, String> getHeaders() {
                Map<String, String> h = new HashMap<>();
                h.put("Content-Type", "application/json");
                if ("gemini".equals(pr.kind)) {
                    h.put("x-goog-api-key", pr.key);
                } else {
                    h.put("Authorization", "Bearer " + pr.key);
                }
                return h;
            }
        };
        // LLM calls routinely exceed Volley's 2.5s default; don't retry (it would double-bill).
        req.setRetryPolicy(new DefaultRetryPolicy(TIMEOUT_MS, 0, 1f));
        req.setTag(this);
        queue.add(req);
    }

    private static JSONObject openAiBody(String model, String system, String user) throws JSONException {
        JSONObject body = new JSONObject();
        body.put("model", model);
        body.put("temperature", 0.2);
        body.put("response_format", new JSONObject().put("type", "json_object"));
        JSONArray messages = new JSONArray();
        messages.put(new JSONObject().put("role", "system").put("content", system));
        messages.put(new JSONObject().put("role", "user").put("content", user));
        body.put("messages", messages);
        return body;
    }

    private static JSONObject geminiBody(String system, String user) throws JSONException {
        JSONObject body = new JSONObject();
        body.put("systemInstruction", new JSONObject()
                .put("parts", new JSONArray().put(new JSONObject().put("text", system))));
        body.put("contents", new JSONArray().put(new JSONObject()
                .put("role", "user")
                .put("parts", new JSONArray().put(new JSONObject().put("text", user)))));
        body.put("generationConfig", new JSONObject()
                .put("temperature", 0.2)
                .put("responseMimeType", "application/json"));
        return body;
    }

    private static String extractOpenAiText(JSONObject response) {
        JSONArray choices = response.optJSONArray("choices");
        if (choices == null || choices.length() == 0) return null;
        JSONObject msg = choices.optJSONObject(0) != null ? choices.optJSONObject(0).optJSONObject("message") : null;
        return msg != null ? msg.optString("content", null) : null;
    }

    private static String extractGeminiText(JSONObject response) {
        JSONArray candidates = response.optJSONArray("candidates");
        if (candidates == null || candidates.length() == 0) return null;
        JSONObject content = candidates.optJSONObject(0) != null ? candidates.optJSONObject(0).optJSONObject("content") : null;
        JSONArray parts = content != null ? content.optJSONArray("parts") : null;
        if (parts == null || parts.length() == 0) return null;
        return parts.optJSONObject(0) != null ? parts.optJSONObject(0).optString("text", null) : null;
    }

    // ----------------------------------------------------------------- parsing

    /** Parses and validates the model's JSON. Returns null if unusable. */
    static RecommendationResult parseResult(String raw, Collection<Crop> crops) {
        if (TextUtils.isEmpty(raw)) return null;
        int start = raw.indexOf('{');
        int end = raw.lastIndexOf('}');
        if (start < 0 || end <= start) return null;

        Map<String, Crop> byName = new HashMap<>();
        for (Crop c : crops) {
            if (c.getName() != null) byName.put(c.getName().trim().toLowerCase(Locale.US), c);
        }

        try {
            JSONObject o = new JSONObject(raw.substring(start, end + 1));
            RecommendationResult r = new RecommendationResult();
            r.soilStatus = o.optString("soilStatus", "").trim();
            r.summary = o.optString("summary", "").trim();
            r.topAction = o.optString("topAction", "").trim();
            r.watering = o.optString("watering", "").trim();
            r.report = o.optString("report", "").trim();

            JSONArray am = o.optJSONArray("amendments");
            if (am != null) {
                for (int i = 0; i < am.length() && r.amendments.size() < 6; i++) {
                    String s = am.optString(i, "").trim();
                    if (!s.isEmpty()) r.amendments.add(s);
                }
            }

            JSONArray rc = o.optJSONArray("recommendedCrops");
            if (rc != null) {
                for (int i = 0; i < rc.length() && r.crops.size() < MAX_CROPS; i++) {
                    JSONObject c = rc.optJSONObject(i);
                    if (c == null) continue;
                    Crop crop = byName.get(c.optString("name", "").trim().toLowerCase(Locale.US));
                    if (crop == null) continue; // hallucinated / unknown crop: drop it
                    boolean dup = false;
                    for (RecommendationResult.CropPick existing : r.crops) {
                        if (existing.crop == crop) dup = true;
                    }
                    if (dup) continue;
                    RecommendationResult.CropPick pick = new RecommendationResult.CropPick();
                    pick.crop = crop;
                    pick.suitability = Math.max(0, Math.min(100, c.optInt("suitability", 0)));
                    pick.reason = c.optString("reason", "").trim();
                    pick.adjustments = c.optString("adjustments", "").trim();
                    r.crops.add(pick);
                }
            }

            // Require at least a valid crop list or a report to count as a usable answer.
            if (r.crops.isEmpty() && r.report.isEmpty()) return null;
            return r;
        } catch (JSONException e) {
            return null;
        }
    }
}
