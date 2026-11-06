/**
 * All tunable settings for the AI recommendations feature live here.
 *
 * To switch models, change CLAUDE_MODEL only, e.g. to "claude-sonnet-5-5"
 * (better reasoning, roughly 20x the cost of Haiku).
 */
export const CLAUDE_MODEL = "claude-haiku-5-5";

/** How hard the model thinks. "low" is cheap and fast; "medium" is the model default. */
export const CLAUDE_EFFORT = "low" as const;

/** Upper bound on the model's reply (thinking + JSON). A cap, not a target. */
export const CLAUDE_MAX_TOKENS = 8000;

/** Per-attempt timeout for the Claude request, in milliseconds. */
export const CLAUDE_TIMEOUT_MS = 30_000;

/** SDK-level retries for 429 / 5xx / network errors (per attempt). */
export const CLAUDE_SDK_RETRIES = 1;

/** Region of the function. Keep it close to Firestore and to the users (PH). */
export const FUNCTION_REGION = "asia-southeast1";

/** Minimum seconds between two real Claude calls for the same user. */
export const USER_COOLDOWN_SECONDS = 60;

/** Max real Claude calls per user per day (Asia/Manila calendar day). */
export const USER_DAILY_CAP = 10;

/** Max real Claude calls per day for the whole project (cost safety net). */
export const GLOBAL_DAILY_CAP = 500;

/** A cached result with unchanged inputs is reused for this long. */
export const CACHE_MAX_AGE_MS = 24 * 60 * 60 * 1000;

/** A sensor reading older than this is reported as stale to the model. */
export const SENSOR_STALE_MS = 48 * 60 * 60 * 1000;

/** Data limits: keep prompts small and predictable. */
export const MAX_CROPS = 40;
export const MAX_ADMIN_RULES = 30;
export const MAX_SOIL_LOGS = 5;
export const MAX_HARVEST_LOGS = 10;
export const MAX_RECOMMENDATIONS = 5;

export const TIME_ZONE = "Asia/Manila";

export const DISCLAIMER =
  "AI-generated suggestions based on your garden data. Not professional agronomic, medical, or legal advice.";
