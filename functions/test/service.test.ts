import { test } from "node:test";
import assert from "node:assert/strict";
import type { ClaudeResult } from "../src/claude";
import { USER_COOLDOWN_SECONDS, USER_DAILY_CAP, GLOBAL_DAILY_CAP } from "../src/config";
import { AppError } from "../src/errors";
import { decideQuota } from "../src/rateLimit";
import { getRecommendationsCore, type CacheDoc, type Deps } from "../src/service";
import { NOW, sampleRaw, validOutput } from "./fixtures";

const USAGE = { input: 100, output: 50, cacheRead: 0, cacheWrite: 0 };

function fakeDeps(replies: Array<unknown | Error>, overrides: Partial<Deps> = {}) {
  const state = { cache: null as CacheDoc | null, calls: 0 };
  const deps: Deps = {
    now: () => NOW,
    loadRaw: async () => sampleRaw(),
    readCache: async () => state.cache,
    writeCache: async (_uid, doc) => {
      state.cache = doc;
    },
    readUsage: async () => undefined,
    reserveQuota: async () => 9,
    recordUsage: async () => undefined,
    generate: async (): Promise<ClaudeResult> => {
      const reply = replies[state.calls++];
      if (reply instanceof Error) throw reply;
      return { output: reply, stopReason: "end_turn", usage: USAGE };
    },
    log: () => undefined,
    ...overrides,
  };
  return { deps, state };
}

test("first call asks Claude and caches; second call is served from cache", async () => {
  const { deps, state } = fakeDeps([validOutput()]);
  const first = await getRecommendationsCore(deps, "u1", false);
  assert.equal(first.cached, false);
  assert.equal(first.recommendations.length, 1);
  assert.equal(state.calls, 1);

  const second = await getRecommendationsCore(deps, "u1", false);
  assert.equal(second.cached, true);
  assert.equal(state.calls, 1, "no second Claude call");
});

test("forceRefresh bypasses the cache", async () => {
  const { deps, state } = fakeDeps([validOutput(), validOutput()]);
  await getRecommendationsCore(deps, "u1", false);
  await getRecommendationsCore(deps, "u1", true);
  assert.equal(state.calls, 2);
});

test("no soil data: answers insufficient_data without calling Claude", async () => {
  const { deps, state } = fakeDeps([], { loadRaw: async () => sampleRaw({ sensor: null, soilLogs: [] }) });
  const res = await getRecommendationsCore(deps, "u1", false);
  assert.equal(res.status, "insufficient_data");
  assert.equal(state.calls, 0);
  assert.ok(res.missingData.length > 0);
});

test("malformed output is retried once, then succeeds", async () => {
  const { deps, state } = fakeDeps([{ nonsense: true }, validOutput()]);
  const res = await getRecommendationsCore(deps, "u1", false);
  assert.equal(state.calls, 2);
  assert.equal(res.status, "ok");
});

test("two malformed outputs give a clean invalid_output error", async () => {
  const { deps } = fakeDeps([{ nonsense: true }, { nonsense: true }]);
  await assert.rejects(getRecommendationsCore(deps, "u1", false), (e: AppError) => e.reason === "invalid_output");
});

test("unparseable reply (SDK error) is also retried once", async () => {
  const bad = new AppError("internal", "invalid_output", "bad json");
  const { deps, state } = fakeDeps([bad, validOutput()]);
  await getRecommendationsCore(deps, "u1", false);
  assert.equal(state.calls, 2);
});

test("upstream rate limit is not retried by us and maps to resource-exhausted", async () => {
  const limited = new AppError("resource-exhausted", "upstream_rate_limit", "busy", 60);
  const { deps, state } = fakeDeps([limited]);
  await assert.rejects(getRecommendationsCore(deps, "u1", false), (e: AppError) => e.code === "resource-exhausted");
  assert.equal(state.calls, 1);
});

test("rate-limited user with an older cache gets the cache instead of an error", async () => {
  const { deps, state } = fakeDeps([validOutput()]);
  await getRecommendationsCore(deps, "u1", false);
  // Inputs change (new pH), but the user is in cooldown.
  deps.loadRaw = async () => {
    const raw = sampleRaw();
    raw.sensor = { ...raw.sensor!, ph: 6.6 };
    return raw;
  };
  deps.reserveQuota = async () => {
    throw new AppError("resource-exhausted", "cooldown", "wait", 30);
  };
  const res = await getRecommendationsCore(deps, "u1", false);
  assert.equal(res.cached, true);
  assert.equal(state.calls, 1);
  // But an explicit refresh surfaces the error so the app can say "wait".
  await assert.rejects(getRecommendationsCore(deps, "u1", true), (e: AppError) => e.reason === "cooldown");
});

test("missing user profile is a clean error", async () => {
  const { deps } = fakeDeps([], { loadRaw: async () => sampleRaw({ user: null }) });
  await assert.rejects(getRecommendationsCore(deps, "u1", false), (e: AppError) => e.reason === "no_profile");
});

test("quota rules: cooldown, daily cap, global cap, new day resets", () => {
  const today = "2026-11-06";
  assert.equal(decideQuota(undefined, 0, NOW, today).allowed, true);
  const recent = decideQuota({ day: today, count: 1, lastCallAt: NOW - 1000 }, 0, NOW, today);
  assert.equal(recent.allowed, false);
  assert.equal(!recent.allowed && recent.reason, "cooldown");
  const old = NOW - (USER_COOLDOWN_SECONDS + 1) * 1000;
  const capped = decideQuota({ day: today, count: USER_DAILY_CAP, lastCallAt: old }, 0, NOW, today);
  assert.equal(!capped.allowed && capped.reason, "daily_cap");
  const yesterday = decideQuota({ day: "2026-11-05", count: USER_DAILY_CAP, lastCallAt: old }, 0, NOW, today);
  assert.equal(yesterday.allowed, true);
  const global = decideQuota(undefined, GLOBAL_DAILY_CAP, NOW, today);
  assert.equal(!global.allowed && global.reason, "global_cap");
});
