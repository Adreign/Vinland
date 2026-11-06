import type { Firestore } from "firebase-admin/firestore";
import { MAX_ADMIN_RULES, MAX_CROPS, MAX_HARVEST_LOGS, MAX_SOIL_LOGS } from "../config";
import type { RawData, RawDoc } from "./context";

/**
 * Reads everything the recommendation needs, server-side, with the Admin SDK.
 * Nothing the app sends is trusted; the uid comes from the verified auth token.
 */
export async function loadRawData(db: Firestore, uid: string): Promise<RawData> {
  const userRef = db.collection("users").doc(uid);

  const [userSnap, soilSnap, harvestSnap, cropsSnap, rulesSnap] = await Promise.all([
    userRef.get(),
    userRef.collection("soil_logs").orderBy("timestamp", "desc").limit(MAX_SOIL_LOGS).get(),
    userRef.collection("harvest_logs").orderBy("timestamp", "desc").limit(MAX_HARVEST_LOGS).get(),
    db.collection("crops").limit(MAX_CROPS * 2).get(),
    db.collection("recommendations").limit(MAX_ADMIN_RULES).get(),
  ]);

  const user = (userSnap.data() as RawDoc | undefined) ?? null;

  let sensor: RawDoc | null = null;
  const deviceId = typeof user?.sensorDeviceId === "string" ? user.sensorDeviceId.trim() : "";
  // Firestore doc ids cannot contain "/"; anything else is looked up as-is.
  if (deviceId && !deviceId.includes("/") && deviceId.length <= 100) {
    const sensorSnap = await db.collection("sensor_devices").doc(deviceId).get();
    sensor = (sensorSnap.data() as RawDoc | undefined) ?? null;
  }

  return {
    user,
    sensor,
    soilLogs: soilSnap.docs.map((d) => d.data() as RawDoc),
    harvestLogs: harvestSnap.docs.map((d) => d.data() as RawDoc),
    crops: cropsSnap.docs.map((d) => d.data() as RawDoc),
    adminRules: rulesSnap.docs.map((d) => d.data() as RawDoc),
  };
}
