package com.vinland.app.planting;

import android.content.Context;
import android.content.SharedPreferences;

import androidx.annotation.NonNull;
import androidx.work.Constraints;
import androidx.work.ExistingPeriodicWorkPolicy;
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

import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * Every morning (around 7 AM, Philippine time) sends one notification with today's
 * tasks for the gardener's active plantings. Respects the "Daily planting tips" switch in Settings.
 */
public class DailyPlantingWorker extends Worker {

    private static final String WORK_NAME = "VinlandDailyPlantingTips";
    private static final int SEND_HOUR = 7;      // 7 AM
    private static final int LATEST_HOUR = 20;   // phone was off all morning: still send until 8 PM
    private static final String PREFS = "daily_planting_tips";
    private static final String KEY_LAST_SENT = "last_sent_date";

    public DailyPlantingWorker(@NonNull Context context, @NonNull WorkerParameters params) {
        super(context, params);
    }

    /** Schedules the daily check. Safe to call every time the app opens. */
    public static void schedule(Context context) {
        PeriodicWorkRequest request = new PeriodicWorkRequest.Builder(
                DailyPlantingWorker.class, 24, TimeUnit.HOURS)
                .setInitialDelay(delayUntilNextSend().toMinutes(), TimeUnit.MINUTES)
                .setConstraints(new Constraints.Builder()
                        .setRequiredNetworkType(NetworkType.CONNECTED).build())
                .build();
        WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                WORK_NAME, ExistingPeriodicWorkPolicy.KEEP, request);
    }

    private static Duration delayUntilNextSend() {
        LocalDateTime now = LocalDateTime.now(PlantingRepository.MANILA);
        LocalDateTime next = now.toLocalDate().atTime(LocalTime.of(SEND_HOUR, 0));
        if (!next.isAfter(now)) next = next.plusDays(1);
        return Duration.between(now, next);
    }

    @NonNull
    @Override
    public Result doWork() {
        LocalDateTime now = LocalDateTime.now(PlantingRepository.MANILA);
        LocalDate today = now.toLocalDate();

        if (now.getHour() < SEND_HOUR || now.getHour() > LATEST_HOUR) return Result.success();
        SharedPreferences prefs = getApplicationContext().getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        if (today.toString().equals(prefs.getString(KEY_LAST_SENT, ""))) return Result.success();

        FirebaseAuth auth = FirebaseAuth.getInstance();
        if (auth.getCurrentUser() == null) return Result.success();
        String uid = auth.getCurrentUser().getUid();
        if (!tipsEnabled(uid)) return Result.success();

        try {
            List<PlantingRepository.Item> items = PlantingRepository.loadToday(uid);
            if (!items.isEmpty()) {
                String[] n = buildNotice(items);
                NotificationHelper.sendNotification(getApplicationContext(), n[0], n[1],
                        MyPlantingsActivity.class, "general");
            }
            prefs.edit().putString(KEY_LAST_SENT, today.toString()).apply();
            return Result.success();
        } catch (Exception e) {
            return Result.retry();
        }
    }

    /** {title, message}. One planting: its top tasks. Several: one line each. */
    static String[] buildNotice(List<PlantingRepository.Item> items) {
        if (items.size() == 1) {
            PlantingRepository.Item it = items.get(0);
            PlantingAdvisor.Advice a = it.advice;
            String title = a.stage.emoji + " " + it.planting.getCropName() + " · Day " + a.dayNumber + " of " + a.totalDays;
            StringBuilder msg = new StringBuilder();
            for (int i = 0; i < Math.min(3, a.tasks.size()); i++) {
                if (i > 0) msg.append('\n');
                msg.append(a.tasks.get(i));
            }
            return new String[]{title, msg.toString()};
        }
        StringBuilder msg = new StringBuilder();
        for (PlantingRepository.Item it : items) {
            if (msg.length() > 0) msg.append('\n');
            msg.append(it.planting.getCropName()).append(" (Day ").append(it.advice.dayNumber).append("): ")
                    .append(it.advice.tasks.isEmpty() ? it.advice.stage.label : it.advice.tasks.get(0));
        }
        return new String[]{"🌱 Today's planting tips (" + items.size() + " crops)", msg.toString()};
    }

    /** Reads the Settings switch from the user's profile. Defaults to on. */
    private boolean tipsEnabled(String uid) {
        try {
            DocumentSnapshot doc = Tasks.await(
                    FirebaseFirestore.getInstance().collection("users").document(uid).get(),
                    15, TimeUnit.SECONDS);
            Boolean enabled = doc.getBoolean("plantingTipsEnabled");
            return enabled == null || enabled;
        } catch (Exception e) {
            return true;
        }
    }
}
