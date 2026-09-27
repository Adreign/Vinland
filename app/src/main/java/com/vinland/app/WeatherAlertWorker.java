package com.vinland.app;

import android.content.Context;

import androidx.annotation.NonNull;
import androidx.work.Worker;
import androidx.work.WorkerParameters;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;

public class WeatherAlertWorker extends Worker {

    public WeatherAlertWorker(@NonNull Context context, @NonNull WorkerParameters workerParams) {
        super(context, workerParams);
    }

    @NonNull
    @Override
    public Result doWork() {
        try {
            String urlString = "https://api.openweathermap.org/data/2.5/weather"
                    + "?q=" + WeatherConfig.CITY_QUERY
                    + "&appid=" + WeatherConfig.API_KEY
                    + "&units=metric";

            URL url = new URL(urlString);
            HttpURLConnection conn = (HttpURLConnection) url.openConnection();
            conn.setRequestMethod("GET");
            conn.setConnectTimeout(10000);
            conn.setReadTimeout(10000);

            int responseCode = conn.getResponseCode();
            if (responseCode == HttpURLConnection.HTTP_OK) {
                BufferedReader reader = new BufferedReader(new InputStreamReader(conn.getInputStream()));
                StringBuilder response = new StringBuilder();
                String line;
                while ((line = reader.readLine()) != null) {
                    response.append(line);
                }
                reader.close();

                JSONObject json = new JSONObject(response.toString());
                JSONObject main = json.getJSONObject("main");
                JSONArray weather = json.getJSONArray("weather");

                double temp = main.getDouble("temp");
                int humidity = main.getInt("humidity");
                String description = weather.getJSONObject(0).getString("description").toLowerCase();

                if (description.contains("rain") || description.contains("thunderstorm") || description.contains("drizzle")) {
                    NotificationHelper.sendNotification(
                            getApplicationContext(),
                            "☔ Quezon City Rain Advisory",
                            "Rain detected today in QC. Delay watering urban beds and verify container drainage."
                    );
                } else if (temp >= 33) {
                    NotificationHelper.sendNotification(
                            getApplicationContext(),
                            "🌡️ Extreme Heat Warning",
                            "Temperatures reaching " + Math.round(temp) + "°C today in QC. Check soil moisture and provide shade for leafy crops."
                    );
                } else if (humidity >= 85) {
                    NotificationHelper.sendNotification(
                            getApplicationContext(),
                            "🌿 High Humidity Alert",
                            "High humidity detected in QC (" + humidity + "%). Check leafy crops like pechay or tomato for fungal leaf spot."
                    );
                }
            }
            conn.disconnect();
            return Result.success();
        } catch (Exception e) {
            return Result.retry();
        }
    }
}
