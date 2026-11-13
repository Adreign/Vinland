package com.vinland.app.planting;

import com.vinland.app.data.Crop;
import com.vinland.app.weather.WeatherForecast;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;

/**
 * Builds today's to-do list for one planting, using fixed rules (no AI, no cost):
 *  1. the growth stage, worked out from the day number and the crop's days to harvest,
 *  2. today's weather (rain or heat changes the watering advice),
 *  3. the latest soil reading compared with the crop's ideal ranges.
 *
 * No Android classes, so it can be unit tested.
 */
public final class PlantingAdvisor {

    private PlantingAdvisor() {
    }

    /** Hot enough to change the watering advice, in °C. */
    static final double HOT_DAY_C = 33;

    /** Days after the expected harvest before we call it "past harvest time". */
    static final int LATE_HARVEST_DAYS = 21;

    /** Crops that flower and fruit (tomato, pepper...) get fruiting-stage tips. */
    private static final List<String> FRUITING = Arrays.asList(
            "tomato", "pepper", "eggplant", "okra", "ampalaya", "squash", "cucumber",
            "sitaw", "string bean", "chili", "siling", "patola", "upo", "melon", "beans");

    public enum Stage {
        PLANTING_DAY("Planting day", "🌱"),
        SPROUTING("Sprouting", "🌰"),
        SEEDLING("Seedling", "🌱"),
        GROWING("Growing", "🌿"),
        FLOWERING("Flowering & fruiting", "🌼"),
        MATURING("Maturing", "🥬"),
        HARVEST("Ready to harvest", "🧺"),
        LATE("Past harvest time", "⏰");

        public final String label;
        public final String emoji;

        Stage(String label, String emoji) {
            this.label = label;
            this.emoji = emoji;
        }
    }

    /** A soil reading. Any value may be null. */
    public static class SoilReading {
        public final Double moisture, ph, nitrogen, phosphorus, potassium;

        public SoilReading(Double moisture, Double ph, Double nitrogen, Double phosphorus, Double potassium) {
            this.moisture = moisture;
            this.ph = ph;
            this.nitrogen = nitrogen;
            this.phosphorus = phosphorus;
            this.potassium = potassium;
        }
    }

    /** Today's advice for one planting. */
    public static class Advice {
        public final int dayNumber;
        public final int totalDays;
        public final Stage stage;
        /** 0-100 */
        public final int progress;
        public final LocalDate harvestDate;
        public final List<String> tasks;

        Advice(int dayNumber, int totalDays, Stage stage, int progress, LocalDate harvestDate, List<String> tasks) {
            this.dayNumber = dayNumber;
            this.totalDays = totalDays;
            this.stage = stage;
            this.progress = progress;
            this.harvestDate = harvestDate;
            this.tasks = tasks;
        }

        public int daysLeft() {
            return Math.max(0, totalDays - dayNumber + 1);
        }
    }

    /**
     * @param crop    the crop's record (ranges + monthly tips); may be null
     * @param soil    latest recent soil reading; null when there is none
     * @param weather today's forecast; null when it could not be loaded
     */
    public static Advice advise(Planting planting, Crop crop, LocalDate today,
                                SoilReading soil, WeatherForecast.Day weather) {
        LocalDate start = LocalDate.parse(planting.getStartDate());
        int day = (int) ChronoUnit.DAYS.between(start, today) + 1;
        if (day < 1) day = 1; // a start date in the future counts as day 1
        int total = Math.max(1, planting.getDaysToHarvest());
        String name = planting.getCropName() == null ? "your crop" : planting.getCropName();
        String lower = name.toLowerCase(Locale.ROOT);
        boolean fruiting = isFruiting(lower, crop);

        Stage stage = stageFor(day, total, fruiting);
        int progress = Math.min(100, Math.round(100f * (day - 1) / total));

        List<String> tasks = new ArrayList<>();

        // 1. Watering: soil reading first, then weather, then the stage's normal routine.
        tasks.add(wateringTask(stage, lower, crop, soil, weather));

        // 2. What this stage needs.
        addStageTasks(tasks, stage, day, total, lower, fruiting);

        // 3. Soil fixes (pH and nutrients) from the latest reading.
        if (soil != null && crop != null) {
            addSoilFixes(tasks, lower, crop, soil);
        } else if (soil == null && stage != Stage.HARVEST && stage != Stage.LATE && day % 7 == 1) {
            tasks.add("No recent soil reading. Check your soil on the Soil Monitoring screen for more exact advice.");
        }

        // 4. The admin's tip for this month, if there is one.
        String monthTip = monthlyTip(crop, today.getMonthValue());
        if (monthTip != null && stage != Stage.LATE) tasks.add("This month: " + monthTip);

        return new Advice(day, total, stage, progress, start.plusDays(total), tasks);
    }

    static Stage stageFor(int day, int total, boolean fruiting) {
        if (day == 1) return Stage.PLANTING_DAY;
        if (day > total + LATE_HARVEST_DAYS) return Stage.LATE;
        if (day > total) return Stage.HARVEST;
        double f = (day - 1) / (double) total;
        if (f < 0.15) return Stage.SPROUTING;
        if (f < 0.35) return Stage.SEEDLING;
        if (f < 0.70) return Stage.GROWING;
        if (f < 0.95) return fruiting ? Stage.FLOWERING : Stage.MATURING;
        return Stage.HARVEST;
    }

    static boolean isFruiting(String lowerName, Crop crop) {
        if (crop != null && crop.getCategory() != null
                && crop.getCategory().toLowerCase(Locale.ROOT).contains("fruit")) {
            return true;
        }
        for (String f : FRUITING) {
            if (lowerName.contains(f)) return true;
        }
        return false;
    }

    private static String wateringTask(Stage stage, String name, Crop crop, SoilReading soil, WeatherForecast.Day w) {
        boolean rainy = w != null && w.isRainy();
        boolean hot = w != null && w.maxTemp >= HOT_DAY_C;

        if (soil != null && soil.moisture != null && crop != null && crop.getMoistureMax() > 0) {
            double m = soil.moisture;
            String range = num(crop.getMoistureMin()) + "–" + num(crop.getMoistureMax()) + "%";
            if (m > crop.getMoistureMax()) {
                return "💧 Soil is wet (" + num(m) + "%, ideal " + range + "). Skip watering today and make sure pots drain well.";
            }
            if (m < crop.getMoistureMin()) {
                return "💧 Soil is dry (" + num(m) + "%, ideal " + range + "). Water " + name + " now at the base"
                        + (rainy ? ", then let the expected rain do the rest." : ".");
            }
        }
        if (rainy) {
            return "🌧️ Rain expected today (" + w.rainChance + "% chance). Skip watering and check that water doesn't pool in the pots.";
        }
        if (hot) {
            return "🔥 Hot day (" + Math.round(w.maxTemp) + "°C). Water early in the morning and again late in the afternoon"
                    + (stage == Stage.SPROUTING || stage == Stage.SEEDLING || stage == Stage.PLANTING_DAY
                    ? ", and give the young plants some afternoon shade." : ".");
        }
        switch (stage) {
            case PLANTING_DAY:
                return "💧 Water gently right after planting so the soil is moist but not soggy.";
            case SPROUTING:
                return "💧 Keep the soil evenly moist. Water lightly in the morning; mist the surface if it dries out.";
            case HARVEST:
            case LATE:
                return "💧 Water lightly in the morning. Less water now keeps the harvest firm and tasty.";
            default:
                return "💧 Water in the morning at the base of the plant, not on the leaves.";
        }
    }

    private static void addStageTasks(List<String> tasks, Stage stage, int day, int total, String name, boolean fruiting) {
        boolean stageStart = stageFor(day - 1, total, fruiting) != stage;
        switch (stage) {
            case PLANTING_DAY:
                tasks.add("🌱 Sow seeds about 1 cm deep, or transplant seedlings, in loose soil mixed with compost.");
                tasks.add("☀️ Place " + name + " where it gets at least " + (fruiting ? "6" : "4") + " hours of sun a day.");
                tasks.add("🏷️ Label the pot with today's date so you can track its growth.");
                break;
            case SPROUTING:
                tasks.add("🛡️ Protect the soil from heavy rain and birds with a light cover or net.");
                if (stageStart) tasks.add("👀 Look for the first sprouts over the next few days.");
                break;
            case SEEDLING:
                if (stageStart) tasks.add("✂️ Thin out crowded seedlings. Keep the strongest ones about 10–15 cm apart.");
                if (day % 3 == 0) tasks.add("🐛 Check young leaves for holes or insects. Remove pests by hand.");
                break;
            case GROWING:
                if (stageStart && fruiting) tasks.add("🪵 Add a stake or trellis now so the plant has support as it grows.");
                if (day % 14 == 0 || stageStart) tasks.add("🧺 Feed with a handful of vermicompost or compost per pot.");
                if (day % 7 == 0) tasks.add("🌾 Pull weeds around the plant and loosen the topsoil.");
                if (day % 3 == 0) tasks.add("🐛 Check under the leaves for aphids, whiteflies or caterpillars.");
                break;
            case FLOWERING:
                tasks.add("🌼 Water deeply and on a steady schedule. Uneven watering makes flowers and fruit drop.");
                if (day % 7 == 0 || stageStart) tasks.add("🍌 Give banana peel tea (soak peels in water for 2–3 days) for potassium to help fruiting.");
                if (day % 3 == 0) tasks.add("🐛 Check flowers and young fruit for pests and remove damaged fruit.");
                break;
            case MATURING:
                tasks.add("🥬 Leaves are filling out. You can start picking the larger outer leaves.");
                if (day % 3 == 0) tasks.add("🐛 Check leaves for holes or insects before they spread.");
                break;
            case HARVEST:
                tasks.add("🧺 " + capitalize(name) + " is ready to harvest. Pick in the early morning when it's cool.");
                tasks.add("✅ Tap Harvested when you're done so it's saved in your harvest log.");
                break;
            case LATE:
                tasks.add("⏰ It's " + (day - total) + " days past the expected harvest. Harvest now, or tap Stop if this planting didn't work out.");
                break;
        }
    }

    private static void addSoilFixes(List<String> tasks, String name, Crop crop, SoilReading s) {
        if (s.ph != null && crop.getPhMax() > 0) {
            String range = num(crop.getPhMin()) + "–" + num(crop.getPhMax());
            if (s.ph < crop.getPhMin()) {
                tasks.add("⚗️ pH " + num(s.ph) + " is low for " + name + " (ideal " + range
                        + "). Mix 1 tablespoon of agricultural lime or crushed eggshells into each pot.");
            } else if (s.ph > crop.getPhMax()) {
                tasks.add("⚗️ pH " + num(s.ph) + " is high for " + name + " (ideal " + range
                        + "). Add compost or coco peat, and avoid wood ash for now.");
            }
        }
        if (s.nitrogen != null && crop.getNitrogenMax() > 0 && s.nitrogen < crop.getNitrogenMin()) {
            tasks.add("🍃 Nitrogen is low (" + num(s.nitrogen) + "). Add a handful of vermicompost or water with fermented plant juice.");
        }
        if (s.phosphorus != null && crop.getPhosphorusMax() > 0 && s.phosphorus < crop.getPhosphorusMin()) {
            tasks.add("🌸 Phosphorus is low (" + num(s.phosphorus) + "). Mix in bone meal or chicken manure compost.");
        }
        if (s.potassium != null && crop.getPotassiumMax() > 0 && s.potassium < crop.getPotassiumMin()) {
            tasks.add("🍌 Potassium is low (" + num(s.potassium) + "). Water with banana peel tea or add a pinch of wood ash.");
        }
    }

    private static String monthlyTip(Crop crop, int month) {
        if (crop == null || crop.getMonthlyTips() == null || crop.getMonthlyTips().size() < month) return null;
        String tip = crop.getMonthlyTips().get(month - 1);
        return tip == null || tip.trim().isEmpty() ? null : tip.trim();
    }

    private static String num(double v) {
        return v == Math.rint(v) ? String.valueOf((long) v) : String.format(Locale.US, "%.1f", v);
    }

    private static String capitalize(String s) {
        return s.isEmpty() ? s : Character.toUpperCase(s.charAt(0)) + s.substring(1);
    }
}
