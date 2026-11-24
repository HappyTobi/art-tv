package io.happytobi.arttv;

import android.app.Activity;
import android.os.Bundle;
import android.view.KeyEvent;

/** TV artwork screen and Android Home entry point. */
public final class HomeActivity extends Activity {
    private ArtHomeView art;

    @Override protected void onCreate(Bundle state) {
        super.onCreate(state);
        Ui.init(this);
        art = new ArtHomeView(this);
        setContentView(art);
    }

    @Override protected void onStart() {
        super.onStart();
        ArtAudioFocus.enter(this);
    }

    @Override protected void onResume() {
        super.onResume();
        art.resume();
    }

    @Override protected void onPause() {
        art.pause();
        super.onPause();
    }

    @Override protected void onStop() {
        ArtAudioFocus.leave();
        super.onStop();
    }

    @Override protected void onDestroy() {
        art.destroy();
        super.onDestroy();
    }

    @Override public boolean dispatchKeyEvent(KeyEvent event) {
        if (art.onKey(event)) return true;
        if (event.getKeyCode() == KeyEvent.KEYCODE_BACK || event.getKeyCode() == KeyEvent.KEYCODE_MENU) {
            if (event.getAction() == KeyEvent.ACTION_DOWN && event.getRepeatCount() == 0) {
                if (event.getKeyCode() == KeyEvent.KEYCODE_MENU) art.settings();
                else art.reset();
            }
            return true;
        }
        return super.dispatchKeyEvent(event);
    }
}
