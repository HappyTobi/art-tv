package io.happytobi.arttv;

public final class ArtChannelNamesTest {
    public static void main(String[] args) {
        matches("ard", "Das Erste HD", 0);
        matches("ard", "ARD", 1);
        matches("ard", "das-erste", 1);
        matches("zdf", " ZDF HD ", 0);
        matches("kika", "KiKA_HD", 0);
        matches("kika", "KiKA", 1);
        matches("zdf", "ZDFinfo HD", -1);
        matches("zdf", "ZDFneo HD", -1);
        matches("ard", "ARD alpha", -1);
        matches("ard", null, -1);
        matches("kika", "", -1);
        matches("unknown", "ZDF", -1);
        if (ArtChannelNames.rank("ard", "Das Erste HD") >= ArtChannelNames.rank("ard", "Das Erste"))
            throw new AssertionError("HD must be preferred to SD");
        System.out.println("Art channel matching passed.");
    }

    private static void matches(String shortcut, String broadcastName, int expected) {
        int actual = ArtChannelNames.rank(shortcut, broadcastName);
        if (actual != expected) throw new AssertionError(shortcut + ": " + broadcastName + " ranked " + actual);
    }
}
