package com.vinland.app.recommendation;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.util.HashMap;
import java.util.Map;

public class SoilRangesTest {

    @Test
    public void missingDocsUseDefaults() {
        SoilRanges r = SoilRanges.from(null, null, null);
        assertEquals(35, r.moistureMin, 0);
        assertEquals(7.5, r.phMax, 0);
        assertEquals(150, r.kMax, 0);
    }

    @Test
    public void readsAdminValues() {
        Map<String, Object> npk = new HashMap<>();
        npk.put("nitrogenOptimalMin", 21L);
        npk.put("nitrogenOptimalMax", 50.0);
        Map<String, Object> ph = new HashMap<>();
        ph.put("optimalPhMin", 6.0);
        ph.put("optimalPhMax", "7.0");

        SoilRanges r = SoilRanges.from(npk, ph, null);
        assertEquals(21, r.nMin, 0);
        assertEquals(50, r.nMax, 0);
        assertEquals(7.0, r.phMax, 0);
        // phosphorus not set -> default
        assertEquals(60, r.pMax, 0);
        assertTrue(r.nOk(30));
        assertFalse(r.nOk(10));
        assertFalse(r.phOk(5.8));
    }

    @Test
    public void brokenPairFallsBackToDefaults() {
        Map<String, Object> m = new HashMap<>();
        m.put("optimalMoistureMin", 90.0);   // min above max
        m.put("optimalMoistureMax", 40.0);
        SoilRanges r = SoilRanges.from(null, null, m);
        assertEquals(35, r.moistureMin, 0);
        assertEquals(80, r.moistureMax, 0);
    }
}
