package com.vinland.app.ai;

import android.content.Context;
import android.net.ConnectivityManager;
import android.net.NetworkCapabilities;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.FirebaseFirestore;
import com.vinland.app.ai.model.AiError;
import com.vinland.app.ai.model.AiRecommendationResponse;

import java.util.HashMap;
import java.util.Map;

/**
 * Default repository: reads the cache from Firestore and refreshes through the
 * getRecommendations Cloud Function. The app never talks to the AI provider directly.
 */
public class FirebaseAiRecommendationRepository implements AiRecommendationRepository {

    static final String FUNCTION_NAME = "getRecommendations";

    /** Returns the signed-in user's uid, or null. */
    public interface UidProvider {
        String currentUid();
    }

    public interface NetworkStatus {
        boolean isOnline();
    }

    /** Reads users/{uid}/ai_recommendations/latest; data is null when missing. */
    public interface CacheSource {
        void readLatest(String uid, CacheCallback callback);
    }

    public interface CacheCallback {
        void onResult(Map<String, Object> data);

        void onFailure(Exception e);
    }

    private final FunctionClient functions;
    private final CacheSource cache;
    private final UidProvider uidProvider;
    private final NetworkStatus network;

    public FirebaseAiRecommendationRepository(FunctionClient functions, CacheSource cache,
                                              UidProvider uidProvider, NetworkStatus network) {
        this.functions = functions;
        this.cache = cache;
        this.uidProvider = uidProvider;
        this.network = network;
    }

    /** Production wiring with real Firebase objects. */
    public static FirebaseAiRecommendationRepository create(Context context) {
        Context app = context.getApplicationContext();
        FirebaseFirestore db = FirebaseFirestore.getInstance();
        return new FirebaseAiRecommendationRepository(
                new FirebaseFunctionClient(),
                (uid, cb) -> db.collection("users").document(uid)
                        .collection("ai_recommendations").document("latest")
                        .get()
                        .addOnSuccessListener(snap -> cb.onResult(snap.exists() ? snap.getData() : null))
                        .addOnFailureListener(cb::onFailure),
                () -> {
                    FirebaseUser user = FirebaseAuth.getInstance().getCurrentUser();
                    return user != null ? user.getUid() : null;
                },
                () -> isOnline(app));
    }

    @Override
    public void getCached(Callback<AiRecommendationResponse> callback) {
        String uid = uidProvider.currentUid();
        if (uid == null) {
            callback.onSuccess(null);
            return;
        }
        cache.readLatest(uid, new CacheCallback() {
            @Override
            public void onResult(Map<String, Object> data) {
                if (data == null) {
                    callback.onSuccess(null);
                    return;
                }
                try {
                    callback.onSuccess(AiRecommendationResponse.fromMap(data, true));
                } catch (IllegalArgumentException e) {
                    callback.onSuccess(null); // a damaged cache is the same as no cache
                }
            }

            @Override
            public void onFailure(Exception e) {
                callback.onSuccess(null); // the cache is optional
            }
        });
    }

    @Override
    public void refresh(boolean forceRefresh, Callback<AiRecommendationResponse> callback) {
        if (uidProvider.currentUid() == null) {
            callback.onError(AiError.notSignedIn());
            return;
        }
        if (!network.isOnline()) {
            callback.onError(AiError.offline());
            return;
        }
        Map<String, Object> data = new HashMap<>();
        data.put("forceRefresh", forceRefresh);

        functions.call(FUNCTION_NAME, data, new FunctionClient.Callback() {
            @Override
            public void onSuccess(Object result) {
                if (!(result instanceof Map)) {
                    callback.onError(AiError.invalidResponse());
                    return;
                }
                try {
                    callback.onSuccess(AiRecommendationResponse.fromMap((Map<?, ?>) result, false));
                } catch (IllegalArgumentException e) {
                    callback.onError(AiError.invalidResponse());
                }
            }

            @Override
            public void onFailure(FunctionClient.CallException error) {
                callback.onError(AiError.fromFunctionError(error.code, error.details, error.getMessage()));
            }
        });
    }

    private static boolean isOnline(Context context) {
        ConnectivityManager cm = (ConnectivityManager) context.getSystemService(Context.CONNECTIVITY_SERVICE);
        if (cm == null) return true; // can't tell: let the call decide
        NetworkCapabilities caps = cm.getNetworkCapabilities(cm.getActiveNetwork());
        return caps != null && caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET);
    }
}
