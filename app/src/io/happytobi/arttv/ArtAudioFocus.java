package io.happytobi.arttv;

import android.content.Context;
import android.media.AudioAttributes;
import android.media.AudioFocusRequest;
import android.media.AudioManager;

/** Keeps artwork quiet without changing volume, including transitions between home and its settings. */
final class ArtAudioFocus {
    private static AudioManager audio;
    private static AudioFocusRequest request;
    private static int screens;

    static void enter(Context context) {
        if (++screens != 1) return;
        audio = context.getApplicationContext().getSystemService(AudioManager.class);
        if (audio == null) {
            AppLog.w("Art TV audio focus: audio service unavailable");
            return;
        }
        request = new AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT)
                .setAudioAttributes(new AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_MEDIA)
                        .setContentType(AudioAttributes.CONTENT_TYPE_MOVIE).build())
                .setOnAudioFocusChangeListener(change -> AppLog.w("Art TV audio focus changed: " + change))
                .build();
        int result = audio.requestAudioFocus(request);
        AppLog.w("Art TV audio focus: " + (result == AudioManager.AUDIOFOCUS_REQUEST_GRANTED ? "held" : "refused"));
    }

    static void leave() {
        if (--screens != 0) return;
        if (audio != null && request != null) {
            int result = audio.abandonAudioFocusRequest(request);
            AppLog.w("Art TV audio focus: " + (result == AudioManager.AUDIOFOCUS_REQUEST_GRANTED ? "released" : "release refused"));
        }
        audio = null;
        request = null;
    }
}
