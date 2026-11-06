import { test } from "node:test";
import assert from "node:assert/strict";
import { buildContext, cleanNumber } from "../src/data/context";
import { fingerprint } from "../src/data/fingerprint";
import { buildUserContent } from "../src/prompt/buildPrompt";
import { scoreCrops } from "../src/scoring";
import { NOW, sampleRaw } from "./fixtures";

function promptText(raw = sampleRaw()) {
  const ctx = buildContext(raw, NOW);
  const blocks = buildUserContent(ctx, ctx.currentReading ? scoreCrops(ctx.currentReading, ctx.crops) : []);
  return { ctx, blocks, text: blocks.map((b) => b.text).join("\n") };
}

test("never sends email, names, uid or device id", () => {
  const { text } = promptText();
  for (const secret of ["someone@example.com", "Juan", "VINLAND-ESP32-01", "role"]) {
    assert.ok(!text.includes(secret), `prompt leaked: ${secret}`);
  }
  assert.ok(text.includes("Batasan Hills"));
});

test("crop block comes first, is cache-marked, and is sorted by name", () => {
  const { blocks } = promptText();
  assert.equal(blocks.length, 2);
  assert.deepEqual(blocks[0].cache_control, { type: "ephemeral" });
  assert.ok(blocks[0].text.startsWith("<crop_database>"));
  const names = JSON.parse(blocks[0].text.replace(/<\/?crop_database>/g, "")).map((c: { name: string }) => c.name);
  assert.deepEqual(names, ["Kangkong", "Pechay", "Tomato"]);
});

test("picks the newest reading and marks season", () => {
  const { ctx } = promptText();
  assert.equal(ctx.currentReading?.source, "sensor");
  assert.equal(ctx.currentReading?.stale, false);
  assert.equal(ctx.season, "dry season"); // November
  assert.equal(ctx.today, "2026-11-06");
});

test("an old sensor reading is marked stale", () => {
  const raw = sampleRaw({ soilLogs: [], sensor: { moisture: 40, ph: 6, timestamp: NOW - 100 * 3600_000 } });
  assert.equal(buildContext(raw, NOW).currentReading?.stale, true);
});

test("impossible sensor values become null", () => {
  assert.equal(cleanNumber(99, 0, 14), null);
  assert.equal(cleanNumber("abc", 0, 14), null);
  assert.equal(cleanNumber(Number.NaN, 0, 14), null);
  assert.equal(cleanNumber("6.5", 0, 14), 6.5);
});

test("no reading at all gives a null current reading", () => {
  const ctx = buildContext(sampleRaw({ sensor: null, soilLogs: [] }), NOW);
  assert.equal(ctx.currentReading, null);
});

test("scoring ranks the best-fitting crop first and explains misses", () => {
  const { ctx } = promptText();
  const fits = scoreCrops(ctx.currentReading!, ctx.crops);
  for (let i = 1; i < fits.length; i++) assert.ok(fits[i - 1].score >= fits[i].score);
  assert.ok(fits.every((f) => f.score >= 0 && f.score <= 100));
  assert.deepEqual(fits.find((f) => f.crop === "Kangkong")!.outOfRange, ["Moisture 42 below 60-100"]);
  assert.ok(fits.find((f) => f.crop === "Pechay")!.outOfRange.some((s) => s.startsWith("pH 5.2 below")));
});

test("fingerprint ignores tiny sensor changes but not real ones", () => {
  const a = fingerprint(buildContext(sampleRaw(), NOW));
  const tiny = sampleRaw();
  tiny.sensor = { ...tiny.sensor!, moisture: 41 }; // same 5% bucket as 42
  const big = sampleRaw();
  big.sensor = { ...big.sensor!, ph: 6.5 };
  assert.equal(fingerprint(buildContext(tiny, NOW)), a);
  assert.notEqual(fingerprint(buildContext(big, NOW)), a);
});
