package com.vinland.app.data;

import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.WriteBatch;

import java.util.Arrays;
import java.util.List;

public class CropDatabaseSeeder {

    public interface OnSeedingCompleteListener {
        void onComplete();
    }

    private static final List<String> TOMATO_TIPS = Arrays.asList(
            "Good month for transplanting seedlings \u2014 cool, dry weather supports strong root growth.",
            "Continue regular watering. Watch for aphids as temperatures start rising.",
            "Fruiting season for Dec/Jan plantings. Support heavy branches with stakes.",
            "Hot, dry conditions \u2014 water more frequently and mulch to retain soil moisture.",
            "Last good month before the rains \u2014 consider starting a new batch of seedlings indoors.",
            "Rainy season begins. Ensure containers/beds have good drainage to prevent root rot.",
            "Peak rainy season \u2014 fungal disease risk is high. Avoid overhead watering; improve airflow.",
            "Continue monitoring for blight and other fungal issues common in wet weather.",
            "Rains may start easing. Prepare beds for the upcoming dry-season planting cycle.",
            "Great month to start new seedlings as rains taper off.",
            "Transplant seedlings started in September/October. Days are cooler and more favorable.",
            "Cool, dry weather \u2014 ideal growing conditions. Monitor moisture regularly."
    );

    private static final List<String> PEPPER_TIPS = Arrays.asList(
            "Favorable month for transplanting \u2014 green peppers prefer the cooler dry season.",
            "Steady growth period. Maintain consistent watering.",
            "Flowering and early fruiting \u2014 avoid letting soil dry out completely.",
            "Hot weather stress possible. Provide light shade during peak afternoon heat if needed.",
            "Harvest season for earlier plantings. Start next batch of seedlings indoors.",
            "Wet season begins \u2014 raised beds or containers with drainage holes are important.",
            "High humidity increases pest/disease risk. Space plants well for airflow.",
            "Continue wet-season care. Watch for waterlogging after heavy rains.",
            "Rains easing gradually. Begin preparing soil for the next planting cycle.",
            "Good month to start new seedlings as conditions dry out.",
            "Transplant seedlings from September/October. Favorable temperatures resume.",
            "Cool, dry conditions \u2014 strong growing month for green pepper."
    );

    public static void seedIfEmpty(FirebaseFirestore db, OnSeedingCompleteListener callback) {
        db.collection("crops").get().addOnCompleteListener(task -> {
            if (task.isSuccessful() && task.getResult() != null && task.getResult().isEmpty()) {
                seedInitialCrops(db, callback);
            } else {
                seedDefaultSensorDeviceIfMissing(db, callback);
            }
        });
    }

    private static void seedDefaultSensorDeviceIfMissing(FirebaseFirestore db, OnSeedingCompleteListener callback) {
        db.collection("sensor_devices").document("VINLAND-ESP32-01").get().addOnCompleteListener(task -> {
            if (task.isSuccessful() && task.getResult() != null && !task.getResult().exists()) {
                java.util.Map<String, Object> defaultSensor = new java.util.HashMap<>();
                defaultSensor.put("moisture", 52.0);
                defaultSensor.put("ph", 6.6);
                defaultSensor.put("nitrogen", 35.0);
                defaultSensor.put("phosphorus", 22.0);
                defaultSensor.put("potassium", 145.0);
                defaultSensor.put("timestamp", System.currentTimeMillis());

                db.collection("sensor_devices").document("VINLAND-ESP32-01")
                        .set(defaultSensor)
                        .addOnCompleteListener(t -> {
                            if (callback != null) callback.onComplete();
                        });
            } else {
                if (callback != null) callback.onComplete();
            }
        });
    }

    private static void seedInitialCrops(FirebaseFirestore db, OnSeedingCompleteListener callback) {
        WriteBatch batch = db.batch();

        Crop tomato = new Crop(
                "tomato",
                "Tomato",
                "Solanum lycopersicum",
                "Fruit Vegetable",
                "\ud83c\udf45",
                "Popular urban garden crop requiring full sunlight and well-draining soil.",
                6.0, 6.8,   // phMin, phMax
                30.0, 70.0, // moistureMin, moistureMax
                5.0, 80.0,  // nitrogenMin, nitrogenMax
                5.0, 60.0,  // phosphorusMin, phosphorusMax
                15.0, 150.0, // potassiumMin, potassiumMax
                TOMATO_TIPS
        );

        Crop pepper = new Crop(
                "green_pepper",
                "Green Pepper",
                "Capsicum annuum",
                "Fruit Vegetable",
                "\ud83f\uded1",
                "Warm-season crop thriving in fertile, warm, moisture-retentive soil.",
                6.0, 7.0,   // phMin, phMax
                30.0, 70.0, // moistureMin, moistureMax
                5.0, 80.0,  // nitrogenMin, nitrogenMax
                5.0, 60.0,  // phosphorusMin, phosphorusMax
                15.0, 150.0, // potassiumMin, potassiumMax
                PEPPER_TIPS
        );

        batch.set(db.collection("crops").document(tomato.getId()), tomato);
        batch.set(db.collection("crops").document(pepper.getId()), pepper);

        batch.commit().addOnCompleteListener(task -> {
            seedDefaultSensorDeviceIfMissing(db, callback);
        });
    }
}
