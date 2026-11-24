package io.happytobi.arttv;

import android.app.KeyguardManager;
import android.content.ActivityNotFoundException;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.os.Build;
import android.os.PowerManager;
import android.os.SystemClock;
import android.provider.Settings;

public final class BootReceiver extends BroadcastReceiver {
    private static final long EARLY_BOOT_WINDOW_MS = 60_000;
    private static final int UNKNOWN_BOOT = -1;

    static boolean supported() {
        return Build.VERSION.SDK_INT == 28 && "Sony".equalsIgnoreCase(Build.BRAND)
                && "BRAVIA 4K GB ATV3".equals(Build.MODEL);
    }

    @Override public void onReceive(Context context, Intent intent) {
        if (Intent.ACTION_BOOT_COMPLETED.equals(intent.getAction())) openAtBoot(context, false);
    }

    static void onShortcutConnected(Context context) {
        if (SystemClock.elapsedRealtime() <= EARLY_BOOT_WINDOW_MS) openAtBoot(context, true);
    }

    static void noteHomeShown(Context context) {
        if (!supported() || !Prefs.openAtStartup()) return;
        int boot = bootCount(context);
        if (boot != UNKNOWN_BOOT && boot != Prefs.homeShownBoot()) Prefs.setHomeShownBoot(boot);
    }

    private static int bootCount(Context context) {
        return Settings.Global.getInt(context.getContentResolver(), Settings.Global.BOOT_COUNT, UNKNOWN_BOOT);
    }

    private static void openAtBoot(Context context, boolean early) {
        if (!supported() || !Prefs.openAtStartup()) return;
        int boot = bootCount(context);
        int shownBoot = Prefs.homeShownBoot();
        if (boot != UNKNOWN_BOOT && boot == shownBoot) return;
        // Without a baseline from an earlier boot, wait for Android's actual boot broadcast.
        if (early && (boot == UNKNOWN_BOOT || shownBoot == UNKNOWN_BOOT || boot == Prefs.earlyAttemptBoot())) return;
        PowerManager power = context.getSystemService(PowerManager.class);
        if (power == null || !power.isInteractive()) {
            AppLog.w("Startup skipped: TV is not interactive.");
            return;
        }
        KeyguardManager keyguard = context.getSystemService(KeyguardManager.class);
        if (keyguard == null || keyguard.isDeviceLocked()) {
            AppLog.w("Startup skipped: TV is locked or lock state is unavailable.");
            return;
        }
        if (early) Prefs.setEarlyAttemptBoot(boot);
        String source = early ? "Home-button service" : "boot broadcast";
        try {
            context.startActivity(new Intent(context, HomeActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
            AppLog.w("Requested Art TV launch at startup via " + source + " at "
                    + SystemClock.elapsedRealtime() + " ms after boot.");
        } catch (ActivityNotFoundException | SecurityException e) {
            AppLog.w("Could not open Art TV at startup via " + source + ": " + e.getMessage());
        }
    }
}
