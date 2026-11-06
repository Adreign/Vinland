package com.vinland.app.ai;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** Builds maps shaped like the Cloud Function response. */
final class TestData {
    private TestData() {
    }

    static Map<String, Object> response(String status, int recCount) {
        Map<String, Object> m = new HashMap<>();
        m.put("status", status);
        m.put("cached", false);
        m.put("generatedAt", 1_762_400_000_000L);
        m.put("model", "claude-haiku-5-5");
        m.put("soilStatus", "action_needed");
        m.put("summary", "Soil is acidic.");
        List<Object> recs = new ArrayList<>();
        for (int i = 0; i < recCount; i++) {
            Map<String, Object> r = new HashMap<>();
            r.put("title", "Raise pH " + i);
            r.put("reason", "pH 5.2 is below 6-7.");
            r.put("priority", "high");
            r.put("suggested_action", "Add 1 tbsp lime per pot.");
            r.put("category", "soil");
            r.put("crop", i == 0 ? "Pechay" : null);
            recs.add(r);
        }
        m.put("recommendations", recs);
        m.put("missingData", Arrays.asList("No harvest history"));
        m.put("disclaimer", "AI-generated.");
        Map<String, Object> quota = new HashMap<>();
        quota.put("remainingToday", 7); // Firebase may decode numbers as Integer
        m.put("quota", quota);
        return m;
    }

    static Map<String, Object> details(String reason, Integer retryAfter) {
        Map<String, Object> d = new HashMap<>();
        d.put("reason", reason);
        if (retryAfter != null) d.put("retryAfterSeconds", retryAfter);
        return d;
    }
}
