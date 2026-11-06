package com.vinland.app.ai;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

import androidx.arch.core.executor.testing.InstantTaskExecutorRule;

import com.vinland.app.ai.model.AiError;
import com.vinland.app.ai.model.AiRecommendationResponse;

import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;

import java.util.ArrayList;
import java.util.List;

public class AiRecommendationViewModelTest {

    /** Lets LiveData run on the test thread. */
    @Rule
    public InstantTaskExecutorRule instantTasks = new InstantTaskExecutorRule();

    /** A repository whose callbacks the test triggers by hand. */
    private static class FakeRepository implements AiRecommendationRepository {
        Callback<AiRecommendationResponse> pendingCache;
        Callback<AiRecommendationResponse> pendingRefresh;
        final List<Boolean> refreshCalls = new ArrayList<>();

        @Override
        public void getCached(Callback<AiRecommendationResponse> callback) {
            pendingCache = callback;
        }

        @Override
        public void refresh(boolean forceRefresh, Callback<AiRecommendationResponse> callback) {
            refreshCalls.add(forceRefresh);
            pendingRefresh = callback;
        }
    }

    private FakeRepository repo;
    private AiRecommendationViewModel vm;

    @Before
    public void setUp() {
        repo = new FakeRepository();
        vm = new AiRecommendationViewModel(repo);
    }

    private static AiRecommendationResponse response(String status, int recs, boolean cached) {
        return AiRecommendationResponse.fromMap(TestData.response(status, recs), cached);
    }

    private AiUiState state() {
        return vm.getState().getValue();
    }

    @Test
    public void start_showsLoading_thenContent() {
        vm.start();
        assertEquals(AiUiState.Status.LOADING, state().status);
        assertEquals(1, repo.refreshCalls.size());
        assertFalse("opening the screen does not force a new AI call", repo.refreshCalls.get(0));

        repo.pendingCache.onSuccess(null);
        assertEquals(AiUiState.Status.LOADING, state().status);

        repo.pendingRefresh.onSuccess(response("ok", 2, false));
        assertEquals(AiUiState.Status.CONTENT, state().status);
        assertFalse(state().refreshing);
        assertNull(state().error);
    }

    @Test
    public void cachedResultIsShownInstantly_whileRefreshing() {
        vm.start();
        AiRecommendationResponse cached = response("ok", 1, true);
        repo.pendingCache.onSuccess(cached);
        assertEquals(AiUiState.Status.CONTENT, state().status);
        assertSame(cached, state().data);
        assertTrue(state().refreshing);
    }

    @Test
    public void lateCacheDoesNotOverwriteFreshResult() {
        vm.start();
        AiRecommendationResponse fresh = response("ok", 3, false);
        repo.pendingRefresh.onSuccess(fresh);
        repo.pendingCache.onSuccess(response("ok", 1, true));
        assertSame(fresh, state().data);
    }

    @Test
    public void errorWithoutData_showsErrorState() {
        vm.start();
        repo.pendingCache.onSuccess(null);
        repo.pendingRefresh.onError(AiError.offline());
        assertEquals(AiUiState.Status.ERROR, state().status);
        assertEquals(AiError.Type.OFFLINE, state().error.type);
    }

    @Test
    public void errorWithData_keepsDataAndShowsBanner() {
        vm.start();
        repo.pendingCache.onSuccess(response("ok", 2, true));
        repo.pendingRefresh.onError(new AiError(AiError.Type.DAILY_LIMIT, "limit", 0));
        assertEquals(AiUiState.Status.CONTENT, state().status);
        assertNotNull(state().data);
        assertEquals(AiError.Type.DAILY_LIMIT, state().error.type);
        assertFalse(state().refreshing);
    }

    @Test
    public void refreshButton_forcesNewAnswer_andIgnoresDoubleTaps() {
        vm.start();
        repo.pendingRefresh.onSuccess(response("ok", 1, false));
        vm.refresh();
        vm.refresh(); // second tap while the first is running
        assertEquals(2, repo.refreshCalls.size());
        assertTrue(repo.refreshCalls.get(1));
        assertTrue(state().refreshing);
    }

    @Test
    public void startTwice_onlyLoadsOnce() {
        vm.start();
        vm.start(); // e.g. after rotation
        assertEquals(1, repo.refreshCalls.size());
    }

    @Test
    public void emptyAndInsufficientStates() {
        vm.start();
        repo.pendingRefresh.onSuccess(response("ok", 0, false));
        assertEquals(AiUiState.Status.EMPTY, state().status);

        vm.retry();
        repo.pendingRefresh.onSuccess(response("insufficient_data", 0, false));
        assertEquals(AiUiState.Status.INSUFFICIENT_DATA, state().status);
        assertEquals("No harvest history", state().data.missingData.get(0));
    }
}
