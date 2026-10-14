package dev.novautils.utils;

/** The bits of 26.x's dev.novautils.utils.ARGB the shared code uses (1.20.1 doesn't have it). */
public final class ARGB {
    private ARGB() {}

    public static int alpha(int c) { return c >>> 24; }
    public static int red(int c) { return c >> 16 & 0xFF; }
    public static int green(int c) { return c >> 8 & 0xFF; }
    public static int blue(int c) { return c & 0xFF; }

    public static int color(int alpha, int rgb) {
        return (alpha & 0xFF) << 24 | (rgb & 0xFFFFFF);
    }

    public static int color(int a, int r, int g, int b) {
        return (a & 0xFF) << 24 | (r & 0xFF) << 16 | (g & 0xFF) << 8 | (b & 0xFF);
    }

    public static int colorFromFloat(float a, float r, float g, float b) {
        return color(Math.round(a * 255), Math.round(r * 255), Math.round(g * 255), Math.round(b * 255));
    }
}
