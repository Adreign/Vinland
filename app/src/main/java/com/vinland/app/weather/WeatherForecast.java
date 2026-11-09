package com.vinland.app.weather;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.time.LocalDate;
import java.time.format.TextStyle;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * 7-day forecast for Quezon City from Open-Meteo (free, no API key).
 * The same service powers the weather card on the web admin dashboard.
 */
public final class WeatherForecast {

    public static final String URL = "https://api.open-meteo.com/v1/forecast"
            + "?latitude=14.6760&longitude=121.0437"
            + "&daily=weather_code,temperature_2m_max,temperature_2m_min,"
            + "precipitation_probability_max,precipitation_sum"
            + "&timezone=Asia%2FManila&forecast_days=7";

    /** One day of the forecast. */
    public static final class Day {
        public final LocalDate date;
        public final int weatherCode;
        public final double maxTemp;
        public final double minTemp;
        public final int rainChance;   // percent, 0-100
        public final double rainMm;

        public Day(LocalDate date, int weatherCode, double maxTemp, double minTemp,
                   int rainChance, double rainMm) {
            this.date = date;
            this.weatherCode = weatherCode;
            this.maxTemp = maxTemp;
            this.minTemp = minTemp;
            this.rainChance = rainChance;
            this.rainMm = rainMm;
        }

        public boolean isRainy() {
            return rainChance >= 60 || rainMm >= 5 || weatherCode >= 61;
        }
    }

    private WeatherForecast() {}

    public static List<Day> parse(JSONObject response) throws JSONException {
        JSONObject daily = response.getJSONObject("daily");
        JSONArray dates = daily.getJSONArray("time");
        JSONArray codes = daily.getJSONArray("weather_code");
        JSONArray maxT = daily.getJSONArray("temperature_2m_max");
        JSONArray minT = daily.getJSONArray("temperature_2m_min");
        JSONArray chance = daily.optJSONArray("precipitation_probability_max");
        JSONArray rain = daily.optJSONArray("precipitation_sum");

        List<Day> days = new ArrayList<>();
        for (int i = 0; i < dates.length(); i++) {
            days.add(new Day(
                    LocalDate.parse(dates.getString(i)),
                    codes.optInt(i, 0),
                    maxT.optDouble(i, 0),
                    minT.optDouble(i, 0),
                    chance == null ? 0 : chance.optInt(i, 0),
                    rain == null ? 0 : rain.optDouble(i, 0)));
        }
        return days;
    }

    /** "Today", "Tomorrow", or the short weekday name (e.g. "Wed"). */
    public static String dayLabel(LocalDate date, LocalDate today) {
        if (date.equals(today)) return "Today";
        if (date.equals(today.plusDays(1))) return "Tomorrow";
        return date.getDayOfWeek().getDisplayName(TextStyle.SHORT, Locale.ENGLISH);
    }

    /** Emoji for a WMO weather code (the codes Open-Meteo uses). */
    public static String emoji(int code) {
        if (code == 0) return "☀️";
        if (code <= 2) return "⛅";
        if (code == 3) return "☁️";
        if (code <= 48) return "🌫️";
        if (code <= 67) return "🌧️";
        if (code <= 77) return "🌨️";
        if (code <= 82) return "🌦️";
        return "⛈️";
    }

    /** Plain description for a WMO weather code. */
    public static String describe(int code) {
        if (code == 0) return "Clear sky";
        if (code == 1) return "Mostly clear";
        if (code == 2) return "Partly cloudy";
        if (code == 3) return "Overcast";
        if (code <= 48) return "Fog";
        if (code <= 57) return "Drizzle";
        if (code <= 67) return "Rain";
        if (code <= 77) return "Snow";
        if (code <= 82) return "Rain showers";
        return "Thunderstorm";
    }

    /** One-line garden tip for the coming days. */
    public static String gardenTip(List<Day> days, LocalDate today) {
        if (days.isEmpty()) return "";
        int rainyDays = 0;
        Day firstRain = null;
        Day hottest = days.get(0);
        for (Day d : days) {
            if (d.isRainy()) {
                rainyDays++;
                if (firstRain == null) firstRain = d;
            }
            if (d.maxTemp > hottest.maxTemp) hottest = d;
        }

        if (rainyDays >= 4) {
            return "A wet week ahead (" + rainyDays + " rainy days). Water less, keep drains clear "
                    + "and move pots under shelter if heavy rain comes.";
        }
        if (firstRain != null) {
            String label = dayLabel(firstRain.date, today);
            String when = label.equals("Today") || label.equals("Tomorrow")
                    ? label.toLowerCase(Locale.ENGLISH) : "on " + label;
            return "Rain likely " + when
                    + " — you can skip watering that day and harvest ripe crops before it.";
        }
        if (hottest.maxTemp >= 33) {
            return "Hot, dry days ahead (up to " + Math.round(hottest.maxTemp)
                    + "°C). Water early in the morning and add mulch to keep soil moist.";
        }
        return "No rain expected this week. Keep to your regular watering schedule.";
    }
}
