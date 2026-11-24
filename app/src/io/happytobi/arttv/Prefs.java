package io.happytobi.arttv;

import android.content.Context;
import android.content.SharedPreferences;

final class Prefs {
    private static SharedPreferences settings;

    static void init(Context context) {
        settings = context.getSharedPreferences("settings", Context.MODE_PRIVATE);
    }

    static String artBackground() { return settings.getString("artBackground", ""); }
    static String artInput() { return settings.getString("artInput", ""); }
    static void setArtBackground(String id) { settings.edit().putString("artBackground", id).apply(); }
    static void setArtInput(String id) { settings.edit().putString("artInput", id).apply(); }
}
