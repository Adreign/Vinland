package com.vinland.app.ai;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import com.vinland.app.ai.model.AiError;
import com.vinland.app.ai.model.AiRecommendationResponse;

import org.junit.Before;
import org.junit.Test;

import java.util.Map;

public class FirebaseAiRecommendationRepositoryTest {

    private FakeFunctionClient functions;
    private Map<String, Object> cacheDoc;
    private String uid;
    private boolean online;
    private FirebaseAiRecommendationRepository repo;

    /** Captures what the repository calls back with. */
    private static class Result implements AiRecommendationRepository.Callback<AiRecommendationResponse> {
        AiRecommendationResponse value;
        AiError error;
        boolean called;

        @Override
        public void onSuccess(AiRecommendationResponse v) {
            called = true;
            value = v;
        }

        @Override
        public void onError(AiError e) {
            called = true;
            error = e;
        }
    }

    @Before
    public void setUp() {
        functions = new FakeFunctionClient();
        cacheDoc = null;
        uid = "user-1";
        online = true;
        repo = new FirebaseAiRecommendationRepository(
                functions,
                (id, cb) -> cb.onResult(cacheDoc),
                () -> uid,
                () -> online);
    }

    @Test
    public void refresh_callsFunctionWithForceFlag_andParsesResponse() {
        functions.nextResult = TestData.response("ok", 2);
        Result r = new Result();
        repo.refresh(true, r);

        assertEquals("getRecommendations", functions.calledNames.get(0));
        assertEquals(Boolean.TRUE, functions.calledData.get(0).get("forceRefresh"));
        assertNull(r.error);
        assertEquals(2, r.value.recommendations.size());
        assertEquals("Pechay", r.value.recommendations.get(0).crop);
        assertEquals("Add 1 tbsp lime per pot.", r.value.recommendations.get(0).suggestedAction);
        assertEquals(7, r.value.remainingToday);
        assertFalse(r.value.cached);
    }

    @Test
    public void refresh_offline_doesNotCallFunction() {
        online = false;
        Result r = new Result();
        repo.refresh(false, r);
        assertTrue(functions.calledNames.isEmpty());
        assertEquals(AiError.Type.OFFLINE, r.error.type);
    }

    @Test
    public void refresh_signedOut_returnsNotSignedIn() {
        uid = null;
        Result r = new Result();
        repo.refresh(false, r);
        assertTrue(functions.calledNames.isEmpty());
        assertEquals(AiError.Type.NOT_SIGNED_IN, r.error.type);
    }

    @Test
    public void refresh_mapsServerErrors() {
        assertEquals(AiError.Type.COOLDOWN, errorFor("resource-exhausted", "cooldown", 42).type);
        assertEquals(42, errorFor("resource-exhausted", "cooldown", 42).retryAfterSeconds);
        assertEquals(AiError.Type.DAILY_LIMIT, errorFor("resource-exhausted", "daily_cap", null).type);
        assertEquals(AiError.Type.BUSY, errorFor("resource-exhausted", "upstream_rate_limit", 60).type);
        assertEquals(AiError.Type.BUSY, errorFor("unavailable", "overloaded", 30).type);
        assertEquals(AiError.Type.INVALID_OUTPUT, errorFor("internal", "invalid_output", null).type);
        // No server details: a transport problem, i.e. offline.
        assertEquals(AiError.Type.OFFLINE, errorFor("unavailable", null, null).type);
        // Signed in but rejected as unauthenticated: App Check failed.
        assertEquals(AiError.Type.APP_CHECK, errorFor("unauthenticated", null, null).type);
    }

    @Test
    public void refresh_malformedResponse_isInvalidOutput() {
        functions.nextResult = "not a map";
        Result r = new Result();
        repo.refresh(false, r);
        assertEquals(AiError.Type.INVALID_OUTPUT, r.error.type);

        functions.nextResult = new java.util.HashMap<String, Object>(); // no status
        Result r2 = new Result();
        repo.refresh(false, r2);
        assertEquals(AiError.Type.INVALID_OUTPUT, r2.error.type);
    }

    @Test
    public void getCached_returnsCacheMarkedCached_orNull() {
        Result none = new Result();
        repo.getCached(none);
        assertTrue(none.called);
        assertNull(none.value);

        cacheDoc = TestData.response("ok", 1);
        cacheDoc.remove("quota"); // the stored cache has no quota
        Result some = new Result();
        repo.getCached(some);
        assertNotNull(some.value);
        assertTrue(some.value.cached);
        assertEquals(-1, some.value.remainingToday);
        assertTrue(functions.calledNames.isEmpty()); // reading the cache never calls the AI
    }

    private AiError errorFor(String code, String reason, Integer retryAfter) {
        functions.nextError = new FunctionClient.CallException(
                code, reason == null ? null : TestData.details(reason, retryAfter), "msg");
        Result r = new Result();
        repo.refresh(false, r);
        functions.nextError = null;
        return r.error;
    }
}
