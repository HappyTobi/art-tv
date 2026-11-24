package io.happytobi.arttv;

import android.content.Context;

import org.json.JSONException;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;

final class Ui {
    private static JSONObject translations = new JSONObject();
    private static String language;

    static void init(Context context) {
        String next = context.getResources().getConfiguration().getLocales().get(0).getLanguage();
        if (next.equals(language)) return;
        String file = "de".equals(next) ? "de" : "en";
        try (InputStream input = context.getAssets().open("i18n/" + file + ".json")) {
            ByteArrayOutputStream output = new ByteArrayOutputStream();
            byte[] buffer = new byte[2048];
            for (int n; (n = input.read(buffer)) != -1; ) output.write(buffer, 0, n);
            translations = new JSONObject(output.toString("UTF-8"));
            language = next;
        } catch (IOException | JSONException error) {
            AppLog.w("Cannot load interface language: " + error.getMessage());
        }
    }

    static String tr(String text) { return translations.optString(text, text); }
    static String trf(String text, String key, Object value) {
        return tr(text).replace("{" + key + "}", String.valueOf(value));
    }
}
