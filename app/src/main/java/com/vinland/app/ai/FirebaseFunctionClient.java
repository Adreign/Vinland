package com.vinland.app.ai;

import com.google.firebase.functions.FirebaseFunctions;
import com.google.firebase.functions.FirebaseFunctionsException;

import java.util.Locale;
import java.util.Map;
import java.util.concurrent.TimeUnit;

/** Real {@link FunctionClient} using Firebase callable functions (auth + App Check tokens are added automatically). */
public class FirebaseFunctionClient implements FunctionClient {

    /** Must match FUNCTION_REGION in functions/src/config.ts. */
    public static final String REGION = "asia-southeast1";
    /** The function may retry the AI once; allow for that. */
    private static final long TIMEOUT_SECONDS = 120;

    private final FirebaseFunctions functions;

    public FirebaseFunctionClient() {
        this(FirebaseFunctions.getInstance(REGION));
    }

    public FirebaseFunctionClient(FirebaseFunctions functions) {
        this.functions = functions;
    }

    @Override
    public void call(String functionName, Map<String, Object> data, Callback callback) {
        functions.getHttpsCallable(functionName)
                .withTimeout(TIMEOUT_SECONDS, TimeUnit.SECONDS)
                .call(data)
                .addOnSuccessListener(result -> callback.onSuccess(result.getData()))
                .addOnFailureListener(e -> callback.onFailure(toCallException(e)));
    }

    static CallException toCallException(Exception e) {
        if (e instanceof FirebaseFunctionsException) {
            FirebaseFunctionsException fe = (FirebaseFunctionsException) e;
            String code = fe.getCode().name().toLowerCase(Locale.US).replace('_', '-');
            Object details = fe.getDetails();
            return new CallException(code, details instanceof Map ? (Map<?, ?>) details : null, fe.getMessage());
        }
        // IOException and friends: treat as a network problem.
        return new CallException("unavailable", null, e.getMessage());
    }
}
