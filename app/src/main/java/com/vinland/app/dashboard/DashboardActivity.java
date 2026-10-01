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
import com.vinland.app.notifications.VinlandFirebaseMessagingService;
import com.vinland.app.profile.ProfileActivity;
import com.vinland.app.recommendation.RecommendationActivity;
import com.vinland.app.reports.ReportsActivity;
import com.vinland.app.weather.WeatherActivity;
import com.vinland.app.weather.WeatherAlertWorker;

import java.util.concurrent.TimeUnit;

public class DashboardActivity extends AppCompatActivity {

    private TextView tvWelcome, tvSubtitle;
    private View navSoil, navWeather, navRecommend, navProfile, navCalendar, navReports;

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
        TextView tvLogout = findViewById(R.id.tvLogout);
        navSoil = findViewById(R.id.navSoil);
        navWeather = findViewById(R.id.navWeather);
        navRecommend = findViewById(R.id.navRecommend);
        navProfile = findViewById(R.id.navProfile);
        navCalendar = findViewById(R.id.navCalendar);
        navReports = findViewById(R.id.navReports);

        loadGardenerProfile();
        requestNotificationPermission();
        scheduleWeatherAlertWorker();
        syncFcmToken();

        tvLogout.setOnClickListener(v -> confirmLogOut());

        navSoil.setOnClickListener(v -> startActivity(new Intent(this, RecommendationActivity.class)));
        navWeather.setOnClickListener(v -> startActivity(new Intent(this, WeatherActivity.class)));
        navRecommend.setOnClickListener(v -> startActivity(new Intent(this, RecommendationActivity.class)));
        navProfile.setOnClickListener(v -> startActivity(new Intent(this, ProfileActivity.class)));
        navCalendar.setOnClickListener(v -> startActivity(new Intent(this, PlantingCalendarActivity.class)));
        navReports.setOnClickListener(v -> startActivity(new Intent(this, ReportsActivity.class)));
    }

    private void requestNotificationPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
                requestPermissions(new String[]{Manifest.permission.POST_NOTIFICATIONS}, 1001);
            }
        }
    }

    private void scheduleWeatherAlertWorker() {
        PeriodicWorkRequest weatherWorkRequest = new PeriodicWorkRequest.Builder(
                WeatherAlertWorker.class,
                6, TimeUnit.HOURS
        ).build();

        WorkManager.getInstance(this).enqueueUniquePeriodicWork(
                "VinlandWeatherAlertWorker",
                ExistingPeriodicWorkPolicy.KEEP,
                weatherWorkRequest
        );
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
        if (document.exists()) {
            String name = document.getString("fullName");
            String barangay = document.getString("barangay");

            tvWelcome.setText("Hello, " + (name != null ? name : "Gardener") + "!");
            tvSubtitle.setText(barangay != null ? barangay + ", Quezon City" : "Welcome to Vinland");
        }
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
