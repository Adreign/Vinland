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
import com.vinland.app.R;
import com.vinland.app.weather.WeatherConfig;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;

public class AiChatActivity extends AppCompatActivity {

    private RecyclerView rvChatMessages;
    private TextInputEditText etInput;
    private MaterialButton btnSend;
    private TextView btnBack, btnClearChat;
    private ProgressBar progressAiLoading;
    private Chip chipYellowLeaves, chipNpkRatio, chipSoilPh, chipWaterSchedule;

    private ChatAdapter chatAdapter;
    private final List<ChatMessage> messageList = new ArrayList<>();
    private RequestQueue requestQueue;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_ai_chat);

        requestQueue = Volley.newRequestQueue(this);

        btnBack = findViewById(R.id.btnBack);
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

    private String getEffectiveGeminiApiKey() {
        SharedPreferences prefs = getSharedPreferences("VinlandPrefs", MODE_PRIVATE);
        String customKey = prefs.getString("gemini_api_key", "");
        if (!TextUtils.isEmpty(customKey)) {
            return customKey;
        }
        return WeatherConfig.GEMINI_API_KEY;
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
        fetchGeminiResponse(text);
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

    private void fetchGeminiResponse(String userPrompt) {
        String activeApiKey = getEffectiveGeminiApiKey();

        // If a valid Google Gemini API Key is configured, query Google Cloud AI in real time
        if (!TextUtils.isEmpty(activeApiKey) && (activeApiKey.startsWith("AIzaSy") || activeApiKey.length() > 25)) {
            progressAiLoading.setVisibility(View.VISIBLE);
            btnSend.setEnabled(false);

            String systemInstruction = "You are an expert AI agricultural agronomist and master urban gardener serving Quezon City, Philippines. "
                    + "You assist urban gardeners growing urban crops and leafy vegetables. "
                    + "Provide helpful, friendly, and practical agronomic advice tailored to tropical Quezon City weather, NPK soil nutrients, pH levels, and organic fertilization. "
                    + "Keep your answers structured with clear headings or bullet points where appropriate.\n\nUser Question: " + userPrompt;

            String url = "https://generativelanguage.googleapis.com/v1beta/models/gemini-1.5-flash:generateContent?key=" + activeApiKey;

            try {
                JSONObject jsonBody = new JSONObject();
                JSONArray contents = new JSONArray();
                JSONObject contentObj = new JSONObject();
                JSONArray parts = new JSONArray();
                JSONObject partObj = new JSONObject();

                partObj.put("text", systemInstruction);
                parts.put(partObj);
                contentObj.put("parts", parts);
                contents.put(contentObj);
                jsonBody.put("contents", contents);

                JsonObjectRequest request = new JsonObjectRequest(
                        Request.Method.POST,
                        url,
                        jsonBody,
                        response -> {
                            progressAiLoading.setVisibility(View.GONE);
                            btnSend.setEnabled(true);
                            try {
                                JSONArray candidates = response.optJSONArray("candidates");
                                if (candidates != null && candidates.length() > 0) {
                                    JSONObject content = candidates.getJSONObject(0).optJSONObject("content");
                                    if (content != null) {
                                        JSONArray resParts = content.optJSONArray("parts");
                                        if (resParts != null && resParts.length() > 0) {
                                            String aiText = resParts.getJSONObject(0).optString("text", "");
                                            if (!TextUtils.isEmpty(aiText)) {
                                                addAiMessage(aiText.trim());
                                                return;
                                            }
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
                            addAiMessage(generateAgronomistAiResponse(userPrompt));
                        }
                );

                requestQueue.add(request);

            } catch (JSONException e) {
                progressAiLoading.setVisibility(View.GONE);
                btnSend.setEnabled(true);
                addAiMessage(generateAgronomistAiResponse(userPrompt));
            }
        } else {
            // Local AI Knowledge Engine
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
