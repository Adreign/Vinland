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

                if (description.contains("rain") || description.contains("thunderstorm") || description.contains("drizzle") || description.contains("shower")) {
                    NotificationHelper.sendNotification(
                            getApplicationContext(),
                            "☔ Quezon City Rain Advisory",
                            "Rain detected in QC (" + description + "). Verify container drainage and delay irrigation."
                    );
                } else if (temp >= 32) {
                    NotificationHelper.sendNotification(
                            getApplicationContext(),
                            "🌡️ Quezon City Heat Warning",
                            "High temperature (" + Math.round(temp) + "°C) in QC today. Water base of plants and shade young seedlings."
                    );
                } else if (humidity >= 80) {
                    NotificationHelper.sendNotification(
                            getApplicationContext(),
                            "🌿 High Humidity Alert",
                            "High humidity detected (" + humidity + "%). Inspect leafy crops for fungal spot or powdery mildew."
                    );
                } else {
                    NotificationHelper.sendNotification(
                            getApplicationContext(),
                            "🌱 Vinland Daily Garden Advisory",
                            "Weather in Quezon City: " + description + " (" + Math.round(temp) + "°C). Check soil Moisture & NPK via Vinland."
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
