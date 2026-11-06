import type Anthropic from "@anthropic-ai/sdk";
import type { ClaudeResult, TokenUsage } from "./claude";
import { CACHE_MAX_AGE_MS, CLAUDE_MODEL, DISCLAIMER } from "./config";
import { buildContext, manilaDate, type RawData } from "./data/context";
import { fingerprint } from "./data/fingerprint";
import { AppError } from "./errors";
import { buildUserContent } from "./prompt/buildPrompt";
import { remainingToday, type UsageState } from "./rateLimit";
import type { RecommendationsResponse } from "./schema";
import { scoreCrops } from "./scoring";
import { validateOutput } from "./validate";

/** What is stored at users/{uid}/ai_recommendations/latest. */
export type CacheDoc = Omit<RecommendationsResponse, "cached" | "quota"> & { fingerprint: string };

/** Everything with side effects, injected so the flow can be tested with fakes. */
export interface Deps {
  now(): number;
  loadRaw(uid: string): Promise<RawData>;
  readCache(uid: string): Promise<CacheDoc | null>;
  writeCache(uid: string, doc: CacheDoc): Promise<void>;
  readUsage(uid: string): Promise<UsageState | undefined>;
  reserveQuota(uid: string, nowMs: number, today: string): Promise<number>;
  recordUsage(uid: string, today: string, usage: TokenUsage): Promise<void>;
  generate(content: Anthropic.TextBlockParam[]): Promise<ClaudeResult>;
  log(event: string, data: Record<string, unknown>): void;
}

const MAX_ATTEMPTS = 2; // first try + one retry on malformed output

export async function getRecommendationsCore(
  deps: Deps,
  uid: string,
  forceRefresh: boolean,
): Promise<RecommendationsResponse> {
  const now = deps.now();
  const today = manilaDate(now);

  const raw = await deps.loadRaw(uid);
  if (!raw.user) {
    throw new AppError("failed-precondition", "no_profile", "Your profile was not found. Please sign in again.");
  }
  const ctx = buildContext(raw, now);
  const fp = fingerprint(ctx);
  const cache = await deps.readCache(uid);

  const respond = (doc: CacheDoc, cached: boolean, remaining: number): RecommendationsResponse => {
    const { fingerprint: _fp, ...rest } = doc;
    return { ...rest, cached, quota: { remainingToday: remaining } };
  };

  // 1. Same inputs as last time and still fresh: reuse it, no Claude call.
  if (!forceRefresh && cache && cache.fingerprint === fp && now - cache.generatedAt < CACHE_MAX_AGE_MS) {
    deps.log("cache_hit", {});
    return respond(cache, true, remainingToday(await deps.readUsage(uid), today));
  }

  // 2. Nothing to ground advice on: say so for free instead of asking Claude to guess.
  if (!ctx.currentReading || ctx.crops.length === 0) {
    const missing: string[] = [];
    if (!ctx.currentReading) {
      missing.push("No soil reading yet. Connect your sensor or save a reading on the Soil Monitoring screen.");
    }
    if (ctx.crops.length === 0) missing.push("The crop database is empty.");
    const doc: CacheDoc = {
      status: "insufficient_data",
      generatedAt: now,
      model: "none",
      soilStatus: "unknown",
      summary: "There isn't enough garden data yet to give grounded recommendations.",
      recommendations: [],
      missingData: missing,
      disclaimer: DISCLAIMER,
      fingerprint: fp,
    };
    await deps.writeCache(uid, doc);
    return respond(doc, false, remainingToday(await deps.readUsage(uid), today));
  }

  // 3. Paid call: reserve quota first.
  let remaining: number;
  try {
    remaining = await deps.reserveQuota(uid, now, today);
  } catch (err) {
    // Rate limited but we have an older answer and the user didn't ask to force: show it.
    if (err instanceof AppError && err.code === "resource-exhausted" && cache && !forceRefresh) {
      return respond(cache, true, remainingToday(await deps.readUsage(uid), today));
    }
    throw err;
  }

  const content = buildUserContent(ctx, scoreCrops(ctx.currentReading, ctx.crops));
  const cropNames = ctx.crops.map((c) => c.name);

  for (let attempt = 1; attempt <= MAX_ATTEMPTS; attempt++) {
    let result: ClaudeResult;
    try {
      result = await deps.generate(content);
    } catch (err) {
      if (err instanceof AppError && err.reason === "invalid_output" && attempt < MAX_ATTEMPTS) {
        deps.log("invalid_output_retry", { attempt });
        continue;
      }
      throw err;
    }

    await deps.recordUsage(uid, today, result.usage).catch(() => undefined);
    deps.log("claude_call", {
      attempt,
      stopReason: result.stopReason,
      inputTokens: result.usage.input,
      outputTokens: result.usage.output,
      cacheReadTokens: result.usage.cacheRead,
    });

    const checked = validateOutput(result.output, cropNames);
    if (checked.ok) {
      const v = checked.value;
      const doc: CacheDoc = {
        status: v.status,
        generatedAt: now,
        model: CLAUDE_MODEL,
        soilStatus: v.soil_status,
        summary: v.summary,
        recommendations: v.recommendations,
        missingData: v.missing_data,
        disclaimer: DISCLAIMER,
        fingerprint: fp,
      };
      await deps.writeCache(uid, doc);
      return respond(doc, false, remaining);
    }
    deps.log("invalid_output", { attempt, problem: checked.problem });
  }

  throw new AppError("internal", "invalid_output", "The AI returned an unusable answer. Please try again.");
}
