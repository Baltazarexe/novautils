package dev.novautils.modules.misc;

import dev.novautils.gui.Theme;
import dev.novautils.modules.Category;
import dev.novautils.modules.HudModule;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.gui.GuiGraphics;

/** WASD + sneak/jump indicator. Movable in the HUD editor. */
public class KeyStrokes extends HudModule {
    private static final int SIZE = 16, GAP = 2;

    public KeyStrokes() {
        super("KeyStrokes", "Shows which movement keys you are pressing.", Category.MISC);
    }

    @Override
    protected int defaultX(int screenW, int w) {
        return 10;
    }

    @Override
    protected int defaultY(int screenH, int h) {
        return screenH - h - 50;
    }

    private void key(GuiGraphics g, KeyMapping mapping, String label, int px, int py, int w, int h) {
        boolean down = mapping.isDown();
        Theme.softShadow(g, px, py, w, h, 3, 0.2f);
        Theme.rounded(g, px, py, w, h, 3, down ? Theme.alpha(Theme.ACCENT, 0.9f) : HUD_BG);
        Theme.border(g, px, py, w, h, down ? 0x50FFFFFF : HUD_BORDER);
        int c = down ? 0xFFFFFFFF : HUD_LABEL;
        if (label.isEmpty()) {
            // the space bar: a short line instead of text
            g.fill(px + w / 2 - 8, py + h / 2, px + w / 2 + 8, py + h / 2 + 1, c);
        } else {
            g.drawString(mc.font, label, px + w / 2 - mc.font.width(label) / 2, py + h / 2 - 4, c, false);
        }
    }

    @Override
    protected int[] render(GuiGraphics g, int left, int top, boolean right) {
        int wide = SIZE * 3 + GAP * 2;
        key(g, mc.options.keyUp, "W", left + SIZE + GAP, top, SIZE, SIZE);
        key(g, mc.options.keyLeft, "A", left, top + SIZE + GAP, SIZE, SIZE);
        key(g, mc.options.keyDown, "S", left + SIZE + GAP, top + SIZE + GAP, SIZE, SIZE);
        key(g, mc.options.keyRight, "D", left + (SIZE + GAP) * 2, top + SIZE + GAP, SIZE, SIZE);
        key(g, mc.options.keyShift, "Shift", left, top + (SIZE + GAP) * 2, wide, SIZE - 4);
        key(g, mc.options.keyJump, "", left, top + (SIZE + GAP) * 2 + SIZE - 2, wide, SIZE - 4);
        return new int[]{wide, (SIZE + GAP) * 2 + (SIZE - 2) + (SIZE - 4)};
    }
}
