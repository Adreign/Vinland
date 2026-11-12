package com.vinland.app.calendar;

import com.vinland.app.data.Crop;

import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Works out, for one crop, which months are for planting, growing and harvesting.
 *
 * The data comes from the crop's Firestore record (plantingMonths + daysToHarvest).
 * If a record doesn't have it yet, a built-in default for common Quezon City crops is used.
 * Months here are 1 = January ... 12 = December.
 */
public class PlantingSchedule {

    /** Built-in fallback: {daysToHarvest, plantingMonth, plantingMonth, ...}. Approximate values. */
    private static final Map<String, int[]> DEFAULTS = new HashMap<>();
    static {
        // Dry, cooler months (Oct-Jan) are best for tomato and pepper; harvest counted from transplanting.
        DEFAULTS.put("tomato",       new int[]{70, 10, 11, 12, 1});
        DEFAULTS.put("green pepper", new int[]{75, 10, 11, 12, 1});
        DEFAULTS.put("eggplant",     new int[]{75, 10, 11, 12, 1, 5, 6});
        DEFAULTS.put("lettuce",      new int[]{45, 10, 11, 12, 1});
        // Leafy greens and okra grow nearly year-round in QC.
        DEFAULTS.put("pechay",       new int[]{30, 1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12});
        DEFAULTS.put("kangkong",     new int[]{30, 1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12});
        DEFAULTS.put("okra",         new int[]{50, 2, 3, 4, 5, 6, 7});
    }

    private final boolean[] plant = new boolean[13];
    private final boolean[] growing = new boolean[13];
    private final boolean[] harvest = new boolean[13];
    private final int daysToHarvest;
    private final boolean hasData;

    public PlantingSchedule(Crop crop) {
        List<Integer> months = crop.getPlantingMonths();
        int days = crop.getDaysToHarvest();

        if (months == null || months.isEmpty() || days <= 0) {
            int[] d = DEFAULTS.get(crop.getName() == null ? "" : crop.getName().trim().toLowerCase(Locale.ROOT));
            if (d != null) {
                days = d[0];
                Integer[] m = new Integer[d.length - 1];
                for (int i = 1; i < d.length; i++) m[i - 1] = d[i];
                months = Arrays.asList(m);
            }
        }

        daysToHarvest = days;
        hasData = months != null && !months.isEmpty() && days > 0;
        if (!hasData) return;

        // Number of months from planting until harvest (at least 1).
        int monthsToHarvest = Math.max(1, Math.round(days / 30.4f));
        for (Integer p : months) {
            if (p == null || p < 1 || p > 12) continue;
            plant[p] = true;
            for (int k = 1; k < monthsToHarvest; k++) growing[wrap(p + k)] = true;
            harvest[wrap(p + monthsToHarvest)] = true;
        }
    }

    private static int wrap(int month) {
        return ((month - 1) % 12) + 1;
    }

    public boolean hasData() { return hasData; }
    public int getDaysToHarvest() { return daysToHarvest; }
    public boolean isPlantMonth(int month) { return hasData && plant[month]; }
    public boolean isGrowingMonth(int month) { return hasData && growing[month]; }
    public boolean isHarvestMonth(int month) { return hasData && harvest[month]; }

    /** The next planting month after {@code fromMonth} (wrapping around the year), or -1 if none. */
    public int nextPlantMonthAfter(int fromMonth) {
        for (int k = 1; k <= 12; k++) {
            int m = wrap(fromMonth + k);
            if (plant[m]) return m;
        }
        return -1;
    }

    /** The month a crop planted in {@code month} should be ready. */
    public int harvestMonthFor(int month) {
        return wrap(month + Math.max(1, Math.round(daysToHarvest / 30.4f)));
    }
}
