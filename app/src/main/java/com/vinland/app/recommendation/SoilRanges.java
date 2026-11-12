package com.vinland.app.recommendation;

import java.util.Map;

/**
 * General "good" soil ranges used by the Soil Monitoring screen when no particular
 * crop is involved. The admin sets them on the web admin "Soil Conditions" page,
 * which saves them in Firestore collection {@code soil_condition_config}
 * (documents {@code npk}, {@code ph}, {@code moisture}). Below the range = low,
 * above = high. Missing or broken values fall back to the defaults.
 */
public final class SoilRanges {

    public final double moistureMin, moistureMax;
    public final double phMin, phMax;
    public final double nMin, nMax;
    public final double pMin, pMax;
    public final double kMin, kMax;

    public SoilRanges(double moistureMin, double moistureMax, double phMin, double phMax,
                      double nMin, double nMax, double pMin, double pMax, double kMin, double kMax) {
        this.moistureMin = moistureMin;
        this.moistureMax = moistureMax;
        this.phMin = phMin;
        this.phMax = phMax;
        this.nMin = nMin;
        this.nMax = nMax;
        this.pMin = pMin;
        this.pMax = pMax;
        this.kMin = kMin;
        this.kMax = kMax;
    }

    /** The limits the app used before the admin could change them. */
    public static SoilRanges defaults() {
        return new SoilRanges(35, 80, 5.5, 7.5, 5, 80, 5, 60, 15, 150);
    }

    /** Builds ranges from the three Firestore documents; any may be null. */
    public static SoilRanges from(Map<String, Object> npk, Map<String, Object> ph, Map<String, Object> moisture) {
        SoilRanges d = defaults();
        double[] m = pair(moisture, "optimalMoistureMin", "optimalMoistureMax", d.moistureMin, d.moistureMax);
        double[] p = pair(ph, "optimalPhMin", "optimalPhMax", d.phMin, d.phMax);
        double[] n = pair(npk, "nitrogenOptimalMin", "nitrogenOptimalMax", d.nMin, d.nMax);
        double[] ph2 = pair(npk, "phosphorusOptimalMin", "phosphorusOptimalMax", d.pMin, d.pMax);
        double[] k = pair(npk, "potassiumOptimalMin", "potassiumOptimalMax", d.kMin, d.kMax);
        return new SoilRanges(m[0], m[1], p[0], p[1], n[0], n[1], ph2[0], ph2[1], k[0], k[1]);
    }

    /** Reads a min/max pair; uses the defaults unless both are valid numbers with min < max. */
    private static double[] pair(Map<String, Object> doc, String minKey, String maxKey, double defMin, double defMax) {
        if (doc == null) return new double[]{defMin, defMax};
        Double min = num(doc.get(minKey));
        Double max = num(doc.get(maxKey));
        if (min == null || max == null || min < 0 || min >= max) return new double[]{defMin, defMax};
        return new double[]{min, max};
    }

    private static Double num(Object o) {
        if (o instanceof Number) {
            double d = ((Number) o).doubleValue();
            return Double.isNaN(d) || Double.isInfinite(d) ? null : d;
        }
        if (o instanceof String) {
            try {
                return Double.parseDouble(((String) o).trim());
            } catch (NumberFormatException e) {
                return null;
            }
        }
        return null;
    }

    public boolean moistureOk(double v) { return v >= moistureMin && v <= moistureMax; }
    public boolean phOk(double v) { return v >= phMin && v <= phMax; }
    public boolean nOk(double v) { return v >= nMin && v <= nMax; }
    public boolean pOk(double v) { return v >= pMin && v <= pMax; }
    public boolean kOk(double v) { return v >= kMin && v <= kMax; }
}
