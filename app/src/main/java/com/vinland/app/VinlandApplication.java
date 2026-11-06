package com.vinland.app;

import android.app.Application;

import com.google.firebase.FirebaseApp;
import com.google.firebase.appcheck.FirebaseAppCheck;

/**
 * App entry point. Turns on Firebase App Check so the Cloud Functions only accept
 * calls from this genuine app (not from scripts that copied the API endpoint).
 */
public class VinlandApplication extends Application {

    @Override
    public void onCreate() {
        super.onCreate();
        FirebaseApp.initializeApp(this);
        // Debug builds use the debug provider, release builds use Play Integrity.
        // See src/debug and src/release for the two versions of AppCheckSetup.
        AppCheckSetup.install(FirebaseAppCheck.getInstance());
    }
}
