package com.vinland.app.weather;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;

import com.android.volley.Request;
import com.android.volley.RequestQueue;
import com.android.volley.toolbox.JsonObjectRequest;
import com.android.volley.toolbox.Volley;
import com.google.android.material.card.MaterialCardView;
import com.vinland.app.R;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.Locale;

public class WeatherActivity extends AppCompatActivity {

    private TextView tvBack, tvLocation, tvTemp, tvDescription, tvHumidity, tvWind, tvFeelsLike, tvAdvisory, tvError;
    private TextView tvForecastTip, tvForecastError;
    private LinearLayout forecastList;
    private MaterialCardView cardCurrentWeather, cardAdvisory, cardForecast;
    private ProgressBar progressBar;
    private RequestQueue requestQueue;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_weather);

        requestQueue = Volley.newRequestQueue(this);

        tvBack = findViewById(R.id.tvBack);
        tvLocation = findViewById(R.id.tvLocation);
        tvTemp = findViewById(R.id.tvTemp);
        tvDescription = findViewById(R.id.tvDescription);
        tvHumidity = findViewById(R.id.tvHumidity);
        tvWind = findViewById(R.id.tvWind);
        tvFeelsLike = findViewById(R.id.tvFeelsLike);
        tvAdvisory = findViewById(R.id.tvAdvisory);
        tvError = findViewById(R.id.tvError);
        cardCurrentWeather = findViewById(R.id.cardCurrentWeather);
        cardAdvisory = findViewById(R.id.cardAdvisory);
        progressBar = findViewById(R.id.progressBar);
        cardForecast = findViewById(R.id.cardForecast);
        tvForecastTip = findViewById(R.id.tvForecastTip);
        tvForecastError = findViewById(R.id.tvForecastError);
        forecastList = findViewById(R.id.forecastList);

        tvBack.setOnClickListener(v -> finish());

        fetchWeather();
        fetchForecast();
    }

    private void fetchForecast() {
        JsonObjectRequest request = new JsonObjectRequest(
                Request.Method.GET,
                WeatherForecast.URL,
                null,
                response -> {
                    try {
                        showForecast(WeatherForecast.parse(response));
                    } catch (JSONException | RuntimeException e) {
                        tvForecastError.setVisibility(View.VISIBLE);
                    }
                },
                error -> tvForecastError.setVisibility(View.VISIBLE)
        );
        requestQueue.add(request);
    }

    private void showForecast(List<WeatherForecast.Day> days) {
        if (days.isEmpty()) {
            tvForecastError.setVisibility(View.VISIBLE);
            return;
        }
        LocalDate today = LocalDate.now(ZoneId.of("Asia/Manila"));
        LayoutInflater inflater = LayoutInflater.from(this);
        forecastList.removeAllViews();

        for (int i = 0; i < days.size(); i++) {
            WeatherForecast.Day day = days.get(i);
            if (i > 0) {
                View line = new View(this);
                line.setBackgroundColor(0xFFE2E7DC);
                forecastList.addView(line, new LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.MATCH_PARENT,
                        Math.max(1, Math.round(getResources().getDisplayMetrics().density))));
            }
            View row = inflater.inflate(R.layout.item_forecast_day, forecastList, false);
            ((TextView) row.findViewById(R.id.tvDay)).setText(WeatherForecast.dayLabel(day.date, today));
            ((TextView) row.findViewById(R.id.tvDayDesc)).setText(WeatherForecast.describe(day.weatherCode));
            ((TextView) row.findViewById(R.id.tvDayIcon)).setText(WeatherForecast.emoji(day.weatherCode));
            ((TextView) row.findViewById(R.id.tvDayRain)).setText("💧" + day.rainChance + "%");
            ((TextView) row.findViewById(R.id.tvDayTemp)).setText(String.format(Locale.getDefault(),
                    "%d° / %d°", Math.round(day.maxTemp), Math.round(day.minTemp)));
            row.setContentDescription(WeatherForecast.dayLabel(day.date, today) + ", "
                    + WeatherForecast.describe(day.weatherCode) + ", " + day.rainChance
                    + " percent chance of rain, high " + Math.round(day.maxTemp)
                    + ", low " + Math.round(day.minTemp) + " degrees");
            forecastList.addView(row);
        }

        tvForecastTip.setText(WeatherForecast.gardenTip(days, today));
        tvForecastError.setVisibility(View.GONE);
        cardForecast.setVisibility(View.VISIBLE);
    }

    private void fetchWeather() {
        progressBar.setVisibility(View.VISIBLE);
        tvError.setVisibility(View.GONE);

        String url = "https://api.openweathermap.org/data/2.5/weather?q="
                + WeatherConfig.CITY_QUERY
                + "&units=metric&appid="
                + WeatherConfig.API_KEY;

        JsonObjectRequest request = new JsonObjectRequest(
                Request.Method.GET,
                url,
                null,
                response -> {
                    progressBar.setVisibility(View.GONE);
                    parseWeatherResponse(response);
                },
                error -> {
                    progressBar.setVisibility(View.GONE);
                    tvError.setText("Could not fetch current weather for Quezon City. Check your connection.");
                    tvError.setVisibility(View.VISIBLE);
                }
        );

        requestQueue.add(request);
    }

    private void parseWeatherResponse(JSONObject response) {
        try {
            JSONObject main = response.getJSONObject("main");
            double temp = main.getDouble("temp");
            double feelsLike = main.getDouble("feels_like");
            int humidity = main.getInt("humidity");

            double windSpeed = 0.0;
            if (response.has("wind")) {
                windSpeed = response.getJSONObject("wind").optDouble("speed", 0.0);
            }

            JSONArray weatherArray = response.getJSONArray("weather");
            String description = "Clear";
            if (weatherArray.length() > 0) {
                description = weatherArray.getJSONObject(0).getString("description");
            }

            tvLocation.setText("Quezon City, Philippines");
            tvTemp.setText(String.format(Locale.getDefault(), "%.1f°C", temp));
            tvDescription.setText(capitalize(description));
            tvHumidity.setText(humidity + "%");
            tvWind.setText(String.format(Locale.getDefault(), "%.1f m/s", windSpeed));
            tvFeelsLike.setText(String.format(Locale.getDefault(), "%.1f°C", feelsLike));

            tvAdvisory.setText(buildAdvisory(temp, humidity, description));

            if (cardCurrentWeather != null) cardCurrentWeather.setVisibility(View.VISIBLE);
            if (cardAdvisory != null) cardAdvisory.setVisibility(View.VISIBLE);

        } catch (JSONException e) {
            tvError.setText("Error parsing weather data: " + e.getMessage());
            tvError.setVisibility(View.VISIBLE);
        }
    }

    private String buildAdvisory(double temp, int humidity, String description) {
        String lowerDesc = description.toLowerCase();

        if (lowerDesc.contains("rain") || lowerDesc.contains("thunderstorm") || lowerDesc.contains("drizzle")) {
            return "Rain detected in current conditions. Consider delaying watering today and check that containers have drainage.";
        }
        if (temp >= 33) {
            return "High temperature today. Soil may dry out faster \u2014 check moisture levels more frequently.";
        }
        if (humidity >= 85) {
            return "High humidity levels. Watch for fungal leaf issues on garden crops and leafy greens.";
        }
        return "Conditions look stable for regular garden care today.";
    }

    private String capitalize(String text) {
        if (text == null || text.isEmpty()) return text;
        return text.substring(0, 1).toUpperCase() + text.substring(1);
    }
}
