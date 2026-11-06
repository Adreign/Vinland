import { test } from "node:test";
import assert from "node:assert/strict";
import { validateOutput } from "../src/validate";
import { validOutput } from "./fixtures";

const CROPS = ["Pechay", "Tomato", "Kangkong"];

test("accepts a valid answer", () => {
  const r = validateOutput(validOutput(), CROPS);
  assert.equal(r.ok, true);
});

test("drops recommendations for crops that are not in the database", () => {
  const out = validOutput();
  out.recommendations.push({ ...out.recommendations[0], title: "Plant durian", crop: "Durian", category: "crop" });
  const r = validateOutput(out, CROPS);
  assert.ok(r.ok);
  assert.equal(r.value.recommendations.length, 1);
  assert.equal(r.dropped, 1);
});

test("uses the database spelling of a crop name", () => {
  const out = validOutput();
  out.recommendations[0].crop = "  pechay ";
  const r = validateOutput(out, CROPS);
  assert.ok(r.ok);
  assert.equal(r.value.recommendations[0].crop, "Pechay");
});

test("fails (so the caller retries) when no valid recommendation is left", () => {
  const out = validOutput();
  out.recommendations[0].crop = "Unicorn plant";
  const r = validateOutput(out, CROPS);
  assert.deepEqual(r, { ok: false, problem: "no_valid_recommendations" });
});

test("fails on the wrong shape", () => {
  assert.equal(validateOutput({ hello: "world" }, CROPS).ok, false);
  assert.equal(validateOutput(null, CROPS).ok, false);
  const badPriority = validOutput() as Record<string, unknown>;
  (badPriority.recommendations as Array<Record<string, unknown>>)[0].priority = "urgent";
  assert.equal(validateOutput(badPriority, CROPS).ok, false);
});

test("caps at 5, removes duplicates, sorts high priority first, clips long text", () => {
  const base = validOutput().recommendations[0];
  const recs = [
    { ...base, title: "Low one", priority: "low" },
    { ...base, title: "Low one", priority: "low" }, // duplicate
    ...[1, 2, 3, 4, 5].map((i) => ({ ...base, title: `Item ${i}`, priority: "medium" })),
    { ...base, title: "Urgent", priority: "high", reason: "x ".repeat(400) },
  ];
  const r = validateOutput({ ...validOutput(), recommendations: recs }, CROPS);
  assert.ok(r.ok);
  assert.equal(r.value.recommendations.length, 5);
  assert.equal(r.value.recommendations[0].title, "Urgent");
  assert.ok(r.value.recommendations[0].reason.length <= 300);
});

test("insufficient_data with an explanation and no items is valid", () => {
  const r = validateOutput(
    { ...validOutput(), status: "insufficient_data", recommendations: [], missing_data: ["No pH reading"] },
    CROPS,
  );
  assert.equal(r.ok, true);
});
