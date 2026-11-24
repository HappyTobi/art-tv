package io.happytobi.arttv;

import android.accessibilityservice.AccessibilityService;
import android.content.ActivityNotFoundException;
import android.content.Intent;
import android.os.Build;
import android.os.PowerManager;
import android.view.KeyEvent;
import android.view.accessibility.AccessibilityEvent;

public final class HomeButtonService extends AccessibilityService {
    private long handledDownTime = -1;
    private int handledDevice;

    @Override protected void onServiceConnected() {
        if (!"Sony".equalsIgnoreCase(Build.BRAND) || !"BRAVIA 4K GB ATV3".equals(Build.MODEL)) {
            AppLog.w("Home button shortcut is unavailable on this TV model.");
            disableSelf();
        }
    }

    @Override protected boolean onKeyEvent(KeyEvent event) {
        if (event.getKeyCode() != KeyEvent.KEYCODE_HOME) return false;

        // Consume repeats and release only for a press we successfully redirected.
        boolean handled = handledDownTime == event.getDownTime() && handledDevice == event.getDeviceId();
        if (event.getAction() == KeyEvent.ACTION_UP) {
            if (handled) handledDownTime = -1;
            return handled;
        }
        if (event.getAction() != KeyEvent.ACTION_DOWN || event.getRepeatCount() != 0) return handled;
        handledDownTime = -1;
        PowerManager power = getSystemService(PowerManager.class);
        if (power == null || !power.isInteractive()) return false;
        try {
            startActivity(new Intent(this, HomeActivity.class)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP));
        } catch (ActivityNotFoundException | SecurityException e) {
            AppLog.w("Could not open Art TV from Home: " + e.getMessage());
            return false;
        }
        handledDevice = event.getDeviceId();
        handledDownTime = event.getDownTime();
        return true;
    }

    @Override public void onAccessibilityEvent(AccessibilityEvent event) {}
    @Override public void onInterrupt() {}
}
