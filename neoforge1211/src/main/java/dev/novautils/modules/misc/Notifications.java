package dev.novautils.modules.misc;

import dev.novautils.gui.Theme;
import dev.novautils.gui.Toasts;
import dev.novautils.modules.Category;
import dev.novautils.modules.HudModule;
import net.minecraft.client.gui.GuiGraphics;

import java.util.List;

/**
 * Small slide-in toasts when you toggle a module with its keybind, so you know it actually
 * switched without opening the menu. On by default. Movable in the HUD editor.
 */
public class Notifications extends HudModule {
    private static final int W = 110, H = 16, GAP = 3;

    public Notifications() {
        super("Notifications", "Shows a toast when you toggle a module with a key.", Category.MISC);
        setEnabled(true);
    }

    @Override
    protected int defaultX(int screenW, int w) {
        return screenW - W - 4;
    }

    @Override
    protected int defaultY(int screenH, int h) {
        return screenH - (H + GAP) * 4 - 30;
    }

    @Override
    protected int[] render(GuiGraphics g, int x, int y, boolean right) {
        List<Toasts.Toast> toasts = Toasts.active();
        if (toasts.isEmpty() && editing) {
            toasts = List.of(new Toasts.Toast("Esp ON", Theme.ACCENT, System.currentTimeMillis()));
        }

        long now = System.currentTimeMillis();
        for (int i = 0; i < toasts.size(); i++) {
            Toasts.Toast t = toasts.get(i);
            float age = (now - t.createdAt()) / (float) Toasts.LIFETIME_MS;
            // slide in over the first 8%, fade out over the last 20%
            float in = Math.min(1f, age / 0.08f);
            float out = age > 0.8f ? 1f - (age - 0.8f) / 0.2f : 1f;
            float alpha = Math.max(0f, Math.min(in, out));

            int slide = (int) ((1f - in) * 20) * (right ? 1 : -1);
            int tx = x + slide, ty = y + i * (H + GAP);

            Theme.roundRect(g, tx, ty, W, H, Theme.alpha(0xF0161620, alpha));
            g.fill(right ? tx + W - 3 : tx, ty, right ? tx + W : tx + 3, ty + H, Theme.alpha(t.color(), alpha));
            if (alpha > 0.1f) g.drawString(mc.font, t.text(), tx + 8, ty + 4, Theme.alpha(Theme.TEXT, alpha));

            // lifetime bar
            g.fill(tx + 3, ty + H - 1, tx + 3 + (int) ((W - 6) * (1f - age)), ty + H, Theme.alpha(t.color(), alpha * 0.6f));
        }
        return new int[]{W, Math.max(1, toasts.size()) * (H + GAP)};
    }
}
