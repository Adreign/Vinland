package com.vinland.app.chat;

import android.content.SharedPreferences;
import android.os.Bundle;
import android.text.TextUtils;
import android.view.View;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.android.volley.Request;
import com.android.volley.RequestQueue;
import com.android.volley.toolbox.JsonObjectRequest;
import com.android.volley.toolbox.Volley;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.chip.Chip;
import com.google.android.material.textfield.TextInputEditText;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FirebaseFirestore;
import com.vinland.app.R;
import com.vinland.app.weather.WeatherConfig;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public class AiChatActivity extends AppCompatActivity {

    private RecyclerView rvChatMessages;
    private TextInputEditText etInput;
    private MaterialButton btnSend;
    private TextView btnClearChat;
    private ProgressBar progressAiLoading;
    private Chip chipYellowLeaves, chipNpkRatio, chipSoilPh, chipWaterSchedule;

    private ChatAdapter chatAdapter;
    private final List<ChatMessage> messageList = new ArrayList<>();
    private RequestQueue requestQueue;
    private FirebaseFirestore db;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_ai_chat);

        requestQueue = Volley.newRequestQueue(this);
        db = FirebaseFirestore.getInstance();

        TextView btnBack = findViewById(R.id.btnBack);
        btnClearChat = findViewById(R.id.btnClearChat);
        rvChatMessages = findViewById(R.id.rvChatMessages);
        etInput = findViewById(R.id.etInput);
        btnSend = findViewById(R.id.btnSend);
        progressAiLoading = findViewById(R.id.progressAiLoading);

        chipYellowLeaves = findViewById(R.id.chipYellowLeaves);
        chipNpkRatio = findViewById(R.id.chipNpkRatio);
        chipSoilPh = findViewById(R.id.chipSoilPh);
        chipWaterSchedule = findViewById(R.id.chipWaterSchedule);

        chatAdapter = new ChatAdapter(messageList);
        rvChatMessages.setLayoutManager(new LinearLayoutManager(this));
        rvChatMessages.setAdapter(chatAdapter);

        btnBack.setOnClickListener(v -> finish());
        btnClearChat.setOnClickListener(v -> clearChatHistory());
        btnSend.setOnClickListener(v -> sendUserMessage());

        chipYellowLeaves.setOnClickListener(v -> sendPresetPrompt("How do I fix yellow leaves on my urban plants in Quezon City?"));
        chipNpkRatio.setOnClickListener(v -> sendPresetPrompt("What is the optimal NPK nutrient ratio for urban vegetable crops?"));
        chipSoilPh.setOnClickListener(v -> sendPresetPrompt("How can I lower or balance soil pH levels naturally for vegetable crops?"));
        chipWaterSchedule.setOnClickListener(v -> sendPresetPrompt("What is the best watering schedule for Quezon City urban gardens during tropical wet and dry seasons?"));

        // Default Welcome Message from AI
        addAiMessage("Hello! 🌿 I am your Vinland AI Agronomist & Urban Gardening Assistant for Quezon City.\n\nAsk me anything about soil NPK, pH levels, crop care, organic fertilizers, or pest management!");
    }

    private String getEffectiveGroqApiKey() {
        return WeatherConfig.GROQ_API_KEY;
    }

    private void clearChatHistory() {
        messageList.clear();
        chatAdapter.notifyDataSetChanged();
        addAiMessage("Hello! 🌿 I am your Vinland AI Agronomist & Urban Gardening Assistant for Quezon City.\n\nAsk me anything about soil NPK, pH levels, crop care, organic fertilizers, or pest management!");
        Toast.makeText(this, "Chat history cleared", Toast.LENGTH_SHORT).show();
    }

    private void sendPresetPrompt(String promptText) {
        etInput.setText(promptText);
        sendUserMessage();
    }

    private void sendUserMessage() {
        String text = etInput.getText() != null ? etInput.getText().toString().trim() : "";
        if (TextUtils.isEmpty(text)) return;

        // 1. Add User Message
        addUserMessage(text);
        etInput.setText("");

        // 2. Fetch AI Response
        fetchGroqResponse(text);
    }

    private void addUserMessage(String text) {
        messageList.add(new ChatMessage(text, true, System.currentTimeMillis()));
        chatAdapter.notifyItemInserted(messageList.size() - 1);
        rvChatMessages.scrollToPosition(messageList.size() - 1);
    }

    private void addAiMessage(String text) {
        messageList.add(new ChatMessage(text, false, System.currentTimeMillis()));
        chatAdapter.notifyItemInserted(messageList.size() - 1);
        rvChatMessages.scrollToPosition(messageList.size() - 1);
    }

    private void fetchGroqResponse(String userPrompt) {
        progressAiLoading.setVisibility(View.VISIBLE);
        btnSend.setEnabled(false);

        // Fetch live ESP32 soil sensor telemetry from Firestore to supply real-time context to the AI
        db.collection("sensor_devices").document("VINLAND-ESP32-01")
                .get()
                .addOnCompleteListener(task -> {
                    String telemetryContext = "";
                    if (task.isSuccessful() && task.getResult() != null && task.getResult().exists()) {
                        DocumentSnapshot doc = task.getResult();
                        Double m = doc.getDouble("moisture");
                        Double ph = doc.getDouble("ph");
                        Double n = doc.getDouble("nitrogen");
                        Double p = doc.getDouble("phosphorus");
                        Double k = doc.getDouble("potassium");

                        if (m != null && ph != null && n != null && p != null && k != null) {
                            telemetryContext = String.format(Locale.US,
                                    "\n\n[Live Soil Sensor Telemetry (VINLAND-ESP32-01)]:\n"
                                            + "• Soil Moisture: %.1f%%\n"
                                            + "• Soil pH: %.1f\n"
                                            + "• Nitrogen (N): %.0f ppm\n"
                                            + "• Phosphorus (P): %.0f ppm\n"
                                            + "• Potassium (K): %.0f ppm\n",
                                    m, ph, n, p, k);
                        }
                    }

                    sendPromptWithTelemetry(userPrompt, telemetryContext);
                });
    }

    private void sendPromptWithTelemetry(String userPrompt, String telemetryContext) {
        String openAiKey = WeatherConfig.OPENAI_API_KEY != null ? WeatherConfig.OPENAI_API_KEY.trim() : "";
        String groqKey = WeatherConfig.GROQ_API_KEY != null ? WeatherConfig.GROQ_API_KEY.trim() : "";

        String systemInstruction = "You are Vinland AI, an expert agricultural assistant specializing in urban leaf vegetable gardening in Quezon City, Philippines. "
                + "Your core task is to analyze soil NPK (Nitrogen, Phosphorus, Potassium) sensor readings and provide actionable, highly accurate planting and fertilizer recommendations.\n\n"
                + "YOUR DOMAIN & FOCUS:\n"
                + "• Target Crops: Leafy vegetables suitable for urban gardening (tomato, green pepper, pechay, kangkong, lettuce, etc.).\n"
                + "• Environment: Urban settings in Quezon City (container gardening, rooftop gardens, vertical setups, tropical climate with distinct wet and dry seasons).\n\n"
                + "HOW YOU SHOULD RESPOND:\n"
                + "• Analyze the Data: When provided with NPK values, explain if levels are low, optimal, or high for leafy greens (which require higher Nitrogen).\n"
                + "• Actionable Advice: Recommend specific organic fertilizers (Vermicompost, FPJ, FAA, Banana Peel Tea, Cal-Phos eggshell tea) or soil amendments (Wood Ash, Agricultural Lime, CRH).\n"
                + "• Tone & Formatting: Be concise, encouraging, and practical. Use short paragraphs and bullet points.\n"
                + "• Language: Respond naturally in Tagalog or Taglish when asked in Tagalog/Taglish.\n"
                + "• Safety/Constraints: If asked about unrelated topics (sports, finance, crypto, politics), politely redirect back to soil monitoring and urban gardening.\n\n"
                + "EXPERT KNOWLEDGE BASE & FEW-SHOT EXAMPLES:\n"
                + "1. Yellow Leaves (Chlorosis): Nitrogen deficiency or poor drainage. Solution: Apply 2 tbsp vermicompost or FPJ weekly and improve pot drainage.\n"
                + "2. Tomato NPK Targets: Vegetative stage (10-10-10 or high-N compost), Fruiting stage (5-10-10 or crushed eggshell & banana peel tea for Calcium/Phosphorus/Potassium).\n"
                + "3. Soil pH Balancers: Ideal pH is 6.0-7.0. Raise low pH (<5.5) with agricultural lime or wood ash. Lower high pH (>7.5) with vermicompost or elemental sulfur.\n"
                + "4. ESP32 Sensor Targets: Moisture 45-75%, pH 6.0-7.0, N 120-200 ppm, P 60-120 ppm, K 150-250 ppm.\n"
                + "5. Organic Pest Spray: For aphids/whiteflies, spray neem oil solution (1 tsp organic neem oil + 1/2 tsp soap per 1L water) in late afternoon.\n"
                + "6. Potting Mix Ratio: 1:1:1 (Garden Soil : Carbonized Rice Hull / Coco Peat : Vermicompost).\n"
                + "7. Watering Schedule: Dry season (twice daily: 6-7:30 AM & 5 PM). Wet season (check moisture sensor first; skip watering if >60%).\n"
                + "8. Blossom End Rot: Caused by Calcium deficiency and irregular watering. Fix with Cal-Phos tea (eggshells in vinegar) and steady irrigation.\n"
                + "9. Fermented Plant Juice (FPJ): KNF Nitrogen booster made from fermented kangkong/banana shoots + brown sugar (1:1 ratio for 7 days).\n"
                + telemetryContext;

        if (!TextUtils.isEmpty(openAiKey) && openAiKey.startsWith("sk-")) {
            // Query OpenAI Fine-Tuned Model (gpt-4o-mini / fine-tuned model ID)
            queryOpenAiApi(userPrompt, systemInstruction, openAiKey);
        } else if (!TextUtils.isEmpty(groqKey) && groqKey.startsWith("gsk_")) {
            // Query Groq Cloud API (Llama 3.1 8B Instant)
            queryGroqApi(userPrompt, systemInstruction, groqKey);
        } else {
            // Local AI Knowledge Engine
            progressAiLoading.setVisibility(View.GONE);
            btnSend.setEnabled(true);
            addAiMessage(generateAgronomistAiResponse(userPrompt));
        }
    }

    private void queryOpenAiApi(String userPrompt, String systemInstruction, String apiKey) {
        String url = "https://api.openai.com/v1/chat/completions";
        String modelId = !TextUtils.isEmpty(WeatherConfig.FINE_TUNED_MODEL_ID) ? WeatherConfig.FINE_TUNED_MODEL_ID : "gpt-4o-mini";

        try {
            JSONObject jsonBody = new JSONObject();
            jsonBody.put("model", modelId);

            JSONArray messages = new JSONArray();

            JSONObject sysMsg = new JSONObject();
            sysMsg.put("role", "system");
            sysMsg.put("content", systemInstruction);
            messages.put(sysMsg);

            JSONObject usrMsg = new JSONObject();
            usrMsg.put("role", "user");
            usrMsg.put("content", userPrompt);
            messages.put(usrMsg);

            jsonBody.put("messages", messages);
            jsonBody.put("temperature", 0.7);

            JsonObjectRequest request = new JsonObjectRequest(
                    Request.Method.POST,
                    url,
                    jsonBody,
                    response -> {
                        progressAiLoading.setVisibility(View.GONE);
                        btnSend.setEnabled(true);
                        try {
                            JSONArray choices = response.optJSONArray("choices");
                            if (choices != null && choices.length() > 0) {
                                JSONObject firstChoice = choices.getJSONObject(0);
                                JSONObject messageObj = firstChoice.optJSONObject("message");
                                if (messageObj != null) {
                                    String aiText = messageObj.optString("content", "");
                                    if (!TextUtils.isEmpty(aiText)) {
                                        addAiMessage(aiText.trim());
                                        return;
                                    }
                                }
                            }
                            addAiMessage(generateAgronomistAiResponse(userPrompt));
                        } catch (JSONException e) {
                            addAiMessage(generateAgronomistAiResponse(userPrompt));
                        }
                    },
                    error -> {
                        progressAiLoading.setVisibility(View.GONE);
                        btnSend.setEnabled(true);
                        String errorMsg = "OpenAI API Error";
                        if (error.networkResponse != null) {
                            errorMsg += " (HTTP " + error.networkResponse.statusCode + ")";
                        }
                        Toast.makeText(AiChatActivity.this, errorMsg + ". Falling back to local agronomist.", Toast.LENGTH_LONG).show();
                        addAiMessage(generateAgronomistAiResponse(userPrompt));
                    }
            ) {
                @Override
                public Map<String, String> getHeaders() {
                    Map<String, String> headers = new HashMap<>();
                    headers.put("Content-Type", "application/json");
                    headers.put("Authorization", "Bearer " + apiKey);
                    return headers;
                }
            };

            requestQueue.add(request);

        } catch (JSONException e) {
            progressAiLoading.setVisibility(View.GONE);
            btnSend.setEnabled(true);
            addAiMessage(generateAgronomistAiResponse(userPrompt));
        }
    }

    private void queryGroqApi(String userPrompt, String systemInstruction, String apiKey) {
        String url = "https://api.groq.com/openai/v1/chat/completions";

        try {
            JSONObject jsonBody = new JSONObject();
            jsonBody.put("model", "llama-3.1-8b-instant");

            JSONArray messages = new JSONArray();

            JSONObject sysMsg = new JSONObject();
            sysMsg.put("role", "system");
            sysMsg.put("content", systemInstruction);
            messages.put(sysMsg);

            JSONObject usrMsg = new JSONObject();
            usrMsg.put("role", "user");
            usrMsg.put("content", userPrompt);
            messages.put(usrMsg);

            jsonBody.put("messages", messages);
            jsonBody.put("temperature", 0.7);

            JsonObjectRequest request = new JsonObjectRequest(
                    Request.Method.POST,
                    url,
                    jsonBody,
                    response -> {
                        progressAiLoading.setVisibility(View.GONE);
                        btnSend.setEnabled(true);
                        try {
                            JSONArray choices = response.optJSONArray("choices");
                            if (choices != null && choices.length() > 0) {
                                JSONObject firstChoice = choices.getJSONObject(0);
                                JSONObject messageObj = firstChoice.optJSONObject("message");
                                if (messageObj != null) {
                                    String aiText = messageObj.optString("content", "");
                                    if (!TextUtils.isEmpty(aiText)) {
                                        addAiMessage(aiText.trim());
                                        return;
                                    }
                                }
                            }
                            addAiMessage(generateAgronomistAiResponse(userPrompt));
                        } catch (JSONException e) {
                            addAiMessage(generateAgronomistAiResponse(userPrompt));
                        }
                    },
                    error -> {
                        progressAiLoading.setVisibility(View.GONE);
                        btnSend.setEnabled(true);
                        String errorMsg = "Groq API Error";
                        if (error.networkResponse != null) {
                            errorMsg += " (HTTP " + error.networkResponse.statusCode + ")";
                        }
                        Toast.makeText(AiChatActivity.this, errorMsg + ". Falling back to local agronomist.", Toast.LENGTH_LONG).show();
                        addAiMessage(generateAgronomistAiResponse(userPrompt));
                    }
            ) {
                @Override
                public Map<String, String> getHeaders() {
                    Map<String, String> headers = new HashMap<>();
                    headers.put("Content-Type", "application/json");
                    headers.put("Authorization", "Bearer " + apiKey);
                    return headers;
                }
            };

            requestQueue.add(request);

        } catch (JSONException e) {
            progressAiLoading.setVisibility(View.GONE);
            btnSend.setEnabled(true);
            addAiMessage(generateAgronomistAiResponse(userPrompt));
        }
    }

    private String generateAgronomistAiResponse(String prompt) {
        String query = prompt.toLowerCase().trim();

        // 0. Friendly Greetings
        if (query.equals("hi") || query.equals("hii") || query.equals("hello") || query.equals("hey")
                || query.contains("good morning") || query.contains("good afternoon") || query.contains("good evening")
                || query.contains("magandang araw") || query.contains("kumusta") || query.contains("kamusta")) {
            return "Hello! 👋 Welcome to Vinland AI Agronomist!\n\n" +
                   "I am your personal AI urban gardening assistant for Quezon City. How can I help your garden today?\n\n" +
                   "• Ask me about **soil NPK nutrients** or **pH levels**.\n" +
                   "• Get care tips for **your urban crops** & **leafy greens**.\n" +
                   "• Ask for **watering schedules** or **organic pest control**!";
        }

        // 0.1 Thanks & Appreciation
        if (query.contains("thank") || query.contains("salamat") || query.contains("great") || query.contains("awesome") || query.contains("good job")) {
            return "You're very welcome! 🌿 Happy urban gardening!\n\n" +
                   "Let me know if you need any more tips for your Quezon City garden or help with your ESP32 soil sensor telemetry.";
        }

        // 0.2 Who are you / Identity
        if (query.contains("who are you") || query.contains("what can you do") || query.contains("what do you do") || query.contains("help")) {
            return "🌿 **I am Vinland's AI Agronomist Chatbot!**\n\n" +
                   "I specialize in tropical urban agriculture for Quezon City. I can help you with:\n" +
                   "1. **Soil Diagnostic Analysis**: Interpreting Moisture, pH, and Nitrogen, Phosphorus, Potassium (NPK) levels.\n" +
                   "2. **Crop Guidance**: Tailored care for urban crops, leafy greens, and garden vegetables.\n" +
                   "3. **Soil Balancers**: Recommendations for vermicompost, bone meal, banana peel tea, and agricultural lime.\n" +
                   "4. **Weather & Irrigation**: Tropical wet and dry season watering schedules.";
        }

        // 1. Yellow leaves
        if (query.contains("yellow") && (query.contains("leaf") || query.contains("leaves") || query.contains("plant"))) {
            return "🍂 **How to Fix Yellow Leaves on Urban Garden Crops:**\n\n" +
                   "• **Nitrogen Deficiency**: If bottom leaves turn pale yellow first while veins stay green, apply organic compost, vermicompost, or dilute fish amino acid (FAA) every 7 days.\n\n" +
                   "• **Overwatering / Poor Drainage**: Quezon City tropical rain can saturate pot soil. Ensure containers have bottom drainage holes and use a well-draining soil mix (1:1:1 garden soil, rice hull, and compost).\n\n" +
                   "• **Fungal Early Blight / Spot**: Look for brown spot concentric rings on yellow leaves. Prune infected lower leaves, avoid overhead watering, and spray organic neem oil solution.";
        }

        // 2. NPK Ratio
        if (query.contains("npk") || query.contains("ratio") || query.contains("nutrient")) {
            return "🫑 **Optimal NPK Nutrient Ratio for Urban Vegetables:**\n\n" +
                   "• **Vegetative Phase (Weeks 1-4)**: Use **10-10-10** or high-Nitrogen organic compost to build strong green stems and leaf foliage.\n\n" +
                   "• **Flowering & Fruiting Phase (Weeks 5+)**: Switch to a **5-10-10** or **5-15-15** low-Nitrogen, high-Phosphorus & Potassium feed (like crushed eggshell tea or bone meal) to boost flower blooms and prevent blossom drop.\n\n" +
                   "• **Target NPK Sensor Readings**: Nitrogen 120-200 ppm, Phosphorus 60-120 ppm, Potassium 150-250 ppm.";
        }

        // 3. Lowering or Raising Soil pH
        if (query.contains("ph") || query.contains("acid") || query.contains("alkaline") || query.contains("lower") || query.contains("raise")) {
            return "⚗️ **Managing Soil pH for Urban Vegetables:**\n\n" +
                   "• **Ideal pH Range**: Most Quezon City leafy vegetables and urban crops thrive in **pH 6.0 to 7.0** (slightly acidic to neutral).\n\n" +
                   "• **To Lower High pH (Alkaline > 7.5)**: Incorporate organic vermicompost, pine needles, or elemental sulfur into the soil.\n\n" +
                   "• **To Raise Low pH (Acidic < 5.5)**: Apply agricultural lime (calcium carbonate) or clean wood ash at 1 tablespoon per pot, water thoroughly, and re-test pH after 3 days.";
        }

        // 4. Watering Schedule for Quezon City
        if (query.contains("water") || query.contains("watering") || query.contains("schedule") || query.contains("quezon") || query.contains("rain")) {
            return "🌧️ **Quezon City Urban Garden Watering Schedule:**\n\n" +
                   "• **Dry Season (November to April)**:\n" +
                   "  - Water **twice daily**: Early morning (6:00 AM – 7:30 AM) and late afternoon (5:00 PM).\n" +
                   "  - Apply water directly at the base/roots, avoiding leaf foliage to prevent fungal diseases.\n\n" +
                   "• **Wet / Rainy Season (May to October)**:\n" +
                   "  - Check soil moisture first using your Vinland sensor! If moisture is above **60%**, skip watering.\n" +
                   "  - Ensure balcony and rooftop pots have elevated drainage trays to prevent waterlogging and root rot.";
        }

        // 5. Fertilizer / Compost Queries
        if (query.contains("fertilizer") || query.contains("compost") || query.contains("feed") || query.contains("organic")) {
            return "🌱 **Organic Fertilizer Guide for Quezon City Urban Gardens:**\n\n" +
                   "• **Nitrogen Boosters**: Vermicompost, fermented plant juice (FPJ), or coffee grounds.\n\n" +
                   "• **Phosphorus Boosters**: Steamed bone meal or fermented fruit juice (FFJ) for root & flower development.\n\n" +
                   "• **Potassium Boosters**: Banana peel liquid tea or wood ash for fruit sweetening and disease resistance.\n\n" +
                   "• **Application Rate**: Top-dress 2 tablespoons of compost per pot every 14 days.";
        }

        // 6. Pest Management
        if (query.contains("pest") || query.contains("bug") || query.contains("worm") || query.contains("aphid") || query.contains("insect")) {
            return "🐛 **Organic Urban Pest Control Solution:**\n\n" +
                   "• **Neem Oil Spray**: Mix 1 tsp organic neem oil + 1/2 tsp mild dish soap in 1 liter of warm water. Spray infected leaves in early evening.\n\n" +
                   "• **Aphids & Whiteflies**: Blast underside of leaves with a firm spray of water or apply soapy water spray.\n\n" +
                   "• **Caterpillars / Hornworms**: Hand-pick in early morning and apply Bacillus thuringiensis (BT) spray if severe.";
        }

        // 7. General Fallback Response
        return "🌿 **Vinland AI Agronomist Recommendation for Your Garden:**\n\n" +
               "For optimal growth of Quezon City urban crops and leafy vegetables:\n\n" +
               "1. Maintain soil moisture between **45% and 75%**.\n" +
               "2. Keep soil pH balanced between **6.0 and 7.0**.\n" +
               "3. Ensure pots receive **6 to 8 hours of direct sunlight** daily.\n" +
               "4. Use a 1:1:1 potting mix (garden soil, rice hull/coco peat, and compost).\n\n" +
               "Feel free to ask specific questions about soil NPK, pH adjustments, or watering schedules!";
    }
}
