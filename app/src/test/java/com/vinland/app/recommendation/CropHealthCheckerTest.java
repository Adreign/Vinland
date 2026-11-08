package com.vinland.app.recommendation;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import com.vinland.app.data.Crop;

import org.junit.Test;

public class CropHealthCheckerTest {

    // pH 6.0–6.8, moisture 60–80%, N 20–40, P 15–30, K 100–150
    private final Crop tomato = new Crop("tomato", "Tomato", "", "Fruit", "🍅", "",
            6.0, 6.8, 60, 80, 20, 40, 15, 30, 100, 150, null);

    @Test
    public void allInRange_isHealthyWithNoProblems() {
        CropHealthChecker.Result r = CropHealthChecker.check(tomato, 70, 6.5, 30, 20, 120);
        assertEquals(100, r.score);
        assertEquals("Healthy", r.statusLabel());
        assertTrue(r.problems().isEmpty());
        for (CropHealthChecker.ParamCheck c : r.checks) {
            assertEquals(CropHealthChecker.Level.OK, c.level);
            assertTrue(c.tip.isEmpty());
        }
    }

    @Test
    public void lowAndHighValues_areFlaggedWithTips() {
        CropHealthChecker.Result r = CropHealthChecker.check(tomato, 30, 7.9, 5, 20, 120);
        assertEquals(3, r.problems().size());
        assertEquals(CropHealthChecker.Level.LOW, r.checks.get(0).level);  // moisture
        assertEquals(CropHealthChecker.Level.HIGH, r.checks.get(1).level); // pH
        assertEquals(CropHealthChecker.Level.LOW, r.checks.get(2).level);  // nitrogen
        assertFalse(r.checks.get(0).tip.isEmpty());
        assertTrue(r.score < 80);
    }

    @Test
    public void cropWithoutRange_isUnknownNotAProblem() {
        Crop noData = new Crop("x", "Mystery", "", "", null, "", 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, null);
        CropHealthChecker.Result r = CropHealthChecker.check(noData, 50, 6, 10, 10, 10);
        assertTrue(r.problems().isEmpty());
        assertEquals(CropHealthChecker.Level.UNKNOWN, r.checks.get(0).level);
        assertEquals("no range set", r.checks.get(0).rangeText());
    }

    @Test
    public void rangeText_formatsNumbers() {
        CropHealthChecker.Result r = CropHealthChecker.check(tomato, 70, 6.5, 30, 20, 120);
        assertEquals("6–6.8", r.checks.get(1).rangeText());
        assertEquals("60–80%", r.checks.get(0).rangeText());
    }
}
