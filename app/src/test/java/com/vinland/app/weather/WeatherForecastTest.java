package com.vinland.app.weather;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import org.json.JSONObject;
import org.junit.Test;

import java.time.LocalDate;
import java.util.List;

public class WeatherForecastTest {

    private static final LocalDate TODAY = LocalDate.of(2026, 11, 9); // a Monday

    private static JSONObject sample(String codes, String chances) throws Exception {
        return new JSONObject("{\"daily\":{"
                + "\"time\":[\"2026-11-09\",\"2026-11-10\",\"2026-11-11\"],"
                + "\"weather_code\":" + codes + ","
                + "\"temperature_2m_max\":[31.4,32.0,30.6],"
                + "\"temperature_2m_min\":[24.1,24.8,23.9],"
                + "\"precipitation_probability_max\":" + chances + ","
                + "\"precipitation_sum\":[0,0,0]}}");
    }

    @Test
    public void parsesDailyArrays() throws Exception {
        List<WeatherForecast.Day> days = WeatherForecast.parse(sample("[0,2,3]", "[5,10,20]"));
        assertEquals(3, days.size());
        assertEquals(LocalDate.of(2026, 11, 10), days.get(1).date);
        assertEquals(31.4, days.get(0).maxTemp, 0.001);
        assertEquals(20, days.get(2).rainChance);
    }

    @Test
    public void labelsDays() {
        assertEquals("Today", WeatherForecast.dayLabel(TODAY, TODAY));
        assertEquals("Tomorrow", WeatherForecast.dayLabel(TODAY.plusDays(1), TODAY));
        assertEquals("Wed", WeatherForecast.dayLabel(TODAY.plusDays(2), TODAY));
    }

    @Test
    public void tipMentionsFirstRainyDay() throws Exception {
        List<WeatherForecast.Day> days = WeatherForecast.parse(sample("[0,2,63]", "[5,10,80]"));
        String tip = WeatherForecast.gardenTip(days, TODAY);
        assertTrue(tip, tip.startsWith("Rain likely on Wed"));
    }

    @Test
    public void tipWhenDry() throws Exception {
        List<WeatherForecast.Day> days = WeatherForecast.parse(sample("[0,1,2]", "[0,5,10]"));
        assertTrue(WeatherForecast.gardenTip(days, TODAY).startsWith("No rain expected"));
    }

    @Test
    public void describesCodes() {
        assertEquals("Thunderstorm", WeatherForecast.describe(95));
        assertEquals("Rain", WeatherForecast.describe(63));
        assertEquals("⛅", WeatherForecast.emoji(2));
    }
}
