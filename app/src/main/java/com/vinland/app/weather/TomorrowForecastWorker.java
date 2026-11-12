package com.vinland.app.weather;

import android.content.Context;
import android.content.SharedPreferences;

import androidx.annotation.NonNull;
import androidx.work.ExistingPeriodicWorkPolicy;
import androidx.work.Constraints;
import androidx.work.NetworkType;
import androidx.work.PeriodicWorkRequest;
import androidx.work.WorkManager;
import androidx.work.Worker;
import androidx.work.WorkerParameters;

import com.google.android.gms.tasks.Tasks;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FirebaseFirestore;
import com.vinland.app.notifications.NotificationHelper;

import org.json.JSONObject;

import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.List;
import java.util.Scanner;
import java.util.concurrent.TimeUnit;

/**
 * Every evening (around 6 PM, Philippine time) sends one notification with
 * tomorrow's Quezon City forecast from Open-Meteo (free, no API key).
 * Respects the "Weather alerts" switch in Settings.
 */
public class TomorrowForecastWorker extends Worker {

    private static final String WORK_NAME = "VinlandTomorrowForecast";
    private static final ZoneId MANILA = ZoneId.of("Asia/Manila");
    private static final int SEND_HOUR = 18;          // 6 PM
    private static final int LATEST_HOUR = 23;        // don't send "tomorrow" after midnight
    private static final String PREFS = "tomorrow_forecast";
    private static final String KEY_LAST_SENT = "last_sent_date";

    public TomorrowForecastWorker(@NonNull Context context, @NonNull WorkerParameters params) {
        super(context, params);
    }

    /** Schedules the daily check. Safe to call every time the app opens. */
    public static void schedule(Context context) {
        PeriodicWorkRequest request = new PeriodicWorkRequest.Builder(
                TomorrowForecastWorker.class, 24, TimeUnit.HOURS)
                .setInitialDelay(delayUntilNextSend().toMinutes(), TimeUnit.MINUTES)
                .setConstraints(new Constraints.Builder()
                        .setRequiredNetworkType(NetworkType.CONNECTED).build())
                .build();
        WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                WORK_NAME, ExistingPeriodicWorkPolicy.KEEP, request);
    }

    private static Duration delayUntilNextSend() {
        LocalDateTime now = LocalDateTime.now(MANILA);
        LocalDateTime next = now.toLocalDate().atTime(LocalTime.of(SEND_HOUR, 0));
        if (!next.isAfter(now)) next = next.plusDays(1);
        return Duration.between(now, next);
    }

    @NonNull
    @Override
    public Result doWork() {
        LocalDateTime now = LocalDateTime.now(MANILA);
        LocalDate today = now.toLocalDate();

        // Only in the evening, and only once per day
        if (now.getHour() < SEND_HOUR || now.getHour() > LATEST_HOUR) return Result.success();
        SharedPreferences prefs = getApplicationContext().getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        if (today.toString().equals(prefs.getString(KEY_LAST_SENT, ""))) return Result.success();
        if (!weatherAlertsEnabled()) return Result.success();

        try {
            List<WeatherForecast.Day> days = WeatherForecast.parse(new JSONObject(download(WeatherForecast.URL)));
            WeatherForecast.Day tomorrow = WeatherForecast.tomorrow(days, today);
            if (tomorrow == null) return Result.retry();

            String[] notice = WeatherForecast.tomorrowNotice(tomorrow);
            NotificationHelper.sendNotification(getApplicationContext(), notice[0], notice[1]);
            prefs.edit().putString(KEY_LAST_SENT, today.toString()).apply();
            return Result.success();
        } catch (Exception e) {
            return Result.retry();
        }
    }

    /** Reads the Settings switch from the user's profile. Defaults to on. */
    private boolean weatherAlertsEnabled() {
        FirebaseAuth auth = FirebaseAuth.getInstance();
        if (auth.getCurrentUser() == null) return false;   // signed out: no alerts
        try {
            DocumentSnapshot doc = Tasks.await(
                    FirebaseFirestore.getInstance().collection("users")
                            .document(auth.getCurrentUser().getUid()).get(),
                    15, TimeUnit.SECONDS);
            Boolean enabled = doc.getBoolean("weatherAlertsEnabled");
            return enabled == null || enabled;
        } catch (Exception e) {
            return true;
        }
    }

    private static String download(String address) throws Exception {
        HttpURLConnection conn = (HttpURLConnection) new URL(address).openConnection();
        conn.setConnectTimeout(10000);
        conn.setReadTimeout(10000);
        try {
            if (conn.getResponseCode() != 200) throw new IllegalStateException("HTTP " + conn.getResponseCode());
            try (InputStream is = conn.getInputStream(); Scanner s = new Scanner(is, "UTF-8").useDelimiter("\\A")) {
                return s.hasNext() ? s.next() : "";
            }
        } finally {
            conn.disconnect();
        }
    }
}
