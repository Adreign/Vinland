package com.vinland.app.ai;

import androidx.annotation.NonNull;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.ViewModel;
import androidx.lifecycle.ViewModelProvider;

import com.vinland.app.ai.model.AiError;
import com.vinland.app.ai.model.AiRecommendationResponse;

/**
 * Screen logic for AI recommendations. Survives rotation, so a refresh in
 * progress is not lost and the AI is not called twice.
 *
 * Flow on open: show the cached answer instantly, then ask the server, which
 * only calls the AI if the garden data changed. The refresh button forces a
 * new AI answer.
 */
public class AiRecommendationViewModel extends ViewModel {

    private final AiRecommendationRepository repository;
    private final MutableLiveData<AiUiState> state = new MutableLiveData<>(AiUiState.loading());
    private boolean started = false;
    private boolean inFlight = false;
    /** Ignores a slow cache read that finishes after a newer server result. */
    private boolean haveServerResult = false;

    public AiRecommendationViewModel(AiRecommendationRepository repository) {
        this.repository = repository;
    }

    public LiveData<AiUiState> getState() {
        return state;
    }

    /** Call from onCreate. Runs once per ViewModel, not again after rotation. */
    public void start() {
        if (started) return;
        started = true;
        repository.getCached(new AiRecommendationRepository.Callback<AiRecommendationResponse>() {
            @Override
            public void onSuccess(AiRecommendationResponse cached) {
                if (cached != null && !haveServerResult) {
                    state.setValue(AiUiState.of(cached, null, inFlight));
                }
            }

            @Override
            public void onError(AiError error) {
                // No cache: keep the loading state until the server answers.
            }
        });
        load(false);
    }

    /** The refresh button: asks the AI for a fresh answer. */
    public void refresh() {
        load(true);
    }

    /** The retry button on the error screen. */
    public void retry() {
        load(false);
    }

    private void load(boolean force) {
        if (inFlight) return;
        inFlight = true;

        AiUiState current = state.getValue();
        AiRecommendationResponse shown = current != null ? current.data : null;
        state.setValue(shown != null ? AiUiState.of(shown, null, true) : AiUiState.loading());

        repository.refresh(force, new AiRecommendationRepository.Callback<AiRecommendationResponse>() {
            @Override
            public void onSuccess(AiRecommendationResponse fresh) {
                inFlight = false;
                haveServerResult = true;
                state.setValue(AiUiState.of(fresh, null, false));
            }

            @Override
            public void onError(AiError error) {
                inFlight = false;
                AiUiState now = state.getValue();
                AiRecommendationResponse keep = now != null ? now.data : null;
                // Keep showing what we have, with the error as a banner.
                state.setValue(keep != null ? AiUiState.of(keep, error, false) : AiUiState.error(error));
            }
        });
    }

    /** Creates the ViewModel with its repository (no DI framework in this app). */
    public static class Factory implements ViewModelProvider.Factory {
        private final AiRecommendationRepository repository;

        public Factory(AiRecommendationRepository repository) {
            this.repository = repository;
        }

        @NonNull
        @Override
        @SuppressWarnings("unchecked")
        public <T extends ViewModel> T create(@NonNull Class<T> modelClass) {
            if (modelClass.isAssignableFrom(AiRecommendationViewModel.class)) {
                return (T) new AiRecommendationViewModel(repository);
            }
            throw new IllegalArgumentException("Unknown ViewModel " + modelClass);
        }
    }
}
