import { z } from "zod";

/**
 * The JSON shape Claude must return. Passed to the API as a structured-output
 * format, so the reply is always valid JSON in this shape. Content limits (lengths,
 * item counts, crop names) are checked afterwards in validate.ts.
 */
export const ModelRecommendationSchema = z.object({
  title: z.string(),
  reason: z.string(),
  priority: z.enum(["high", "medium", "low"]),
  suggested_action: z.string(),
  category: z.enum(["crop", "soil", "watering", "general"]),
  crop: z.string().nullable(),
});

export const ModelOutputSchema = z.object({
  status: z.enum(["ok", "insufficient_data"]),
  soil_status: z.enum(["optimal", "fair", "action_needed", "unknown"]),
  summary: z.string(),
  recommendations: z.array(ModelRecommendationSchema),
  missing_data: z.array(z.string()),
});

export type ModelRecommendation = z.infer<typeof ModelRecommendationSchema>;
export type ModelOutput = z.infer<typeof ModelOutputSchema>;

/** What the Android app receives (and what is cached in Firestore). */
export interface RecommendationsResponse {
  status: "ok" | "insufficient_data";
  cached: boolean;
  generatedAt: number;
  model: string;
  soilStatus: ModelOutput["soil_status"];
  summary: string;
  recommendations: ModelRecommendation[];
  missingData: string[];
  disclaimer: string;
  quota: { remainingToday: number };
}
