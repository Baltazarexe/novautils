package dev.novautils.gui;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.util.ARGB;

/** Colours and small drawing helpers shared by the whole GUI. */
public final class Theme {
    public static final int ACCENT = 0xFF7C5CFF;
    public static final int ACCENT_DIM = 0xFF5B44C4;

    public static final int PANEL_BG = 0xF0161620;
    public static final int PANEL_BORDER = 0xFF2A2A38;
    public static final int HEADER_TOP = 0xFF242431;
    public static final int HEADER_BOTTOM = 0xFF1B1B26;

    public static final int ROW_HOVER = 0x22FFFFFF;
    public static final int ROW_ACTIVE = 0x1A7C5CFF;

    public static final int TEXT = 0xFFE9E9F2;
    public static final int TEXT_DIM = 0xFF8C8CA0;
    public static final int TEXT_OFF = 0xFF6E6E82;

    public static final int TRACK = 0xFF2E2E3E;

    private Theme() {}

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

    public static void shadow(GuiGraphicsExtractor g, int x, int y, int w, int h) {
        g.fill(x + 2, y + h, x + w + 2, y + h + 2, 0x40000000);
        g.fill(x + w, y + 2, x + w + 2, y + h, 0x40000000);
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
