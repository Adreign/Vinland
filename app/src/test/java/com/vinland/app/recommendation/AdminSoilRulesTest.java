package com.vinland.app.recommendation;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class AdminSoilRulesTest {

    private static Map<String, Object> rule(String param, String condition, String... crops) {
        Map<String, Object> m = new HashMap<>();
        m.put("title", "t");
        m.put("parameter", param);
        m.put("condition", condition);
        m.put("status", "s");
        m.put("advice", "do this");
        m.put("applicableCrops", new ArrayList<>(Arrays.asList(crops)));
        return m;
    }

    @Test
    public void parsesParameterNames() {
        assertEquals(AdminSoilRules.Param.PH, AdminSoilRules.parseParam("Soil pH"));
        assertEquals(AdminSoilRules.Param.PHOSPHORUS, AdminSoilRules.parseParam("Phosphorus (P)"));
        assertEquals(AdminSoilRules.Param.POTASSIUM, AdminSoilRules.parseParam("Potassium (K)"));
        assertEquals(AdminSoilRules.Param.NITROGEN, AdminSoilRules.parseParam("Nitrogen (N)"));
        assertEquals(AdminSoilRules.Param.MOISTURE, AdminSoilRules.parseParam("Soil Moisture"));
        assertNull(AdminSoilRules.parseParam("Temperature"));
    }

    @Test
    public void conditions() {
        assertTrue(AdminSoilRules.conditionMatches("< 5.5", 5.0));
        assertFalse(AdminSoilRules.conditionMatches("< 5.5", 5.5));
        assertTrue(AdminSoilRules.conditionMatches("<=5.5", 5.5));
        assertTrue(AdminSoilRules.conditionMatches("> 80%", 85));
        assertTrue(AdminSoilRules.conditionMatches(">= 20 ppm", 20));
        assertTrue(AdminSoilRules.conditionMatches("5.5 - 6.5", 6.0));
        assertTrue(AdminSoilRules.conditionMatches("5.5 to 6.5", 6.5));
        assertFalse(AdminSoilRules.conditionMatches("5.5 - 6.5", 7.0));
        assertFalse(AdminSoilRules.conditionMatches("low", 1));
    }

    @Test
    public void badRulesAreSkipped() {
        assertNull(AdminSoilRules.fromMap(rule("Soil pH", "very low")));
        assertNull(AdminSoilRules.fromMap(rule("Temperature", "< 5")));
        assertNotNull(AdminSoilRules.fromMap(rule("Soil pH", "< 5.5")));
    }

    @Test
    public void matchesByCropAndReading() {
        List<AdminSoilRules.Rule> rules = new ArrayList<>();
        rules.add(AdminSoilRules.fromMap(rule("Soil pH", "< 5.5")));                 // all crops
        rules.add(AdminSoilRules.fromMap(rule("Nitrogen (N)", "< 20", "Tomato")));  // tomato only
        rules.add(AdminSoilRules.fromMap(rule("Potassium (K)", "> 200")));          // won't fire

        // moisture, ph, n, p, k
        assertEquals(2, AdminSoilRules.matching(rules, "tomato", 50, 5.0, 10, 20, 100).size());
        assertEquals(1, AdminSoilRules.matching(rules, "Pechay", 50, 5.0, 10, 20, 100).size());
        // General soil check (no crop): only all-crop rules
        assertEquals(1, AdminSoilRules.matching(rules, null, 50, 5.0, 10, 20, 100).size());
        assertEquals(0, AdminSoilRules.matching(rules, "Tomato", 50, 6.5, 30, 20, 100).size());
    }
}
