package com.vinland.app.weather;

import com.vinland.app.BuildConfig;

/**
 * Shared configuration for weather API calls.
 *
 * The OpenWeatherMap key is NOT stored here. It comes from secrets.properties in the
 * project root (ignored by git) and is copied into BuildConfig when the app is built.
 * An empty string means the key is not set.
 */
public class WeatherConfig {
    public static final String API_KEY = BuildConfig.OPENWEATHER_API_KEY;
    public static final String CITY_QUERY = "Quezon City,PH";
}
