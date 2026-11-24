package io.happytobi.arttv;

import java.util.Locale;

/** Match broadcast names, not channel numbers or related stations such as ZDFinfo. */
final class ArtChannelNames {
    static int rank(String shortcut, String name) {
        if (name == null) return -1;
        String normalized = name.toLowerCase(Locale.ROOT).replaceAll("[\\s._-]+", "");
        boolean hd = normalized.endsWith("hd");
        if (hd) normalized = normalized.substring(0, normalized.length() - 2);
        boolean matches = "ard".equals(shortcut) ? normalized.equals("ard") || normalized.equals("daserste")
                : "zdf".equals(shortcut) ? normalized.equals("zdf")
                : "kika".equals(shortcut) && normalized.equals("kika");
        return matches ? (hd ? 0 : 1) : -1;
    }

    private ArtChannelNames() {}
}
