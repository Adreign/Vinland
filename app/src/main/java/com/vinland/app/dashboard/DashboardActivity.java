package com.vinland.app.dashboard;

import android.Manifest;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Bundle;
import android.view.View;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.work.ExistingPeriodicWorkPolicy;
import androidx.work.OneTimeWorkRequest;
import androidx.work.PeriodicWorkRequest;
import androidx.work.WorkManager;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.messaging.FirebaseMessaging;
import com.vinland.app.R;
import com.vinland.app.auth.LoginActivity;
import com.vinland.app.calendar.PlantingCalendarActivity;
import com.vinland.app.notifications.NotificationHelper;
import com.vinland.app.notifications.NotificationHistoryActivity;
import com.vinland.app.notifications.VinlandFirebaseMessagingService;
import com.vinland.app.profile.ProfileActivity;
import com.vinland.app.recommendation.RecommendationActivity;
import com.vinland.app.reports.ReportsActivity;
import com.vinland.app.settings.SettingsActivity;
import com.vinland.app.weather.WeatherActivity;
import com.vinland.app.weather.WeatherAlertWorker;

import java.util.concurrent.TimeUnit;

public class DashboardActivity extends AppCompatActivity {

    private TextView tvWelcome, tvSubtitle;
    private View navSoil, navWeather, navProfile, navCalendar, navReports, navSettings;

    private FirebaseAuth mAuth;
    private FirebaseFirestore db;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_dashboard);

        mAuth = FirebaseAuth.getInstance();
        db = FirebaseFirestore.getInstance();

        tvWelcome = findViewById(R.id.tvWelcome);
        tvSubtitle = findViewById(R.id.tvSubtitle);
        TextView tvHeaderNotification = findViewById(R.id.tvHeaderNotification);
        navSoil = findViewById(R.id.navSoil);
        navWeather = findViewById(R.id.navWeather);
        navProfile = findViewById(R.id.navProfile);
        navCalendar = findViewById(R.id.navCalendar);
        navReports = findViewById(R.id.navReports);
        navSettings = findViewById(R.id.navSettings);
        View navLogout = findViewById(R.id.navLogout);

        com.vinland.app.data.CropDatabaseSeeder.seedIfEmpty(db, null);

        loadGardenerProfile();
        requestNotificationPermission();
        scheduleWeatherAlertWorker();
        syncFcmToken();

        tvHeaderNotification.setOnClickListener(v -> startActivity(new Intent(this, NotificationHistoryActivity.class)));

        navSoil.setOnClickListener(v -> startActivity(new Intent(this, RecommendationActivity.class)));
        navWeather.setOnClickListener(v -> startActivity(new Intent(this, WeatherActivity.class)));
        navProfile.setOnClickListener(v -> startActivity(new Intent(this, ProfileActivity.class)));
        navCalendar.setOnClickListener(v -> startActivity(new Intent(this, PlantingCalendarActivity.class)));
        navReports.setOnClickListener(v -> startActivity(new Intent(this, ReportsActivity.class)));
        View navAiRecommendations = findViewById(R.id.navAiRecommendations);
        navAiRecommendations.setOnClickListener(v ->
                startActivity(new Intent(this, com.vinland.app.ai.AiRecommendationActivity.class)));
        findViewById(R.id.navSavedRecommendations).setOnClickListener(v ->
                startActivity(new Intent(this, com.vinland.app.ai.SavedRecommendationsActivity.class)));
        findViewById(R.id.navMyPlantings).setOnClickListener(v ->
                startActivity(new Intent(this, com.vinland.app.planting.MyPlantingsActivity.class)));
        if (navSettings != null) {
            navSettings.setOnClickListener(v -> startActivity(new Intent(this, SettingsActivity.class)));
        }
        navLogout.setOnClickListener(v -> confirmLogOut());
    }

    private void requestNotificationPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
                requestPermissions(new String[]{Manifest.permission.POST_NOTIFICATIONS}, 1001);
            }
        }
    }

    private void scheduleWeatherAlertWorker() {
        NotificationHelper.createNotificationChannel(this);

        PeriodicWorkRequest weatherWorkRequest = new PeriodicWorkRequest.Builder(
                WeatherAlertWorker.class,
                5, TimeUnit.HOURS
        ).build();

        WorkManager.getInstance(this).enqueueUniquePeriodicWork(
                "VinlandWeatherAlertWorker",
                ExistingPeriodicWorkPolicy.UPDATE,
                weatherWorkRequest
        );

        // Immediate sync on launch to issue daily garden care notification
        OneTimeWorkRequest immediateWorkRequest = new OneTimeWorkRequest.Builder(WeatherAlertWorker.class).build();
        WorkManager.getInstance(this).enqueue(immediateWorkRequest);

        // Every evening: tomorrow's forecast
        com.vinland.app.weather.TomorrowForecastWorker.schedule(this);

        // Every morning: today's tasks for active plantings
        com.vinland.app.planting.DailyPlantingWorker.schedule(this);
    }

    @Override
    protected void onResume() {
        super.onResume();
        loadPlantingsSummary();
    }

    /** "2 active · Pechay day 5" on the My Plantings card. */
    private void loadPlantingsSummary() {
        if (mAuth.getCurrentUser() == null) return;
        TextView tvSummary = findViewById(R.id.tvPlantingsSummary);
        com.vinland.app.planting.PlantingRepository.plantings(mAuth.getCurrentUser().getUid())
                .whereEqualTo("status", com.vinland.app.planting.Planting.ACTIVE)
                .get()
                .addOnSuccessListener(snap -> {
                    if (snap.isEmpty()) {
                        tvSummary.setText("Start one from the Planting Calendar");
                        return;
                    }
                    // Show the most recently started one.
                    String latestName = null, latestStart = null;
                    for (DocumentSnapshot d : snap.getDocuments()) {
                        String start = d.getString("startDate");
                        if (start != null && (latestStart == null || start.compareTo(latestStart) > 0)) {
                            latestStart = start;
                            latestName = d.getString("cropName");
                        }
                    }
                    String text = snap.size() + " active";
                    if (latestName != null) {
                        long day = java.time.temporal.ChronoUnit.DAYS.between(
                                java.time.LocalDate.parse(latestStart),
                                com.vinland.app.planting.PlantingRepository.today()) + 1;
                        text += " · " + latestName + " day " + Math.max(1, day);
                    }
                    tvSummary.setText(text + " · tap for today's tasks");
                });
    }

    private void syncFcmToken() {
        FirebaseMessaging.getInstance().getToken().addOnCompleteListener(task -> {
            if (task.isSuccessful() && task.getResult() != null) {
                VinlandFirebaseMessagingService.saveTokenToFirestore(task.getResult());
            }
        });
    }

    private void loadGardenerProfile() {
        FirebaseUser user = mAuth.getCurrentUser();
        if (user == null) {
            goToLogin();
            return;
        }

        db.collection("users").document(user.getUid())
                .get()
                .addOnSuccessListener(this::displayProfile)
                .addOnFailureListener(e ->
                        Toast.makeText(this, "Could not load your profile: " + e.getMessage(), Toast.LENGTH_SHORT).show());
    }

    private void displayProfile(DocumentSnapshot document) {
        if (!document.exists()) {
            // Account was deleted from database! Block access & logout
            Toast.makeText(this, "Your account has been deleted by the administrator.", Toast.LENGTH_LONG).show();
            logOut();
            return;
        }

        String status = document.getString("status");
        Boolean isDeleted = document.getBoolean("deleted");

        if ("Suspended".equalsIgnoreCase(status) || "Deactivated".equalsIgnoreCase(status) || Boolean.TRUE.equals(isDeleted)) {
            // Account suspended or deactivated! Block access & logout
            Toast.makeText(this, "Your account has been suspended by the administrator.", Toast.LENGTH_LONG).show();
            logOut();
            return;
        }

        String name = document.getString("fullName");
        String barangay = document.getString("barangay");

        tvWelcome.setText("Hello, " + (name != null ? name : "Gardener") + "!");
        tvSubtitle.setText(barangay != null ? barangay + ", Quezon City" : "Welcome to Vinland");
    }

    private void confirmLogOut() {
        new AlertDialog.Builder(this)
                .setTitle("Log Out")
                .setMessage("Are you sure you want to log out of Vinland?")
                .setPositiveButton("Log Out", (dialog, which) -> logOut())
                .setNegativeButton("Cancel", (dialog, which) -> dialog.dismiss())
                .show();
    }

    private void logOut() {
        mAuth.signOut();
        goToLogin();
    }

    private void goToLogin() {
        Intent intent = new Intent(this, LoginActivity.class);
        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        startActivity(intent);
        finish();
    }
}
