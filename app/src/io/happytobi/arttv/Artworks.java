package io.happytobi.arttv;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/** Bundled, offline artwork. Stable ids keep a selection when the catalogue grows. */
final class Artworks {
    static final class Entry {
        final String id, title, file;

        Entry(String id, String title, String file) {
            this.id = id;
            this.title = title;
            this.file = file;
        }
    }

    static List<Entry> list(Context c) throws IOException {
        try (InputStream in = c.getAssets().open("art/catalogue.json")) {
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            byte[] buffer = new byte[4096];
            for (int n; (n = in.read(buffer)) != -1; ) out.write(buffer, 0, n);
            JSONArray rows = new JSONArray(out.toString("UTF-8"));
            if (rows.length() == 0) throw new IOException("empty artwork catalogue");
            List<Entry> entries = new ArrayList<>();
            Set<String> ids = new HashSet<>();
            for (int i = 0; i < rows.length(); i++) {
                JSONObject row = rows.getJSONObject(i);
                String id = row.getString("id"), title = row.getString("title"), file = row.getString("file");
                if (!id.matches("[a-z0-9_-]+") || !ids.add(id) || title.trim().isEmpty()
                        || !file.matches("[a-zA-Z0-9_-]+\\.(jpg|jpeg|png|webp)")) {
                    throw new IOException("invalid artwork entry " + i);
                }
                entries.add(new Entry(id, title, file));
            }
            return entries;
        } catch (JSONException e) {
            throw new IOException("invalid artwork catalogue", e);
        }
    }

    static Bitmap decode(Context c, Entry entry, int width, int height) throws IOException {
        BitmapFactory.Options options = new BitmapFactory.Options();
        options.inJustDecodeBounds = true;
        try (InputStream in = c.getAssets().open("art/" + entry.file)) {
            BitmapFactory.decodeStream(in, null, options);
        }
        if (options.outWidth <= 0 || options.outHeight <= 0) throw new IOException("unreadable artwork " + entry.file);
        options.inSampleSize = 1;
        while (options.outWidth / options.inSampleSize > width * 2
                || options.outHeight / options.inSampleSize > height * 2) options.inSampleSize *= 2;
        options.inJustDecodeBounds = false;
        try (InputStream in = c.getAssets().open("art/" + entry.file)) {
            Bitmap image = BitmapFactory.decodeStream(in, null, options);
            if (image == null) throw new IOException("unreadable artwork " + entry.file);
            return image;
        }
    }

    private Artworks() {}
}
