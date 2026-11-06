package com.vinland.app;

import com.google.firebase.appcheck.FirebaseAppCheck;
import com.google.firebase.appcheck.debug.DebugAppCheckProviderFactory;

/**
 * Debug builds only. On first launch Logcat prints a line like
 * "Enter this debug secret into the allow list in the Firebase Console: ...".
 * Add that token in Firebase Console > App Check > Apps > (your app) > Manage debug tokens.
 */
final class AppCheckSetup {
    private AppCheckSetup() {
    }

    static void install(FirebaseAppCheck appCheck) {
        appCheck.installAppCheckProviderFactory(DebugAppCheckProviderFactory.getInstance());
    }
}
