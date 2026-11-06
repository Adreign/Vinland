import type { FunctionsErrorCode } from "firebase-functions/v2/https";

/** Machine-readable reason sent to the app in HttpsError.details.reason. */
export type ErrorReason =
  | "app_check"
  | "no_profile"
  | "cooldown"
  | "daily_cap"
  | "global_cap"
  | "upstream_rate_limit"
  | "overloaded"
  | "timeout"
  | "network"
  | "invalid_output"
  | "refused"
  | "config"
  | "unknown";

/** An error with a clean code for the app. Never carries user data. */
export class AppError extends Error {
  constructor(
    public readonly code: FunctionsErrorCode,
    public readonly reason: ErrorReason,
    message: string,
    public readonly retryAfterSeconds?: number,
  ) {
    super(message);
  }
}
