package com.vinland.app.recommendation;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import com.vinland.app.data.Crop;

import org.junit.Test;

import java.util.Arrays;
import java.util.List;

public class CropScorerTest {

    private static Crop crop(String name, double phMin, double phMax, double mMin, double mMax) {
        return new Crop(name.toLowerCase(), name, "", "", "", "", phMin, phMax, mMin, mMax,
                5, 80, 5, 60, 15, 150, null);
    }

    @Test
    public void perfectSoilScores100() {
        CropScorer.Match m = CropScorer.score(crop("Tomato", 6.0, 6.8, 30, 70), 50, 6.5, 40, 30, 100);
        assertEquals(100, m.score);
        assertTrue(m.issues.isEmpty());
    }

    @Test
    public void outOfRangeLowersScoreAndReportsIssue() {
        CropScorer.Match m = CropScorer.score(crop("Tomato", 6.0, 6.8, 30, 70), 50, 5.5, 40, 30, 100);
        assertTrue(m.score < 100 && m.score > 50);
        assertEquals(1, m.issues.size());
        assertTrue(m.issues.get(0).startsWith("pH 5.5 is below"));
    }

    @Test
    public void unsetRangesAreIgnored() {
        assertEquals(-1.0, CropScorer.paramScore(10, 0, 0, 1), 0.0);
    }

    @Test
    public void rankOrdersBestFirst() {
        List<CropScorer.Match> ranked = CropScorer.rank(Arrays.asList(
                crop("Acid Lover", 4.5, 5.5, 30, 70),
                crop("Neutral", 6.0, 7.0, 30, 70)), 50, 6.5, 40, 30, 100);
        assertEquals("Neutral", ranked.get(0).crop.getName());
    }
}
