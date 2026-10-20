package dev.novautils.utils;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.world.entity.LivingEntity;

import java.util.HashMap;
import java.util.Map;

/**
 * Pretty health bars shared by Nametags and HealthBars: the fill slides to the new value, the
 * health that was just lost stays as a pale "ghost" for a moment before draining, absorption is a
 * gold strip on top, and thin ticks mark every two hearts.
 */
public final class HealthDisplay {
    /** Per entity id: {shown fraction, ghost fraction, last time health dropped (ms), last frame (ms)}. */
    private static final Map<Integer, double[]> ANIM = new HashMap<>();

    private HealthDisplay() {}

    /** Green at full, through yellow, to red when nearly dead. */
    public static int color(float f) {
        f = Math.max(0f, Math.min(1f, f));
        float hue = 0.33f * f;
        return 0xFF000000 | (java.awt.Color.HSBtoRGB(hue, 0.72f, 0.95f) & 0xFFFFFF);
    }

    private static int lighten(int c, float t) {
        int r = c >> 16 & 0xFF, g = c >> 8 & 0xFF, b = c & 0xFF;
        r += (int) ((255 - r) * t);
        g += (int) ((255 - g) * t);
        b += (int) ((255 - b) * t);
        return c & 0xFF000000 | r << 16 | g << 8 | b;
    }

    /** Animated {shown, ghost} fractions for this entity. */
    public static float[] animated(LivingEntity e) {
        float target = Math.max(0f, Math.min(1f, e.getHealth() / Math.max(1f, e.getMaxHealth())));
        long now = System.currentTimeMillis();
        if (ANIM.size() > 512) ANIM.clear();
        double[] a = ANIM.computeIfAbsent(e.getId(), k -> new double[]{target, target, 0, now});
        double dt = Math.min(0.1, (now - a[3]) / 1000.0);
        a[3] = now;
        if (target < a[0] - 0.001) a[2] = now;
        // the fill catches up quickly, the ghost waits a little then drains
        a[0] += (target - a[0]) * Math.min(1.0, dt * 14);
        if (target >= a[1]) a[1] = target;
        else if (now - a[2] > 450) a[1] = Math.max(target, a[1] - dt * 0.9);
        return new float[]{(float) a[0], (float) a[1]};
    }

    /** Fill colour of the pill bars: a deep green when healthy, easing to red as health drops. */
    public static int pillColor(float f) {
        f = Math.max(0f, Math.min(1f, f));
        return dev.novautils.gui.Theme.mix(0xFFE5484D, 0xFF058669, Math.min(1f, f * 1.6f));
    }

    /**
     * The shared look: a rounded pill on a dark translucent track, the fill a rounded pill too, with
     * the pale "ghost" of health that was just lost sitting behind it. {@code shown} and
     * {@code ghost} are fractions (see {@link #animated}), {@code now} the real fraction (colour).
     */
    public static void pill(GuiGraphicsExtractor g, int x, int y, int w, int h, float shown, float ghost, float now, float alpha) {
        int r = h / 2;
        if (h < 3) {
            g.fill(x, y, x + w, y + h, dev.novautils.gui.Theme.alpha(0x64000000, alpha));
            g.fill(x, y, x + Math.round(w * shown), y + h, dev.novautils.gui.Theme.alpha(pillColor(now), alpha));
            return;
        }
        dev.novautils.gui.Theme.rounded(g, x, y, w, h, r, dev.novautils.gui.Theme.alpha(0x64000000, alpha));
        int ghostW = Math.round(w * ghost), fill = Math.round(w * shown);
        if (ghostW > fill) dev.novautils.gui.Theme.rounded(g, x, y, ghostW, h, r, dev.novautils.gui.Theme.alpha(0xFFF2F2F2, 0.8f * alpha));
        if (fill > 0) dev.novautils.gui.Theme.rounded(g, x, y, Math.max(fill, h), h, r, dev.novautils.gui.Theme.alpha(pillColor(now), alpha));
    }

    /** Same look as {@link #pill} standing up: the fill rises from the bottom, the ghost sits above it. */
    public static void verticalPill(GuiGraphicsExtractor g, int x, int y, int w, int h, float shown, float ghost, float now, float alpha) {
        int r = w / 2;
        if (w < 3) {
            g.fill(x, y, x + w, y + h, dev.novautils.gui.Theme.alpha(0x64000000, alpha));
            int f = Math.round(h * shown);
            g.fill(x, y + h - f, x + w, y + h, dev.novautils.gui.Theme.alpha(pillColor(now), alpha));
            return;
        }
        dev.novautils.gui.Theme.rounded(g, x, y, w, h, r, dev.novautils.gui.Theme.alpha(0x64000000, alpha));
        int ghostH = Math.round(h * ghost), fill = Math.round(h * shown);
        if (ghostH > fill) dev.novautils.gui.Theme.rounded(g, x, y + h - ghostH, w, ghostH, r, dev.novautils.gui.Theme.alpha(0xFFF2F2F2, 0.8f * alpha));
        if (fill > 0) {
            int fh = Math.max(fill, w);
            dev.novautils.gui.Theme.rounded(g, x, y + h - fh, w, fh, r, dev.novautils.gui.Theme.alpha(pillColor(now), alpha));
        }
    }

    /** Vertical bar for an entity (the side-of-the-mob placement). */
    public static void barVertical(GuiGraphicsExtractor g, int x, int y, int w, int h, LivingEntity e, float alpha) {
        float[] f = animated(e);
        float now = Math.max(0f, Math.min(1f, e.getHealth() / Math.max(1f, e.getMaxHealth())));
        verticalPill(g, x, y, w, h, f[0], f[1], now, alpha);
        float absorb = e.getAbsorptionAmount();
        if (absorb > 0 && w >= 3) {
            int ah = Math.min(h, Math.round(h * absorb / Math.max(1f, e.getMaxHealth())));
            dev.novautils.gui.Theme.rounded(g, x, y + h - Math.max(ah, 2), Math.max(1, w / 3), Math.max(ah, 2), 1, dev.novautils.gui.Theme.alpha(0xFFFFD84A, alpha));
        }
    }

    /**
     * Draws the bar for an entity. {@code ticks} is kept for older callers (the pill look has none);
     * absorption is a thin gold pill along the top.
     */
    public static void bar(GuiGraphicsExtractor g, int x, int y, int w, int h, LivingEntity e, boolean ticks, float alpha) {
        float[] f = animated(e);
        float now = Math.max(0f, Math.min(1f, e.getHealth() / Math.max(1f, e.getMaxHealth())));
        pill(g, x, y, w, h, f[0], f[1], now, alpha);

        float absorb = e.getAbsorptionAmount();
        if (absorb > 0 && h >= 3) {
            int aw = Math.min(w, Math.round(w * absorb / Math.max(1f, e.getMaxHealth())));
            dev.novautils.gui.Theme.rounded(g, x, y, Math.max(aw, 2), Math.max(1, h / 3), 1, dev.novautils.gui.Theme.alpha(0xFFFFD84A, alpha));
        }
    }
}
