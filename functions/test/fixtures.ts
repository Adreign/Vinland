import type { RawData } from "../src/data/context";

/** 2026-11-06 10:00 Manila time. */
export const NOW = Date.UTC(2026, 10, 6, 2, 0, 0);
const HOUR = 60 * 60 * 1000;

export function sampleRaw(overrides: Partial<RawData> = {}): RawData {
  return {
    user: {
      email: "someone@example.com",
      fullName: "Juan Dela Cruz",
      firstName: "Juan",
      barangay: "Batasan Hills",
      gardenType: "Container",
      crops: ["Pechay", "Tomato"],
      sensorDeviceId: "VINLAND-ESP32-01",
      role: "user",
    },
    sensor: { moisture: 42, ph: 5.2, nitrogen: 20, phosphorus: 15, potassium: 80, timestamp: NOW - 2 * HOUR },
    soilLogs: [
      { cropName: "Pechay", moisture: 50, ph: 5.4, nitrogen: 22, phosphorus: 14, potassium: 70, overallStatus: "Fair", timestamp: NOW - 48 * HOUR },
    ],
    harvestLogs: [{ cropName: "Pechay", quantityKg: 1.5, notes: "Good leaves", timestamp: NOW - 240 * HOUR }],
    crops: [
      { name: "Tomato", category: "Fruit", phMin: 6, phMax: 6.8, moistureMin: 40, moistureMax: 70, nitrogenMin: 10, nitrogenMax: 50, phosphorusMin: 10, phosphorusMax: 40, potassiumMin: 50, potassiumMax: 150 },
      { name: "Pechay", category: "Leafy", phMin: 6, phMax: 7, moistureMin: 50, moistureMax: 80, nitrogenMin: 15, nitrogenMax: 60, phosphorusMin: 5, phosphorusMax: 30, potassiumMin: 30, potassiumMax: 120 },
      { name: "Kangkong", category: "Leafy", phMin: 5, phMax: 7, moistureMin: 60, moistureMax: 100 },
    ],
    adminRules: [{ title: "Acidic soil", parameter: "ph", condition: "< 5.5", advice: "Add agricultural lime", severity: "high" }],
    ...overrides,
  };
}

export function validOutput() {
  return {
    status: "ok",
    soil_status: "action_needed",
    summary: "Your soil is acidic for most of your crops.",
    recommendations: [
      {
        title: "Raise pH before planting pechay",
        reason: "pH 5.2 is below pechay's 6-7 range.",
        priority: "high",
        suggested_action: "Mix 1 tbsp agricultural lime per pot and re-test in 7 days.",
        category: "soil",
        crop: "Pechay",
      },
    ],
    missing_data: [],
  };
}
