package io.happytobi.arttv;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.CookieManager;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.function.BooleanSupplier;

/** Sony's own broadcast list; its DVB URIs are not Android TvContract row IDs. */
final class SonyArtChannels {
    private final CookieManager cookies = new CookieManager();
    private final String server = Config.SONY_TV_URL.replaceAll("/+$", "");

    static boolean configured() {
        return !Config.SONY_TV_URL.isEmpty();
    }

    void tune(String shortcut, BooleanSupplier current) throws IOException, JSONException {
        call("accessControl", "actRegister", new JSONArray()
                .put(new JSONObject().put("clientid", "art-tv:channel-lookup")
                        .put("nickname", "Art TV channel lookup"))
                .put(new JSONArray()));
        JSONArray sources = call("avContent", "getSourceList",
                new JSONArray().put(new JSONObject().put("scheme", "tv"))).getJSONArray(0);
        String chosen = null;
        int best = Integer.MAX_VALUE;
        for (int s = 0; s < sources.length(); s++) {
            String source = sources.getJSONObject(s).getString("source");
            if (!source.matches("tv:dvb[stc]")) continue;
            boolean end = false;
            for (int start = 0; start < 10000; start += 200) {
                if (!current.getAsBoolean()) return;
                JSONArray rows = call("avContent", "getContentList", new JSONArray()
                        .put(new JSONObject().put("source", source).put("stIdx", start).put("cnt", 200)))
                        .getJSONArray(0);
                for (int i = 0; i < rows.length(); i++) {
                    JSONObject row = rows.getJSONObject(i);
                    int rank = ArtChannelNames.rank(shortcut, row.optString("title"));
                    String uri = row.optString("uri");
                    if (rank >= 0 && rank < best && uri.startsWith(source + "?")) {
                        chosen = uri;
                        best = rank;
                    }
                }
                if (best == 0 || rows.length() < 200) {
                    end = true;
                    break;
                }
            }
            if (!end) throw new IOException("Sony channel list exceeded lookup limit");
            if (best == 0) break;
        }
        if (chosen == null) throw new IOException("Sony channel not found: " + shortcut);
        if (!current.getAsBoolean()) return;
        call("avContent", "setPlayContent", new JSONArray().put(new JSONObject().put("uri", chosen)));
        AppLog.w("Sony art channel requested: " + shortcut);
    }

    private JSONArray call(String endpoint, String method, JSONArray params) throws IOException, JSONException {
        URL url = new URL(server + "/sony/" + endpoint);
        HttpURLConnection connection = (HttpURLConnection) url.openConnection();
        connection.setInstanceFollowRedirects(false);
        connection.setConnectTimeout(4000);
        connection.setReadTimeout(6000);
        connection.setRequestMethod("POST");
        connection.setDoOutput(true);
        connection.setRequestProperty("Content-Type", "application/json");
        try {
            for (Map.Entry<String, List<String>> header : cookies.get(url.toURI(), java.util.Collections.emptyMap()).entrySet())
                connection.setRequestProperty(header.getKey(), String.join("; ", header.getValue()));
            byte[] body = new JSONObject().put("method", method).put("params", params)
                    .put("id", 1).put("version", "1.0").toString().getBytes(StandardCharsets.UTF_8);
            try (OutputStream out = connection.getOutputStream()) {
                out.write(body);
            }
            int status = connection.getResponseCode();
            if (status != 200) throw new IOException("Sony " + method + ": HTTP " + status
                    + (status == 401 || status == 403 ? " (remote-control pairing required)" : ""));
            cookies.put(url.toURI(), connection.getHeaderFields());
            ByteArrayOutputStream bytes = new ByteArrayOutputStream();
            try (InputStream in = connection.getInputStream()) {
                byte[] buffer = new byte[4096];
                int n;
                while ((n = in.read(buffer)) != -1) {
                    if (bytes.size() + n > 1024 * 1024) throw new IOException("Sony response too large");
                    bytes.write(buffer, 0, n);
                }
            }
            JSONObject reply = new JSONObject(new String(bytes.toByteArray(), StandardCharsets.UTF_8));
            if (reply.has("error")) throw new IOException("Sony " + method + ": error "
                    + reply.getJSONArray("error").getInt(0));
            return reply.getJSONArray("result");
        } catch (java.net.URISyntaxException e) {
            throw new IOException("Invalid Sony TV address", e);
        } finally {
            connection.disconnect();
        }
    }
}
