package io.happytobi.arttv;

/** Cached box blur; no work is done while a frame is drawn. */
final class ScreenBlur {
    static void blur(int[] pixels, int width, int height, int radius, int passes) {
        int[] temporary = new int[pixels.length];
        for (int pass = 0; pass < passes; pass++) {
            box(pixels, temporary, width, height, radius, true);
            box(temporary, pixels, width, height, radius, false);
        }
        for (int i = 0; i < pixels.length; i++) {
            int pixel = pixels[i], r = (pixel >> 16) & 255, g = (pixel >> 8) & 255, b = pixel & 255;
            float gray = .299f * r + .587f * g + .114f * b;
            pixels[i] = 0xff000000 | clamp(gray + 1.3f * (r - gray)) << 16
                    | clamp(gray + 1.3f * (g - gray)) << 8 | clamp(gray + 1.3f * (b - gray));
        }
    }

    private static int clamp(float value) { return Math.max(0, Math.min(255, Math.round(value))); }

    private static void box(int[] input, int[] output, int width, int height, int radius, boolean horizontal) {
        int count = horizontal ? width : height, lines = horizontal ? height : width;
        int step = horizontal ? 1 : width, lineStep = horizontal ? width : 1;
        float norm = 1f / (2 * radius + 1);
        for (int line = 0; line < lines; line++) {
            int base = line * lineStep;
            float r = 0, g = 0, b = 0;
            for (int i = -radius; i <= radius; i++) {
                int pixel = input[base + Math.max(0, Math.min(count - 1, i)) * step];
                r += (pixel >> 16) & 255;
                g += (pixel >> 8) & 255;
                b += pixel & 255;
            }
            for (int i = 0; i < count; i++) {
                output[base + i * step] = 0xff000000 | Math.round(r * norm) << 16
                        | Math.round(g * norm) << 8 | Math.round(b * norm);
                int add = input[base + Math.min(count - 1, i + radius + 1) * step];
                int remove = input[base + Math.max(0, i - radius) * step];
                r += ((add >> 16) & 255) - ((remove >> 16) & 255);
                g += ((add >> 8) & 255) - ((remove >> 8) & 255);
                b += (add & 255) - (remove & 255);
            }
        }
    }
}
