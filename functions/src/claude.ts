import Anthropic from "@anthropic-ai/sdk";
import { zodOutputFormat } from "@anthropic-ai/sdk/helpers/zod";
import {
  CLAUDE_EFFORT,
  CLAUDE_MAX_TOKENS,
  CLAUDE_MODEL,
  CLAUDE_SDK_RETRIES,
  CLAUDE_TIMEOUT_MS,
} from "./config";
import { AppError } from "./errors";
import { SYSTEM_PROMPT } from "./prompt/systemPrompt";
import { ModelOutputSchema } from "./schema";

export interface TokenUsage {
  input: number;
  output: number;
  cacheRead: number;
  cacheWrite: number;
}

export interface ClaudeResult {
  /** The parsed JSON, or null if the reply could not be parsed. */
  output: unknown | null;
  stopReason: string | null;
  usage: TokenUsage;
}

export function createClient(apiKey: string): Anthropic {
  return new Anthropic({ apiKey, timeout: CLAUDE_TIMEOUT_MS, maxRetries: CLAUDE_SDK_RETRIES });
}

/**
 * One request to Claude. Structured outputs make the API return JSON matching
 * ModelOutputSchema; the SDK parses it into `parsed_output`.
 */
export async function callClaude(
  client: Anthropic,
  content: Anthropic.TextBlockParam[],
): Promise<ClaudeResult> {
  try {
    const res = await client.messages.parse({
      model: CLAUDE_MODEL,
      max_tokens: CLAUDE_MAX_TOKENS,
      system: SYSTEM_PROMPT,
      messages: [{ role: "user", content }],
      output_config: { effort: CLAUDE_EFFORT, format: zodOutputFormat(ModelOutputSchema) },
    });
    const usage: TokenUsage = {
      input: res.usage.input_tokens ?? 0,
      output: res.usage.output_tokens ?? 0,
      cacheRead: res.usage.cache_read_input_tokens ?? 0,
      cacheWrite: res.usage.cache_creation_input_tokens ?? 0,
    };
    if (res.stop_reason === "refusal") {
      throw new AppError("internal", "refused", "The AI declined this request.");
    }
    // A reply cut off by max_tokens is incomplete JSON: treat it as invalid output.
    const output = res.stop_reason === "max_tokens" ? null : (res.parsed_output ?? null);
    return { output, stopReason: res.stop_reason, usage };
  } catch (err) {
    throw mapClaudeError(err);
  }
}

/** Converts SDK errors into clean app error codes. Most specific class first. */
export function mapClaudeError(err: unknown): AppError {
  if (err instanceof AppError) return err;
  if (err instanceof Anthropic.RateLimitError) {
    return new AppError("resource-exhausted", "upstream_rate_limit", "The AI service is busy. Try again in a minute.", 60);
  }
  if (err instanceof Anthropic.APIConnectionTimeoutError) {
    return new AppError("deadline-exceeded", "timeout", "The AI took too long to answer.");
  }
  if (err instanceof Anthropic.APIConnectionError) {
    return new AppError("unavailable", "network", "Could not reach the AI service.");
  }
  if (err instanceof Anthropic.AuthenticationError || err instanceof Anthropic.PermissionDeniedError) {
    return new AppError("internal", "config", "The AI service is not configured correctly.");
  }
  if (err instanceof Anthropic.APIError) {
    if (err.status === 529 || err.status === 503) {
      return new AppError("unavailable", "overloaded", "The AI service is overloaded. Try again shortly.", 30);
    }
    if (typeof err.status === "number" && err.status >= 500) {
      return new AppError("unavailable", "overloaded", "The AI service had a temporary problem.", 30);
    }
    return new AppError("internal", "unknown", "The AI request failed.");
  }
  // The SDK's JSON parsing of a malformed reply lands here: retryable as invalid output.
  return new AppError("internal", "invalid_output", "The AI returned an unreadable answer.");
}
