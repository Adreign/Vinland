package com.vinland.app.weather;

import android.os.Bundle;
import android.view.View;
import android.widget.ProgressBar;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;

import com.android.volley.Request;
import com.android.volley.RequestQueue;
import com.android.volley.toolbox.JsonObjectRequest;
import com.android.volley.toolbox.Volley;
import com.google.android.material.card.MaterialCardView;
import com.vinland.app.R;

import org.json.JSONException;
import org.json.JSONObject;

public class WeatherActivity extends AppCompatActivity {

    private ProgressBar progressBar;
    private TextView tvError;
    private MaterialCardView cardCurrentWeather, cardAdvisory;
    private TextView tvLocation, tvTemp, tvDescription, tvHumidity, tvWind, tvFeelsLike, tvAdvisory;

    private RequestQueue requestQueue;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_weather);

        progressBar = findViewById(R.id.progressBar);
        tvError = findViewById(R.id.tvError);
        cardCurrentWeather = findViewById(R.id.cardCurrentWeather);
        cardAdvisory = findViewById(R.id.cardAdvisory);
        tvLocation = findViewById(R.id.tvLocation);
        tvTemp = findViewById(R.id.tvTemp);
        tvDescription = findViewById(R.id.tvDescription);
        tvHumidity = findViewById(R.id.tvHumidity);
        tvWind = findViewById(R.id.tvWind);
        tvFeelsLike = findViewById(R.id.tvFeelsLike);
        tvAdvisory = findViewById(R.id.tvAdvisory);
        TextView tvBack = findViewById(R.id.tvBack);

        tvBack.setOnClickListener(v -> finish());

        requestQueue = Volley.newRequestQueue(this);
        fetchWeather();
    }

    private void fetchWeather() {
        String url = "https://api.openweathermap.org/data/2.5/weather"
                + "?q=" + WeatherConfig.CITY_QUERY
                + "&appid=" + WeatherConfig.API_KEY
                + "&units=metric";

        JsonObjectRequest request = new JsonObjectRequest(
                Request.Method.GET,
                url,
                null,
                this::displayWeather,
                error -> showError()
        );

        requestQueue.add(request);
    }

    private void displayWeather(JSONObject response) {
        try {
            JSONObject main = response.getJSONObject("main");
            JSONObject wind = response.getJSONObject("wind");
            String description = response.getJSONArray("weather")
                    .getJSONObject(0)
                    .getString("description");

            double temp = main.getDouble("temp");
            double feelsLike = main.getDouble("feels_like");
            int humidity = main.getInt("humidity");
            double windSpeed = wind.getDouble("speed");

            tvLocation.setText("Quezon City");
            tvTemp.setText(String.format("%.0f°C", temp));
            tvDescription.setText(capitalize(description));
            tvHumidity.setText(humidity + "%");
            tvWind.setText(String.format("%.1f m/s", windSpeed));
            tvFeelsLike.setText(String.format("%.0f°C", feelsLike));

            tvAdvisory.setText(buildAdvisory(temp, humidity, description));

            progressBar.setVisibility(View.GONE);
            tvError.setVisibility(View.GONE);
            cardCurrentWeather.setVisibility(View.VISIBLE);
            cardAdvisory.setVisibility(View.VISIBLE);

        } catch (JSONException e) {
            showError();
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
            return "High humidity levels. Watch for fungal issues on leafy crops like pechay or tomato.";
        }
        return "Conditions look stable for regular garden care today.";
    }

    private String capitalize(String text) {
        if (text == null || text.isEmpty()) return text;
        return text.substring(0, 1).toUpperCase() + text.substring(1);
    }

    private void showError() {
        progressBar.setVisibility(View.GONE);
        cardCurrentWeather.setVisibility(View.GONE);
        cardAdvisory.setVisibility(View.GONE);
        tvError.setVisibility(View.VISIBLE);
    }
}
