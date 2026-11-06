import { createHash } from "node:crypto";
import { CLAUDE_MODEL } from "../config";
import type { GardenContext, Reading } from "./context";

/** Bump when the prompt or output format changes, so old caches are ignored. */
export const PROMPT_VERSION = 1;

function bucket(value: number | null, step: number): number | null {
  return value === null ? null : Math.round(value / step) * step;
}

function bucketReading(r: Reading) {
  return {
    m: bucket(r.moisture, 5),
    ph: bucket(r.ph, 0.2),
    n: bucket(r.nitrogen, 5),
    p: bucket(r.phosphorus, 5),
    k: bucket(r.potassium, 5),
  };
}

/**
 * A hash of the inputs that matter. If it hasn't changed, the cached
 * recommendation is still valid and Claude is not called.
 * Sensor values are rounded into buckets, so tiny fluctuations
 * (moisture 61% -> 62%) don't cause a new paid call.
 */
export function fingerprint(ctx: GardenContext): string {
  const material = {
    v: PROMPT_VERSION,
    model: CLAUDE_MODEL,
    month: ctx.month,
    profile: ctx.profile,
    reading: ctx.currentReading
      ? { ...bucketReading(ctx.currentReading), src: ctx.currentReading.source, stale: ctx.currentReading.stale }
      : null,
    logs: ctx.recentSoilLogs.map((l) => ({ ...bucketReading(l), d: l.date, c: l.crop })),
    harvests: ctx.recentHarvests,
    rules: ctx.adminRules,
    crops: ctx.crops,
  };
  return createHash("sha256").update(JSON.stringify(material)).digest("hex");
}
