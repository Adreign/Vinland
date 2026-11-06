package com.vinland.app.ai;

import java.util.Map;

/**
 * Calls a Cloud Function by name. An interface so tests can use a fake instead of
 * the real Firebase Functions SDK.
 */
public interface FunctionClient {

    interface Callback {
        void onSuccess(Object data);

        void onFailure(CallException error);
    }

    void call(String functionName, Map<String, Object> data, Callback callback);

    /** A failed call, reduced to plain values (no Firebase classes). */
    class CallException extends Exception {
        /** Lower-case callable code, e.g. "resource-exhausted" or "unavailable". */
        public final String code;
        /** HttpsError details sent by the server, or null. */
        public final Map<?, ?> details;

        public CallException(String code, Map<?, ?> details, String message) {
            super(message);
            this.code = code;
            this.details = details;
        }
    }
}
