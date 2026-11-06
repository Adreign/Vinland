package com.vinland.app.ai.model;

import java.util.Map;

/** A failure the UI can explain to the user. Built from Cloud Function error codes. */
public class AiError {

    public enum Type {
        NOT_SIGNED_IN,
        OFFLINE,
        /** Refreshed too soon; wait {@link #retryAfterSeconds}. */
        COOLDOWN,
        /** Daily limit reached. */
        DAILY_LIMIT,
        /** AI service busy, overloaded or slow. Worth retrying later. */
        BUSY,
        /** App Check failed (e.g. debug token not registered). */
        APP_CHECK,
        /** The AI answer was unusable even after a retry. */
        INVALID_OUTPUT,
        UNKNOWN
    }

    public final Type type;
    public final String message;
    /** Seconds to wait before retrying, or 0 when not known. */
    public final int retryAfterSeconds;

    public AiError(Type type, String message, int retryAfterSeconds) {
        this.type = type;
        this.message = message;
        this.retryAfterSeconds = retryAfterSeconds;
    }

    public boolean isRateLimited() {
        return type == Type.COOLDOWN || type == Type.DAILY_LIMIT;
    }

    public static AiError notSignedIn() {
        return new AiError(Type.NOT_SIGNED_IN, "Please sign in to get AI recommendations.", 0);
    }

    public static AiError offline() {
        return new AiError(Type.OFFLINE,
                "You're offline. Connect to the internet to refresh your recommendations.", 0);
    }

    public static AiError invalidResponse() {
        return new AiError(Type.INVALID_OUTPUT,
                "The AI returned an unusable answer. Please try again.", 0);
    }

    /**
     * Maps a Cloud Function error to an AiError.
     *
     * @param code    callable error code in lower case, e.g. "resource-exhausted"
     * @param details the HttpsError details map from the server (may be null)
     * @param message the server's user-safe message (may be null)
     */
    public static AiError fromFunctionError(String code, Map<?, ?> details, String message) {
        String reason = details != null ? Maps.string(details, "reason", null) : null;
        int retryAfter = details != null ? (int) Maps.number(details, "retryAfterSeconds", 0) : 0;

        if (reason != null) {
            switch (reason) {
                case "cooldown":
                    return new AiError(Type.COOLDOWN, retryAfter > 0
                            ? "Please wait " + retryAfter + " seconds before refreshing again."
                            : "Please wait a moment before refreshing again.", retryAfter);
                case "daily_cap":
                case "global_cap":
                    return new AiError(Type.DAILY_LIMIT,
                            "You've used today's AI refreshes. Your last recommendations are still shown. Try again tomorrow.",
                            0);
                case "upstream_rate_limit":
                case "overloaded":
                case "timeout":
                case "network":
                    return new AiError(Type.BUSY,
                            "The AI service is busy right now. Please try again in a minute.", retryAfter);
                case "invalid_output":
                case "refused":
                    return invalidResponse();
                case "unauthenticated":
                    return notSignedIn();
                default:
                    break;
            }
        }

        if (code == null) code = "unknown";
        switch (code) {
            case "unauthenticated":
                // Signed-in users only reach this when App Check rejected the call.
                return new AiError(Type.APP_CHECK,
                        "Couldn't verify this copy of the app. Please update the app or try again later.", 0);
            case "unavailable":
            case "deadline-exceeded":
                return offline();
            case "resource-exhausted":
                return new AiError(Type.BUSY, "Too many requests. Please try again later.", retryAfter);
            default:
                return new AiError(Type.UNKNOWN,
                        message != null && !message.isEmpty() ? message : "Something went wrong. Please try again.",
                        0);
        }
    }
}
