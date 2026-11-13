package com.vinland.app.planting;

import com.google.android.gms.tasks.Tasks;
import com.google.firebase.firestore.CollectionReference;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.Query;
import com.google.firebase.firestore.QuerySnapshot;
import com.vinland.app.data.Crop;
import com.vinland.app.weather.WeatherForecast;

import org.json.JSONObject;

import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Scanner;
import java.util.concurrent.TimeUnit;

/**
 * Loads everything the daily planting tips need and runs {@link PlantingAdvisor}.
 * Blocking calls: run it on a background thread (the screen uses an executor, the worker already is one).
 */
public final class PlantingRepository {

    public static final ZoneId MANILA = ZoneId.of("Asia/Manila");

    /** Soil readings older than this are ignored. */
    static final long READING_MAX_AGE_MS = 48L * 60 * 60 * 1000;
    private static final int TIMEOUT_S = 15;

    private PlantingRepository() {
    }

    /** A planting together with today's advice for it. */
    public static class Item {
        public final Planting planting;
        public final PlantingAdvisor.Advice advice;

        Item(Planting planting, PlantingAdvisor.Advice advice) {
            this.planting = planting;
            this.advice = advice;
        }
    }

    public static CollectionReference plantings(String uid) {
        return FirebaseFirestore.getInstance().collection("users").document(uid).collection("plantings");
    }

    public static LocalDate today() {
        return LocalDate.now(MANILA);
    }

    /** Active plantings with today's advice, oldest first. Weather/soil failures are skipped, not fatal. */
    public static List<Item> loadToday(String uid) throws Exception {
        FirebaseFirestore db = FirebaseFirestore.getInstance();

        QuerySnapshot snap = Tasks.await(plantings(uid).whereEqualTo("status", Planting.ACTIVE).get(),
                TIMEOUT_S, TimeUnit.SECONDS);
        List<Planting> active = new ArrayList<>();
        for (DocumentSnapshot doc : snap.getDocuments()) {
            Planting p = doc.toObject(Planting.class);
            if (p != null && p.getStartDate() != null) {
                p.setId(doc.getId());
                active.add(p);
            }
        }
        active.sort((a, b) -> a.getStartDate().compareTo(b.getStartDate()));
        if (active.isEmpty()) return new ArrayList<>();

        Map<String, Crop> crops = loadCrops(db);
        PlantingAdvisor.SoilReading soil = loadSoil(db, uid);
        WeatherForecast.Day weather = loadWeather();

        LocalDate today = today();
        List<Item> items = new ArrayList<>();
        for (Planting p : active) {
            Crop crop = p.getCropName() == null ? null : crops.get(p.getCropName().toLowerCase(Locale.ROOT));
            items.add(new Item(p, PlantingAdvisor.advise(p, crop, today, soil, weather)));
        }
        return items;
    }

    private static Map<String, Crop> loadCrops(FirebaseFirestore db) {
        Map<String, Crop> map = new HashMap<>();
        try {
            for (DocumentSnapshot doc : Tasks.await(db.collection("crops").get(), TIMEOUT_S, TimeUnit.SECONDS)) {
                Crop c = doc.toObject(Crop.class);
                if (c != null && c.getName() != null) map.put(c.getName().toLowerCase(Locale.ROOT), c);
            }
        } catch (Exception ignored) {
            // No crop data: tips still work, just without ranges and monthly tips.
        }
        return map;
    }

    /** The newer of the paired sensor's reading and the latest saved soil log, if under 48 hours old. */
    private static PlantingAdvisor.SoilReading loadSoil(FirebaseFirestore db, String uid) {
        long now = System.currentTimeMillis();
        DocumentSnapshot best = null;
        long bestMs = 0;
        try {
            DocumentSnapshot user = Tasks.await(db.collection("users").document(uid).get(), TIMEOUT_S, TimeUnit.SECONDS);
            String sensorId = user.getString("sensorDeviceId");
            if (sensorId != null && !sensorId.trim().isEmpty()) {
                DocumentSnapshot sensor = Tasks.await(
                        db.collection("sensor_devices").document(sensorId.trim()).get(), TIMEOUT_S, TimeUnit.SECONDS);
                Long ms = toMillis(sensor.get("timestamp"));
                if (sensor.exists() && ms != null) {
                    best = sensor;
                    bestMs = ms;
                }
            }
        } catch (Exception ignored) {
        }
        try {
            QuerySnapshot logs = Tasks.await(db.collection("users").document(uid).collection("soil_logs")
                    .orderBy("timestamp", Query.Direction.DESCENDING).limit(1).get(), TIMEOUT_S, TimeUnit.SECONDS);
            if (!logs.isEmpty()) {
                DocumentSnapshot log = logs.getDocuments().get(0);
                Long ms = toMillis(log.get("timestamp"));
                if (ms != null && ms > bestMs) {
                    best = log;
                    bestMs = ms;
                }
            }
        } catch (Exception ignored) {
        }
        if (best == null || now - bestMs > READING_MAX_AGE_MS) return null;
        return new PlantingAdvisor.SoilReading(
                best.getDouble("moisture"), best.getDouble("ph"), best.getDouble("nitrogen"),
                best.getDouble("phosphorus"), best.getDouble("potassium"));
    }

    /** Today's Quezon City forecast from Open-Meteo (free, no key), or null. */
    private static WeatherForecast.Day loadWeather() {
        try {
            List<WeatherForecast.Day> days = WeatherForecast.parse(new JSONObject(download(WeatherForecast.URL)));
            LocalDate today = today();
            for (WeatherForecast.Day d : days) {
                if (d.date.equals(today)) return d;
            }
        } catch (Exception ignored) {
        }
        return null;
    }

    private static Long toMillis(Object value) {
        if (value instanceof Number) return ((Number) value).longValue();
        if (value instanceof java.util.Date) return ((java.util.Date) value).getTime();
        if (value instanceof com.google.firebase.Timestamp) return ((com.google.firebase.Timestamp) value).toDate().getTime();
        return null;
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
