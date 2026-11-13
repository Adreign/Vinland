package com.vinland.app.planting;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import com.vinland.app.data.Crop;
import com.vinland.app.weather.WeatherForecast;

import org.junit.Test;

import java.time.LocalDate;
import java.util.List;

public class PlantingAdvisorTest {

    private static final LocalDate START = LocalDate.of(2026, 11, 1);

    private static Planting planting(String crop, int days) {
        return new Planting(crop, "🥬", days, START.toString());
    }

    private static Crop pechay() {
        Crop c = new Crop();
        c.setName("Pechay");
        c.setCategory("Leafy Vegetable");
        c.setMoistureMin(40);
        c.setMoistureMax(70);
        c.setPhMin(6.0);
        c.setPhMax(7.0);
        c.setNitrogenMin(20);
        c.setNitrogenMax(80);
        c.setPhosphorusMin(5);
        c.setPhosphorusMax(60);
        c.setPotassiumMin(15);
        c.setPotassiumMax(150);
        return c;
    }

    private static WeatherForecast.Day weather(double maxTemp, int rainChance) {
        return new WeatherForecast.Day(START, 1, maxTemp, 24, rainChance, 0);
    }

    private static boolean anyContains(List<String> tasks, String text) {
        for (String t : tasks) if (t.contains(text)) return true;
        return false;
    }

    @Test
    public void dayNumberAndHarvestDate() {
        PlantingAdvisor.Advice a = PlantingAdvisor.advise(planting("Pechay", 30), pechay(),
                START.plusDays(4), null, null);
        assertEquals(5, a.dayNumber);
        assertEquals(30, a.totalDays);
        assertEquals(26, a.daysLeft());
        assertEquals(START.plusDays(30), a.harvestDate);
    }

    @Test
    public void stagesFollowTheDays() {
        assertEquals(PlantingAdvisor.Stage.PLANTING_DAY, PlantingAdvisor.stageFor(1, 30, false));
        assertEquals(PlantingAdvisor.Stage.SPROUTING, PlantingAdvisor.stageFor(3, 30, false));
        assertEquals(PlantingAdvisor.Stage.SEEDLING, PlantingAdvisor.stageFor(8, 30, false));
        assertEquals(PlantingAdvisor.Stage.GROWING, PlantingAdvisor.stageFor(15, 30, false));
        assertEquals(PlantingAdvisor.Stage.MATURING, PlantingAdvisor.stageFor(25, 30, false));
        assertEquals(PlantingAdvisor.Stage.FLOWERING, PlantingAdvisor.stageFor(25, 30, true));
        assertEquals(PlantingAdvisor.Stage.HARVEST, PlantingAdvisor.stageFor(31, 30, false));
        assertEquals(PlantingAdvisor.Stage.LATE, PlantingAdvisor.stageFor(60, 30, false));
    }

    @Test
    public void rainMeansSkipWatering() {
        PlantingAdvisor.Advice a = PlantingAdvisor.advise(planting("Pechay", 30), pechay(),
                START.plusDays(10), null, weather(30, 80));
        assertTrue(a.tasks.get(0).contains("Skip watering"));
    }

    @Test
    public void hotDayMeansWaterTwice() {
        PlantingAdvisor.Advice a = PlantingAdvisor.advise(planting("Pechay", 30), pechay(),
                START.plusDays(10), null, weather(35, 10));
        assertTrue(a.tasks.get(0).contains("Hot day (35°C)"));
    }

    @Test
    public void drySoilBeatsTheWeather() {
        PlantingAdvisor.SoilReading soil = new PlantingAdvisor.SoilReading(25.0, 6.5, 40.0, 20.0, 50.0);
        PlantingAdvisor.Advice a = PlantingAdvisor.advise(planting("Pechay", 30), pechay(),
                START.plusDays(10), soil, weather(30, 80));
        assertTrue(a.tasks.get(0).contains("Soil is dry (25%"));
    }

    @Test
    public void lowPhAndNutrientsGetFixes() {
        PlantingAdvisor.SoilReading soil = new PlantingAdvisor.SoilReading(50.0, 5.2, 10.0, 2.0, 5.0);
        PlantingAdvisor.Advice a = PlantingAdvisor.advise(planting("Pechay", 30), pechay(),
                START.plusDays(10), soil, null);
        assertTrue(anyContains(a.tasks, "pH 5.2 is low"));
        assertTrue(anyContains(a.tasks, "Nitrogen is low"));
        assertTrue(anyContains(a.tasks, "Phosphorus is low"));
        assertTrue(anyContains(a.tasks, "Potassium is low"));
    }

    @Test
    public void goodSoilGetsNoFixes() {
        PlantingAdvisor.SoilReading soil = new PlantingAdvisor.SoilReading(50.0, 6.5, 40.0, 20.0, 50.0);
        PlantingAdvisor.Advice a = PlantingAdvisor.advise(planting("Pechay", 30), pechay(),
                START.plusDays(10), soil, null);
        assertFalse(anyContains(a.tasks, "is low"));
        assertFalse(anyContains(a.tasks, "is high"));
    }

    @Test
    public void tomatoIsFruiting() {
        assertTrue(PlantingAdvisor.isFruiting("tomato", null));
        assertFalse(PlantingAdvisor.isFruiting("pechay", pechay()));
    }

    @Test
    public void harvestDayTellsYouToHarvest() {
        PlantingAdvisor.Advice a = PlantingAdvisor.advise(planting("Pechay", 30), pechay(),
                START.plusDays(30), null, null);
        assertEquals(PlantingAdvisor.Stage.HARVEST, a.stage);
        assertTrue(anyContains(a.tasks, "ready to harvest"));
        assertEquals(100, a.progress);
    }
}
