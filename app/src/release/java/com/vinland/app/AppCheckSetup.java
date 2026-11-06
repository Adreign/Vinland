package com.vinland.app;

import com.google.firebase.appcheck.FirebaseAppCheck;
import com.google.firebase.appcheck.playintegrity.PlayIntegrityAppCheckProviderFactory;

/** Release builds: Google Play Integrity proves the request comes from your real app. */
final class AppCheckSetup {
    private AppCheckSetup() {
    }

    static void install(FirebaseAppCheck appCheck) {
        appCheck.installAppCheckProviderFactory(PlayIntegrityAppCheckProviderFactory.getInstance());
    }
}
