package io.happytobi.arttv;

import android.app.Application;

public final class App extends Application {
    @Override public void onCreate() {
        super.onCreate();
        Prefs.init(this);
    }
}
