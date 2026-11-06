import {
  MAX_ADMIN_RULES,
  MAX_CROPS,
  MAX_HARVEST_LOGS,
  MAX_SOIL_LOGS,
  SENSOR_STALE_MS,
  TIME_ZONE,
} from "../config";

/** Firestore documents as plain objects (whatever shape is stored). */
export type RawDoc = Record<string, unknown>;

export interface RawData {
  user: RawDoc | null;
  sensor: RawDoc | null;
  soilLogs: RawDoc[];
  harvestLogs: RawDoc[];
  crops: RawDoc[];
  adminRules: RawDoc[];
}

export interface Reading {
  moisture: number | null;
  ph: number | null;
  nitrogen: number | null;
  phosphorus: number | null;
  potassium: number | null;
  /** yyyy-mm-dd in Manila time, or null if unknown. */
  date: string | null;
}

export interface CurrentReading extends Reading {
  source: "sensor" | "saved_log";
  stale: boolean;
}

export interface CropInfo {
  name: string;
  category?: string;
  description?: string;
  ranges: Partial<Record<"moisture" | "ph" | "nitrogen" | "phosphorus" | "potassium", [number, number]>>;
  tipThisMonth?: string;
}

export interface GardenContext {
  today: string;
  month: string;
  season: "dry season" | "wet season";
  profile: { barangay?: string; gardenType?: string; crops: string[] };
  currentReading: CurrentReading | null;
  recentSoilLogs: Array<Reading & { crop?: string; status?: string }>;
  recentHarvests: Array<{ date: string | null; crop?: string; kg: number | null; notes?: string }>;
  adminRules: Array<Record<string, string | string[]>>;
  crops: CropInfo[];
}

// ------------------------------------------------------------- sanitizing

// Control characters, zero-width characters, line/paragraph separators and
// bidi overrides. Built from a string because some of these are line
// terminators and would break a regex literal after compilation.
const INVISIBLE_CHARS = new RegExp(
  "[\\u0000-\\u001F\\u007F-\\u009F\\u200B-\\u200F\\u2028-\\u202E\\u2066-\\u2069]",
  "g",
);

/**
 * Makes a user- or admin-written string safe to place in the prompt:
 * removes control characters and angle brackets (so text cannot fake a
 * </user_data> tag), collapses whitespace and limits the length.
 * Returns undefined for empty or non-string values.
 */
export function cleanText(value: unknown, max: number): string | undefined {
  if (typeof value !== "string") return undefined;
  const cleaned = value
    .replace(INVISIBLE_CHARS, " ")
    .replace(/[<>]/g, " ")
    .replace(/\s+/g, " ")
    .trim();
  if (!cleaned) return undefined;
  return cleaned.length > max ? cleaned.slice(0, max).trimEnd() + "…" : cleaned;
}

/** A finite number inside [min, max], else null (bad sensor values become null). */
export function cleanNumber(value: unknown, min: number, max: number): number | null {
  const n = typeof value === "number" ? value : typeof value === "string" ? Number(value) : NaN;
  if (!Number.isFinite(n) || n < min || n > max) return null;
  return Math.round(n * 100) / 100;
}

/** Accepts epoch millis (number or numeric string) or a Firestore Timestamp. */
export function toMillis(value: unknown): number | null {
  if (typeof value === "number" && Number.isFinite(value)) return value;
  if (typeof value === "string" && /^\d+$/.test(value)) return Number(value);
  if (value && typeof (value as { toMillis?: unknown }).toMillis === "function") {
    return (value as { toMillis: () => number }).toMillis();
  }
  return null;
}

export function manilaDate(ms: number): string {
  // en-CA formats as yyyy-mm-dd
  return new Intl.DateTimeFormat("en-CA", { timeZone: TIME_ZONE }).format(new Date(ms));
}

function readingFrom(doc: RawDoc): Reading {
  const ms = toMillis(doc.timestamp);
  return {
    moisture: cleanNumber(doc.moisture, 0, 100),
    ph: cleanNumber(doc.ph, 0, 14),
    nitrogen: cleanNumber(doc.nitrogen, 0, 5000),
    phosphorus: cleanNumber(doc.phosphorus, 0, 5000),
    potassium: cleanNumber(doc.potassium, 0, 5000),
    date: ms !== null && ms > 1_000_000_000_000 ? manilaDate(ms) : null,
  };
}

function hasAnyValue(r: Reading): boolean {
  return [r.moisture, r.ph, r.nitrogen, r.phosphorus, r.potassium].some((v) => v !== null);
}

function range(min: unknown, max: unknown, lo: number, hi: number): [number, number] | undefined {
  let a = cleanNumber(min, lo, hi);
  let b = cleanNumber(max, lo, hi);
  if (a === null || b === null || (a === 0 && b === 0)) return undefined;
  if (a > b) [a, b] = [b, a];
  return [a, b];
}

function stringList(value: unknown, maxItems: number, maxLen: number): string[] {
  if (!Array.isArray(value)) return [];
  return value
    .map((v) => cleanText(v, maxLen))
    .filter((v): v is string => v !== undefined)
    .slice(0, maxItems);
}

// ----------------------------------------------------------- build context

/**
 * Turns raw Firestore data into the compact, sanitized context sent to Claude.
 * Only allow-listed fields are copied: no email, names, uid or device id.
 */
export function buildContext(raw: RawData, nowMs: number): GardenContext {
  const monthIndex = Number(
    new Intl.DateTimeFormat("en-US", { timeZone: TIME_ZONE, month: "numeric" }).format(new Date(nowMs)),
  ) - 1;
  const monthName = new Intl.DateTimeFormat("en-US", { timeZone: TIME_ZONE, month: "long" }).format(
    new Date(nowMs),
  );
  const wet = monthIndex >= 5 && monthIndex <= 9; // June-October

  const user = raw.user ?? {};
  const profile = {
    barangay: cleanText(user.barangay, 60),
    gardenType: cleanText(user.gardenType, 60),
    crops: stringList(user.crops, 15, 40),
  };

  const soilLogs = raw.soilLogs.slice(0, MAX_SOIL_LOGS).map((d) => ({
    ...readingFrom(d),
    crop: cleanText(d.cropName, 40),
    status: cleanText(d.overallStatus, 40),
  }));

  // Current reading: the newest of the live sensor and the latest saved log.
  let currentReading: CurrentReading | null = null;
  const sensorMs = raw.sensor ? toMillis(raw.sensor.timestamp) : null;
  const sensorReading = raw.sensor ? readingFrom(raw.sensor) : null;
  const latestLog = raw.soilLogs[0];
  const logMs = latestLog ? toMillis(latestLog.timestamp) : null;

  const useSensor =
    sensorReading !== null &&
    hasAnyValue(sensorReading) &&
    (logMs === null || (sensorMs !== null && sensorMs >= logMs));

  if (useSensor && sensorReading) {
    currentReading = {
      ...sensorReading,
      source: "sensor",
      stale: sensorMs === null || nowMs - sensorMs > SENSOR_STALE_MS,
    };
  } else if (latestLog && hasAnyValue(readingFrom(latestLog))) {
    currentReading = {
      ...readingFrom(latestLog),
      source: "saved_log",
      stale: logMs === null || nowMs - logMs > SENSOR_STALE_MS,
    };
  }

  const recentHarvests = raw.harvestLogs.slice(0, MAX_HARVEST_LOGS).map((d) => {
    const ms = toMillis(d.timestamp);
    return {
      date: ms !== null ? manilaDate(ms) : null,
      crop: cleanText(d.cropName, 40),
      kg: cleanNumber(d.quantityKg, 0, 10_000),
      notes: cleanText(d.notes, 200),
    };
  });

  const adminRules = raw.adminRules.slice(0, MAX_ADMIN_RULES).map((d) => {
    const rule: Record<string, string | string[]> = {};
    for (const key of ["title", "parameter", "condition", "status", "severity"] as const) {
      const v = cleanText(d[key], 80);
      if (v) rule[key] = v;
    }
    const advice = cleanText(d.advice, 300);
    if (advice) rule.advice = advice;
    const crops = stringList(d.applicableCrops, 20, 40);
    if (crops.length) rule.applicableCrops = crops;
    return rule;
  }).filter((r) => Object.keys(r).length > 0);

  const crops: CropInfo[] = raw.crops
    .map((d): CropInfo | null => {
      const name = cleanText(d.name, 40);
      if (!name) return null;
      const tips = Array.isArray(d.monthlyTips) ? d.monthlyTips : [];
      return {
        name,
        category: cleanText(d.category, 40),
        description: cleanText(d.description, 200),
        ranges: {
          moisture: range(d.moistureMin, d.moistureMax, 0, 100),
          ph: range(d.phMin, d.phMax, 0, 14),
          nitrogen: range(d.nitrogenMin, d.nitrogenMax, 0, 5000),
          phosphorus: range(d.phosphorusMin, d.phosphorusMax, 0, 5000),
          potassium: range(d.potassiumMin, d.potassiumMax, 0, 5000),
        },
        tipThisMonth: tips.length === 12 ? cleanText(tips[monthIndex], 200) : undefined,
      };
    })
    .filter((c): c is CropInfo => c !== null)
    // Sorted so the crop block is identical for every user (better prompt caching).
    .sort((a, b) => a.name.localeCompare(b.name))
    .slice(0, MAX_CROPS);

  return {
    today: manilaDate(nowMs),
    month: monthName,
    season: wet ? "wet season" : "dry season",
    profile,
    currentReading,
    recentSoilLogs: soilLogs,
    recentHarvests,
    adminRules,
    crops,
  };
}
