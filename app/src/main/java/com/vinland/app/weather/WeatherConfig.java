package com.vinland.app.weather;

import com.vinland.app.BuildConfig;

/**
 * Shared configuration for weather & AI related API calls.
 *
 * The AI keys are NOT stored here. They come from secrets.properties in the project
 * root (ignored by git) and are copied into BuildConfig when the app is built.
 * An empty string means the key is not set.
 *
 * Note: keys built into an app can still be extracted from the APK. The AI
 * recommendations screen avoids this by calling a Cloud Function instead.
 */
public class WeatherConfig {
    public static final String API_KEY = "f6916f7a3b5df359d39289773c941106";
    public static final String GEMINI_API_KEY = BuildConfig.GEMINI_API_KEY;
    public static final String GROQ_API_KEY = BuildConfig.GROQ_API_KEY;
    public static final String OPENAI_API_KEY = BuildConfig.OPENAI_API_KEY;
    public static final String FINE_TUNED_MODEL_ID = BuildConfig.OPENAI_FINE_TUNED_MODEL_ID;
    public static final String CITY_QUERY = "Quezon City,PH";
}
