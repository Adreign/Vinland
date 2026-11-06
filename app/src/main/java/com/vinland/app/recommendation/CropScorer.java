package com.vinland.app.recommendation;

import com.vinland.app.data.Crop;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Locale;

/**
 * Deterministic crop-vs-soil scoring. Runs instantly and offline, and its scores are
 * handed to the LLM as a grounded starting point (and used as the fallback ranking).
 */
public final class CropScorer {

    private CropScorer() {
    }

    public static final class Match {
        public final Crop crop;
        /** 0-100 suitability. */
        public final int score;
        /** Human-readable list of out-of-range parameters. */
        public final List<String> issues;

        Match(Crop crop, int score, List<String> issues) {
            this.crop = crop;
            this.score = score;
            this.issues = issues;
        }
    }

    /** Returns all crops ranked best-first. */
    public static List<Match> rank(Collection<Crop> crops, double moisture, double ph,
                                   double n, double p, double k) {
        List<Match> matches = new ArrayList<>();
        for (Crop crop : crops) {
            matches.add(score(crop, moisture, ph, n, p, k));
        }
        matches.sort((a, b) -> Integer.compare(b.score, a.score));
        return matches;
    }

    public static Match score(Crop crop, double moisture, double ph, double n, double p, double k) {
        List<String> issues = new ArrayList<>();
        double total = 0;
        int counted = 0;

        double[] s = {
                param("pH", ph, crop.getPhMin(), crop.getPhMax(), 1.0, "", issues),
                param("Moisture", moisture, crop.getMoistureMin(), crop.getMoistureMax(), 20.0, "%", issues),
                param("Nitrogen", n, crop.getNitrogenMin(), crop.getNitrogenMax(), -1, " ppm", issues),
                param("Phosphorus", p, crop.getPhosphorusMin(), crop.getPhosphorusMax(), -1, " ppm", issues),
                param("Potassium", k, crop.getPotassiumMin(), crop.getPotassiumMax(), -1, " ppm", issues),
        };
        for (double v : s) {
            if (v >= 0) {
                total += v;
                counted++;
            }
        }
        int score = counted == 0 ? 50 : (int) Math.round(total / counted * 100);
        return new Match(crop, score, issues);
    }

    /**
     * 1.0 inside [min,max], decaying linearly to 0 over {@code tolerance} outside it.
     * Returns -1 when the crop has no range for this parameter (both bounds unset).
     * A negative tolerance means "half the range width, at least 10".
     */
    static double paramScore(double value, double min, double max, double tolerance) {
        if (min <= 0 && max <= 0) return -1;
        if (max < min) {
            double t = min;
            min = max;
            max = t;
        }
        if (tolerance < 0) tolerance = Math.max(10.0, (max - min) * 0.5);
        if (value >= min && value <= max) return 1.0;
        double dist = value < min ? min - value : value - max;
        return Math.max(0.0, 1.0 - dist / tolerance);
    }

    private static double param(String label, double value, double min, double max,
                                double tolerance, String unit, List<String> issues) {
        double score = paramScore(value, min, max, tolerance);
        if (score >= 0 && score < 1.0) {
            String dir = value < Math.min(min, max) ? "below" : "above";
            issues.add(String.format(Locale.US, "%s %s%s is %s the %s–%s%s range",
                    label, fmt(value), unit, dir, fmt(Math.min(min, max)), fmt(Math.max(min, max)), unit));
        }
        return score;
    }

    private static String fmt(double d) {
        return d == Math.rint(d) ? String.valueOf((long) d) : String.format(Locale.US, "%.1f", d);
    }
}
