package dev.novautils.gui;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.util.ARGB;

/**
 * Colours and drawing helpers shared by the whole GUI. The look: dark glass panels, a neon
 * cyan-to-violet accent that runs through borders, switches and sliders, soft glows instead of
 * hard shadows, and small "tech" corner brackets. Everything is built from plain rectangles, so it
 * works the same on every loader and version.
 */
public final class Theme {
    /** Main accent (neon cyan) and the second end of every gradient (violet). */
    public static final int ACCENT = 0xFF8B9CFF;
    public static final int ACCENT2 = 0xFFC49BFF;
    public static final int ACCENT_DIM = 0xFF5D6BCC;

    public static final int PANEL_BG = 0xF2121419;
    public static final int PANEL_BORDER = 0xFF262A33;
    public static final int HEADER_TOP = 0xFF171A21;
    public static final int HEADER_BOTTOM = 0xFF131519;

    public static final int ROW_HOVER = 0x1AFFFFFF;
    public static final int ROW_ACTIVE = 0x228B9CFF;

    public static final int TEXT = 0xFFE8EAF0;
    public static final int TEXT_DIM = 0xFF9196A4;
    public static final int TEXT_OFF = 0xFF60657A;

    public static final int TRACK = 0xFF2B2F3A;

    private Theme() {}

    /** Seconds since the GUI code first ran - drives pulses and the background animation. */
    public static float time() {
        return (System.nanoTime() - START) / 1e9f;
    }

    private static final long START = System.nanoTime();

    /** Flat rect with 1px clipped corners, which reads as slightly rounded at GUI scale. */
    public static void roundRect(GuiGraphicsExtractor g, int x, int y, int w, int h, int color) {
        if (w <= 0 || h <= 0) return;
        g.fill(x + 1, y, x + w - 1, y + h, color);
        g.fill(x, y + 1, x + 1, y + h - 1, color);
        g.fill(x + w - 1, y + 1, x + w, y + h - 1, color);
    }

    public static void border(GuiGraphicsExtractor g, int x, int y, int w, int h, int color) {
        g.fill(x + 1, y, x + w - 1, y + 1, color);
        g.fill(x + 1, y + h - 1, x + w - 1, y + h, color);
        g.fill(x, y + 1, x + 1, y + h - 1, color);
        g.fill(x + w - 1, y + 1, x + w, y + h - 1, color);
    }

    /** Rectangle with properly rounded corners of radius {@code r} (a quarter circle per corner). */
    public static void rounded(GuiGraphicsExtractor g, int x, int y, int w, int h, int r, int color) {
        if (w <= 0 || h <= 0) return;
        r = Math.max(0, Math.min(r, Math.min(w, h) / 2));
        for (int i = 0; i < r; i++) {
            double dy = r - i - 0.5;
            int inset = (int) Math.round(r - Math.sqrt(r * r - dy * dy));
            g.fill(x + inset, y + i, x + w - inset, y + i + 1, color);
            g.fill(x + inset, y + h - 1 - i, x + w - inset, y + h - i, color);
        }
        g.fill(x, y + r, x + w, y + h - r, color);
    }

    /** Soft drop shadow under a rounded box: wide, faint and slightly lower than the box. */
    public static void softShadow(GuiGraphicsExtractor g, int x, int y, int w, int h, int r, float strength) {
        for (int i = 8; i >= 1; i--) {
            int a = (int) (14 * strength * (9 - i) / 8f);
            if (a > 0) rounded(g, x - i, y - i + 3, w + i * 2, h + i * 2, r + i, ARGB.color(a, 0));
        }
    }

    /** iOS-style switch, {@code t} = 0 (off) .. 1 (on) so it can slide. 16x9. */
    public static void toggle(GuiGraphicsExtractor g, int x, int y, float t) {
        rounded(g, x, y, 16, 9, 4, mix(0xFF343844, ACCENT, t));
        int kx = x + 1 + Math.round(t * 7);
        rounded(g, kx, y + 1, 7, 7, 3, mix(0xFFB8BCC8, 0xFFFFFFFF, t));
    }

    /** Soft glow around a box: a few widening rings that fade out. */
    public static void glow(GuiGraphicsExtractor g, int x, int y, int w, int h, int color, float strength) {
        for (int i = 1; i <= 4; i++) {
            int a = (int) (ARGB.alpha(color) * strength * (0.22f / i));
            if (a <= 0) continue;
            border(g, x - i, y - i, w + i * 2, h + i * 2, ARGB.color(a, color & 0xFFFFFF));
        }
    }

    /** Kept for older callers: now a soft dark glow instead of a hard drop shadow. */
    public static void shadow(GuiGraphicsExtractor g, int x, int y, int w, int h) {
        glow(g, x, y + 1, w, h, 0xFF000000, 0.9f);
    }

    /** Left-to-right gradient (vanilla only has top-to-bottom), drawn as thin columns. */
    public static void hGradient(GuiGraphicsExtractor g, int x1, int y1, int x2, int y2, int left, int right) {
        int w = x2 - x1;
        if (w <= 0 || y2 <= y1) return;
        int steps = Math.max(1, Math.min(48, w / 2));
        for (int i = 0; i < steps; i++) {
            int a = x1 + w * i / steps, b = x1 + w * (i + 1) / steps;
            if (b > a) g.fill(a, y1, b, y2, mix(left, right, (i + 0.5f) / steps));
        }
    }

    /** The signature accent line: cyan to violet, with a faint glow under it. */
    public static void accentLine(GuiGraphicsExtractor g, int x1, int y, int x2, float alpha) {
        hGradient(g, x1, y, x2, y + 1, Theme.alpha(ACCENT, alpha), Theme.alpha(ACCENT2, alpha));
        hGradient(g, x1, y + 1, x2, y + 2, Theme.alpha(ACCENT, alpha * 0.25f), Theme.alpha(ACCENT2, alpha * 0.25f));
    }

    /** Little L-shaped brackets on the four corners - the "HUD" look. */
    public static void corners(GuiGraphicsExtractor g, int x, int y, int w, int h, int len, int color) {
        g.fill(x, y, x + len, y + 1, color);
        g.fill(x, y, x + 1, y + len, color);
        g.fill(x + w - len, y, x + w, y + 1, color);
        g.fill(x + w - 1, y, x + w, y + len, color);
        g.fill(x, y + h - 1, x + len, y + h, color);
        g.fill(x, y + h - len, x + 1, y + h, color);
        g.fill(x + w - len, y + h - 1, x + w, y + h, color);
        g.fill(x + w - 1, y + h - len, x + w, y + h, color);
    }

    /** Accent colour at a position along a gradient (0 = cyan, 1 = violet). */
    public static int accentAt(float t) {
        return mix(ACCENT, ACCENT2, t);
    }

    /** 0..1..0 wave, for pulsing glows. */
    public static float pulse(float speed) {
        return 0.5f + 0.5f * (float) Math.sin(time() * speed);
    }

    public static int alpha(int color, float a) {
        return ARGB.color((int) (ARGB.alpha(color) * Math.max(0f, Math.min(1f, a))), color & 0xFFFFFF);
    }

    public static int mix(int a, int b, float t) {
        t = Math.max(0f, Math.min(1f, t));
        int ar = (a >> 16) & 0xFF, ag = (a >> 8) & 0xFF, ab = a & 0xFF, aa = (a >>> 24) & 0xFF;
        int br = (b >> 16) & 0xFF, bg = (b >> 8) & 0xFF, bb = b & 0xFF, ba = (b >>> 24) & 0xFF;
        return ARGB.color((int) (aa + (ba - aa) * t), (int) (ar + (br - ar) * t),
            (int) (ag + (bg - ag) * t), (int) (ab + (bb - ab) * t));
    }

    public static float approach(float current, float target, float delta) {
        float speed = Math.min(1f, delta * 0.45f);
        return current + (target - current) * speed;
    }
}
