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
    static boolean openAtStartup() { return settings.getBoolean("openAtStartup", false); }
    static int homeShownBoot() { return settings.getInt("homeShownBoot", -1); }
    static int earlyAttemptBoot() { return settings.getInt("earlyAttemptBoot", -1); }
    static void setArtBackground(String id) { settings.edit().putString("artBackground", id).apply(); }
    static void setArtInput(String id) { settings.edit().putString("artInput", id).apply(); }
    static void setOpenAtStartup(boolean enabled) { settings.edit().putBoolean("openAtStartup", enabled).apply(); }
    static void setHomeShownBoot(int boot) { settings.edit().putInt("homeShownBoot", boot).apply(); }
    static void setEarlyAttemptBoot(int boot) { settings.edit().putInt("earlyAttemptBoot", boot).apply(); }
}
