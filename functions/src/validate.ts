import { MAX_RECOMMENDATIONS } from "./config";
import { ModelOutput, ModelOutputSchema, ModelRecommendation } from "./schema";

const LIMITS = { title: 80, reason: 300, suggested_action: 300, summary: 300, missing: 160 };
const PRIORITY_ORDER = { high: 0, medium: 1, low: 2 } as const;

export type ValidationResult =
  | { ok: true; value: ModelOutput; dropped: number }
  | { ok: false; problem: string };

/** Trims, collapses whitespace and cuts at a word boundary. */
export function clip(text: string, max: number): string {
  const clean = text.replace(/\s+/g, " ").trim();
  if (clean.length <= max) return clean;
  const cut = clean.slice(0, max - 1);
  const lastSpace = cut.lastIndexOf(" ");
  return (lastSpace > max * 0.6 ? cut.slice(0, lastSpace) : cut) + "…";
}

/**
 * Checks Claude's parsed output before it reaches the app:
 * - re-validates the shape (defence in depth),
 * - drops items naming a crop that is not in the crop database,
 * - drops empty and duplicate items, clips text lengths, caps the count,
 * - sorts by priority.
 * Returns ok:false when nothing usable is left, so the caller can retry once.
 */
export function validateOutput(raw: unknown, cropNames: string[]): ValidationResult {
  const parsed = ModelOutputSchema.safeParse(raw);
  if (!parsed.success) return { ok: false, problem: "schema_mismatch" };
  const out = parsed.data;

  const known = new Map(cropNames.map((n) => [n.trim().toLowerCase(), n]));
  const seenTitles = new Set<string>();
  const kept: ModelRecommendation[] = [];
  let dropped = 0;

  for (const rec of out.recommendations) {
    const title = clip(rec.title, LIMITS.title);
    const reason = clip(rec.reason, LIMITS.reason);
    const action = clip(rec.suggested_action, LIMITS.suggested_action);
    let crop: string | null = null;

    if (rec.crop !== null && rec.crop.trim() !== "") {
      const match = known.get(rec.crop.trim().toLowerCase());
      if (!match) {
        dropped++; // unknown / invented crop
        continue;
      }
      crop = match; // use the database spelling
    }
    if (rec.category === "crop" && crop === null) {
      dropped++;
      continue;
    }
    if (!title || !reason || !action || seenTitles.has(title.toLowerCase())) {
      dropped++;
      continue;
    }
    seenTitles.add(title.toLowerCase());
    kept.push({ ...rec, title, reason, suggested_action: action, crop });
  }

  kept.sort((a, b) => PRIORITY_ORDER[a.priority] - PRIORITY_ORDER[b.priority]);
  const recommendations = kept.slice(0, MAX_RECOMMENDATIONS);
  const missing = out.missing_data.map((m) => clip(m, LIMITS.missing)).filter(Boolean).slice(0, 5);
  const summary = clip(out.summary, LIMITS.summary);

  if (out.status === "ok" && recommendations.length === 0) {
    return { ok: false, problem: "no_valid_recommendations" };
  }
  if (out.status === "insufficient_data" && recommendations.length === 0 && missing.length === 0) {
    return { ok: false, problem: "insufficient_without_reason" };
  }
  if (!summary) return { ok: false, problem: "empty_summary" };

  return {
    ok: true,
    dropped,
    value: { ...out, summary, recommendations, missing_data: missing },
  };
}
