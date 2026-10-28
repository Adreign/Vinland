package com.vinland.app.weather;

import android.content.Context;

import androidx.annotation.NonNull;
import androidx.work.Worker;
import androidx.work.WorkerParameters;

import com.vinland.app.notifications.NotificationHelper;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.Scanner;

public class WeatherAlertWorker extends Worker {

    public WeatherAlertWorker(@NonNull Context context, @NonNull WorkerParameters workerParams) {
        super(context, workerParams);
    }

    @NonNull
    @Override
    public Result doWork() {
        try {
            String urlString = "https://api.openweathermap.org/data/2.5/weather?q="
                    + WeatherConfig.CITY_QUERY
                    + "&units=metric&appid="
                    + WeatherConfig.API_KEY;

            URL url = new URL(urlString);
            HttpURLConnection conn = (HttpURLConnection) url.openConnection();
            conn.setRequestMethod("GET");
            conn.setConnectTimeout(10000);
            conn.setReadTimeout(10000);

            if (conn.getResponseCode() == 200) {
                InputStream is = conn.getInputStream();
                Scanner scanner = new Scanner(is).useDelimiter("\\A");
                String responseStr = scanner.hasNext() ? scanner.next() : "";

                JSONObject response = new JSONObject(responseStr);
                JSONObject main = response.getJSONObject("main");
                double temp = main.getDouble("temp");
                int humidity = main.getInt("humidity");

                JSONArray weatherArray = response.getJSONArray("weather");
                String description = "";
                if (weatherArray.length() > 0) {
                    description = weatherArray.getJSONObject(0).optString("description", "").toLowerCase();
                }

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
                            "High humidity detected in QC (" + humidity + "%). Check garden crops and leafy greens for fungal leaf spot."
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
