import { FieldValue, type Firestore } from "firebase-admin/firestore";
import { GLOBAL_DAILY_CAP, USER_COOLDOWN_SECONDS, USER_DAILY_CAP } from "./config";
import { AppError } from "./errors";
import type { TokenUsage } from "./claude";

export interface UsageState {
  day?: string;
  count?: number;
  lastCallAt?: number;
}

export type QuotaDecision =
  | { allowed: true; countAfter: number }
  | { allowed: false; reason: "cooldown" | "daily_cap" | "global_cap"; retryAfterSeconds?: number };

/** Pure decision logic, unit-tested separately from Firestore. */
export function decideQuota(
  user: UsageState | undefined,
  globalCountToday: number,
  nowMs: number,
  today: string,
): QuotaDecision {
  const countToday = user?.day === today ? user.count ?? 0 : 0;
  const sinceLast = user?.lastCallAt ? (nowMs - user.lastCallAt) / 1000 : Infinity;

  if (sinceLast < USER_COOLDOWN_SECONDS) {
    return { allowed: false, reason: "cooldown", retryAfterSeconds: Math.ceil(USER_COOLDOWN_SECONDS - sinceLast) };
  }
  if (countToday >= USER_DAILY_CAP) return { allowed: false, reason: "daily_cap" };
  if (globalCountToday >= GLOBAL_DAILY_CAP) return { allowed: false, reason: "global_cap" };
  return { allowed: true, countAfter: countToday + 1 };
}

export function remainingToday(user: UsageState | undefined, today: string): number {
  const used = user?.day === today ? user.count ?? 0 : 0;
  return Math.max(0, USER_DAILY_CAP - used);
}

/**
 * Atomically reserves one Claude call for this user (before calling Claude, so
 * parallel requests can't slip past the cap). Returns calls left today.
 */
export async function reserveQuota(db: Firestore, uid: string, nowMs: number, today: string): Promise<number> {
  const userRef = db.collection("ai_usage").doc(uid);
  const globalRef = db.collection("ai_usage_global").doc(today);

  return db.runTransaction(async (tx) => {
    const [userSnap, globalSnap] = await Promise.all([tx.get(userRef), tx.get(globalRef)]);
    const state = userSnap.data() as UsageState | undefined;
    const globalCount = (globalSnap.data()?.count as number | undefined) ?? 0;
    const decision = decideQuota(state, globalCount, nowMs, today);

    if (!decision.allowed) {
      const messages = {
        cooldown: "Please wait a moment before refreshing again.",
        daily_cap: "You've reached today's limit for AI recommendations. Try again tomorrow.",
        global_cap: "AI recommendations are paused for today. Try again tomorrow.",
      };
      throw new AppError("resource-exhausted", decision.reason, messages[decision.reason], decision.retryAfterSeconds);
    }

    tx.set(userRef, { day: today, count: decision.countAfter, lastCallAt: nowMs }, { merge: true });
    tx.set(globalRef, { count: FieldValue.increment(1) }, { merge: true });
    return USER_DAILY_CAP - decision.countAfter;
  });
}

/** Token counts only (no content), for comparing cost estimates with the real bill. */
export async function recordUsage(db: Firestore, uid: string, today: string, usage: TokenUsage): Promise<void> {
  const inc = {
    inputTokens: FieldValue.increment(usage.input),
    outputTokens: FieldValue.increment(usage.output),
    cacheReadTokens: FieldValue.increment(usage.cacheRead),
    cacheWriteTokens: FieldValue.increment(usage.cacheWrite),
  };
  await Promise.all([
    db.collection("ai_usage").doc(uid).set(inc, { merge: true }),
    db.collection("ai_usage_global").doc(today).set(inc, { merge: true }),
  ]);
}

export async function readUsage(db: Firestore, uid: string): Promise<UsageState | undefined> {
  return (await db.collection("ai_usage").doc(uid).get()).data() as UsageState | undefined;
}
