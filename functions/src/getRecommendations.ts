import { createHash } from "node:crypto";
import { initializeApp, getApps } from "firebase-admin/app";
import { getFirestore } from "firebase-admin/firestore";
import * as logger from "firebase-functions/logger";
import { defineSecret } from "firebase-functions/params";
import { HttpsError, onCall } from "firebase-functions/v2/https";
import type Anthropic from "@anthropic-ai/sdk";
import { callClaude, createClient } from "./claude";
import { FUNCTION_REGION } from "./config";
import { loadRawData } from "./data/loadContext";
import { AppError } from "./errors";
import { readUsage, recordUsage, reserveQuota } from "./rateLimit";
import { getRecommendationsCore, type CacheDoc, type Deps } from "./service";

/** Stored in Google Secret Manager. Set with: firebase functions:secrets:set ANTHROPIC_API_KEY */
const anthropicApiKey = defineSecret("ANTHROPIC_API_KEY");

if (getApps().length === 0) initializeApp();
const db = getFirestore();

let client: Anthropic | null = null;

function cacheRef(uid: string) {
  return db.collection("users").doc(uid).collection("ai_recommendations").doc("latest");
}

export const getRecommendations = onCall(
  {
    region: FUNCTION_REGION,
    enforceAppCheck: true, // rejects calls that don't come from your genuine app
    secrets: [anthropicApiKey],
    timeoutSeconds: 120,
    memory: "256MiB",
    maxInstances: 10,
  },
  async (request) => {
    if (!request.auth) {
      throw new HttpsError("unauthenticated", "Please sign in to get recommendations.", { reason: "unauthenticated" });
    }
    const uid = request.auth.uid;
    const uidHash = createHash("sha256").update(uid).digest("hex").slice(0, 12);
    const forceRefresh = (request.data as { forceRefresh?: unknown } | null)?.forceRefresh === true;

    client ??= createClient(anthropicApiKey.value());
    const anthropic = client;

    const deps: Deps = {
      now: () => Date.now(),
      loadRaw: (id) => loadRawData(db, id),
      readCache: async (id) => ((await cacheRef(id).get()).data() as CacheDoc | undefined) ?? null,
      writeCache: async (id, doc) => {
        await cacheRef(id).set(doc);
      },
      readUsage: (id) => readUsage(db, id),
      reserveQuota: (id, now, today) => reserveQuota(db, id, now, today),
      recordUsage: (id, today, usage) => recordUsage(db, id, today, usage),
      generate: (content) => callClaude(anthropic, content),
      // Logs never include user data, prompts, replies or secrets.
      log: (event, data) => logger.info(event, { uidHash, ...data }),
    };

    try {
      return await getRecommendationsCore(deps, uid, forceRefresh);
    } catch (err) {
      if (err instanceof AppError) {
        logger.warn("recommendation_error", { uidHash, reason: err.reason, code: err.code });
        throw new HttpsError(err.code, err.message, {
          reason: err.reason,
          retryAfterSeconds: err.retryAfterSeconds ?? null,
        });
      }
      logger.error("recommendation_unexpected", { uidHash, type: (err as Error)?.name ?? "unknown" });
      throw new HttpsError("internal", "Something went wrong. Please try again.", { reason: "unknown" });
    }
  },
);
