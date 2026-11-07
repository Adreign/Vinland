package com.vinland.app.ai.direct;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import com.vinland.app.ai.model.AiRecommendationResponse;

import org.json.JSONArray;
import org.json.JSONObject;
import org.junit.Test;

import java.util.List;

public class SavedRecommendationsTest {

    private static JSONObject doc(long time, String summary) throws Exception {
        JSONObject rec = new JSONObject()
                .put("title", "Add lime").put("reason", "pH low").put("priority", "high")
                .put("suggestedAction", "1 tbsp").put("category", "soil").put("crop", JSONObject.NULL);
        return new JSONObject()
                .put("status", "ok").put("generatedAt", time).put("model", "m")
                .put("soilStatus", "fair").put("summary", summary)
                .put("recommendations", new JSONArray().put(rec))
                .put("missingData", new JSONArray()).put("disclaimer", "d");
    }

    @Test
    public void newestFirstAndCapped() throws Exception {
        String json = null;
        for (long t = 1; t <= 5; t++) json = SavedRecommendations.addTo(json, doc(t, "s" + t), 3);
        List<AiRecommendationResponse> list = SavedRecommendations.parse(json);
        assertEquals(3, list.size());
        assertEquals(5, list.get(0).generatedAt);
        assertEquals(3, list.get(2).generatedAt);
        assertEquals(1, list.get(0).recommendations.size());
    }

    @Test
    public void sameTimeReplacesInsteadOfDuplicating() throws Exception {
        String json = SavedRecommendations.addTo(null, doc(7, "old"), 50);
        json = SavedRecommendations.addTo(json, doc(7, "new"), 50);
        List<AiRecommendationResponse> list = SavedRecommendations.parse(json);
        assertEquals(1, list.size());
        assertEquals("new", list.get(0).summary);
    }

    @Test
    public void deleteRemovesOnlyThatEntry() throws Exception {
        String json = SavedRecommendations.addTo(null, doc(1, "a"), 50);
        json = SavedRecommendations.addTo(json, doc(2, "b"), 50);
        List<AiRecommendationResponse> list = SavedRecommendations.parse(SavedRecommendations.removeFrom(json, 1));
        assertEquals(1, list.size());
        assertEquals(2, list.get(0).generatedAt);
    }

    @Test
    public void damagedDataGivesEmptyListOrSkipsBadEntries() {
        assertTrue(SavedRecommendations.parse(null).isEmpty());
        assertTrue(SavedRecommendations.parse("{broken").isEmpty());
        assertEquals(0, SavedRecommendations.parse("[{\"summary\":\"no status\"}]").size());
    }
}
