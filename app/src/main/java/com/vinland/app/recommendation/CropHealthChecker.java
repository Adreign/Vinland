package com.vinland.app.recommendation;

import com.vinland.app.data.Crop;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * "Monitor Crops / Plants" mode: compares one soil reading with a crop's ideal ranges
 * and says, per parameter, whether it is low, OK or high, plus what to do about it.
 */
public final class CropHealthChecker {

    private CropHealthChecker() {
    }

    public enum Level { LOW, OK, HIGH, UNKNOWN }

    /** One soil parameter checked against the crop's range. */
    public static final class ParamCheck {
        public final String label;
        public final String unit;
        public final double value;
        public final double min;
        public final double max;
        public final Level level;
        /** What to do; empty when the value is fine. */
        public final String tip;

        ParamCheck(String label, String unit, double value, double min, double max, Level level, String tip) {
            this.label = label;
            this.unit = unit;
            this.value = value;
            this.min = min;
            this.max = max;
            this.level = level;
            this.tip = tip;
        }

        public String rangeText() {
            if (level == Level.UNKNOWN) return "no range set";
            return fmt(min) + "–" + fmt(max) + unit;
        }

        public String valueText() {
            return fmt(value) + unit;
        }
    }

    /** The full health check of one crop. */
    public static final class Result {
        public final Crop crop;
        /** 0-100, same scale as the New Soil crop match. */
        public final int score;
        public final List<ParamCheck> checks;

        Result(Crop crop, int score, List<ParamCheck> checks) {
            this.crop = crop;
            this.score = score;
            this.checks = checks;
        }

        /** "Healthy", "Fair" or "Needs attention". */
        public String statusLabel() {
            if (score >= 80) return "Healthy";
            if (score >= 60) return "Fair";
            return "Needs attention";
        }

        /** Parameters outside the crop's range. */
        public List<ParamCheck> problems() {
            List<ParamCheck> list = new ArrayList<>();
            for (ParamCheck c : checks) {
                if (c.level == Level.LOW || c.level == Level.HIGH) list.add(c);
            }
            return list;
        }
    }

    public static Result check(Crop crop, double moisture, double ph, double n, double p, double k) {
        List<ParamCheck> checks = new ArrayList<>();
        checks.add(param("Moisture", "%", moisture, crop.getMoistureMin(), crop.getMoistureMax(),
                "Water more often; mulch to keep the soil moist.",
                "Water less and make sure the pot or bed drains well."));
        checks.add(param("pH", "", ph, crop.getPhMin(), crop.getPhMax(),
                "Soil is too acidic: mix in agricultural lime or wood ash.",
                "Soil is too alkaline: add compost or rice hull/pine bark mulch."));
        checks.add(param("Nitrogen", " ppm", n, crop.getNitrogenMin(), crop.getNitrogenMax(),
                "Add vermicompost or fermented plant juice (FPJ).",
                "Hold off on nitrogen fertilizer for now."));
        checks.add(param("Phosphorus", " ppm", p, crop.getPhosphorusMin(), crop.getPhosphorusMax(),
                "Add bone meal or fermented fruit juice (FFJ).",
                "Skip phosphorus fertilizer until levels drop."));
        checks.add(param("Potassium", " ppm", k, crop.getPotassiumMin(), crop.getPotassiumMax(),
                "Add banana peel tea or wood ash.",
                "Skip potassium fertilizer until levels drop."));

        int score = CropScorer.score(crop, moisture, ph, n, p, k).score;
        return new Result(crop, score, checks);
    }

    private static ParamCheck param(String label, String unit, double value, double min, double max,
                                    String lowTip, String highTip) {
        if (min <= 0 && max <= 0) {
            return new ParamCheck(label, unit, value, min, max, Level.UNKNOWN, "");
        }
        double lo = Math.min(min, max);
        double hi = Math.max(min, max);
        if (value < lo) return new ParamCheck(label, unit, value, lo, hi, Level.LOW, lowTip);
        if (value > hi) return new ParamCheck(label, unit, value, lo, hi, Level.HIGH, highTip);
        return new ParamCheck(label, unit, value, lo, hi, Level.OK, "");
    }

    static String fmt(double d) {
        return d == Math.rint(d) ? String.valueOf((long) d) : String.format(Locale.US, "%.1f", d);
    }
}
