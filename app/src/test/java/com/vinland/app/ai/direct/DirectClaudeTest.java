package com.vinland.app.ai.direct;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class DirectClaudeTest {

    private static final List<String> CROPS = Arrays.asList("Pechay", "Tomato");

    @Test
    public void validatorKeepsGoodItemsDropsInventedCropsAndSorts() {
        String json = "{\"status\":\"ok\",\"soil_status\":\"fair\",\"summary\":\"Soil is a bit acidic.\","
                + "\"recommendations\":["
                + "{\"title\":\"Water less\",\"reason\":\"Moisture 80% is high.\",\"priority\":\"low\",\"suggested_action\":\"Skip a day.\",\"category\":\"watering\",\"crop\":null},"
                + "{\"title\":\"Plant mango\",\"reason\":\"x\",\"priority\":\"high\",\"suggested_action\":\"y\",\"category\":\"crop\",\"crop\":\"Mango\"},"
                + "{\"title\":\"Add lime\",\"reason\":\"pH 5.2 is below pechay's 6-7.\",\"priority\":\"high\",\"suggested_action\":\"1 tbsp per pot.\",\"category\":\"soil\",\"crop\":\"pechay\"}],"
                + "\"missing_data\":[]}";
        RecommendationValidator.Result r = RecommendationValidator.validate(json, CROPS);
        assertNotNull(r);
        assertEquals(2, r.recommendations.size());
        assertEquals("Add lime", r.recommendations.get(0).get("title"));
        assertEquals("Pechay", r.recommendations.get(0).get("crop")); // database spelling
    }

    @Test
    public void validatorRejectsBrokenOrEmptyAnswers() {
        assertNull(RecommendationValidator.validate(null, CROPS));
        assertNull(RecommendationValidator.validate("{not json", CROPS));
        assertNull(RecommendationValidator.validate(
                "{\"status\":\"ok\",\"soil_status\":\"fair\",\"summary\":\"s\",\"recommendations\":[],\"missing_data\":[]}", CROPS));
    }

    @Test
    public void promptUsesNewestReadingAndScoresCrops() {
        long now = 1_793_000_000_000L;
        GardenPrompt.RawData raw = new GardenPrompt.RawData();
        raw.user = new HashMap<>();
        raw.user.put("barangay", "Bagong <Silangan>");
        Map<String, Object> log = new HashMap<>();
        log.put("ph", 5.2);
        log.put("moisture", 60);
        log.put("timestamp", now - 1000);
        raw.soilLogs.add(log);
        Map<String, Object> crop = new HashMap<>();
        crop.put("name", "Pechay");
        crop.put("phMin", 6.0);
        crop.put("phMax", 7.0);
        raw.crops.add(crop);

        GardenPrompt.Built b = GardenPrompt.build(raw, now);
        assertTrue(b.hasReading);
        assertEquals(Arrays.asList("Pechay"), b.cropNames);
        assertTrue(b.userBlock.contains("\"source\": \"saved_log\""));
        assertTrue(b.userBlock.contains("pH 5.2 below 6-7"));
        assertFalse(b.userBlock.contains("<Silangan>")); // angle brackets stripped
    }

    @Test
    public void noReadingMeansInsufficientData() {
        GardenPrompt.RawData raw = new GardenPrompt.RawData();
        assertFalse(GardenPrompt.build(raw, 1_793_000_000_000L).hasReading);
    }
}
