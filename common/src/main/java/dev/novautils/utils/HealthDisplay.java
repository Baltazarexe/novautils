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

    /**
     * Draws the bar. {@code ticks} adds a mark every two hearts (when there's room), {@code number}
     * writes the health in the middle (needs h >= 8).
     */
    public static void bar(GuiGraphicsExtractor g, int x, int y, int w, int h, LivingEntity e, boolean ticks, float alpha) {
        float[] f = animated(e);
        float now = Math.max(0f, Math.min(1f, e.getHealth() / Math.max(1f, e.getMaxHealth())));
        int c = color(now);
        int a = Math.max(0, Math.min(255, (int) (255 * alpha)));

        g.fill(x - 1, y - 1, x + w + 1, y + h + 1, Math.min(255, (int) (0xC0 * alpha)) << 24 | 0x0B0C10);
        g.fill(x, y, x + w, y + h, Math.min(255, (int) (0xFF * alpha)) << 24 | 0x23262E);

        int ghost = Math.round(w * f[1]), fill = Math.round(w * f[0]);
        if (ghost > fill) g.fill(x + fill, y, x + ghost, y + h, Math.min(255, (int) (0xD0 * alpha)) << 24 | 0xF2F2F2);
        if (fill > 0) {
            int top = lighten(c, 0.35f) & 0xFFFFFF | a << 24, bottom = c & 0xFFFFFF | a << 24;
            if (h >= 3) g.fillGradient(x, y, x + fill, y + h, top, bottom);
            else g.fill(x, y, x + fill, y + h, bottom);
        }

        float absorb = e.getAbsorptionAmount();
        if (absorb > 0) {
            int aw = Math.min(w, Math.round(w * absorb / Math.max(1f, e.getMaxHealth())));
            g.fill(x, y, x + aw, y + Math.max(1, h / 3), a << 24 | 0xFFD84A);
        }

        // a tick every 4 health (two hearts) if they'd be at least 4 px apart
        float max = e.getMaxHealth();
        if (ticks && max >= 8 && w / (max / 4f) >= 4) {
            for (float hp = 4; hp < max - 0.1f; hp += 4) {
                int tx = x + Math.round(w * hp / max);
                g.fill(tx, y, tx + 1, y + h, Math.min(255, (int) (0x70 * alpha)) << 24);
            }
        }
    }
}
