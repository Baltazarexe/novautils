package dev.novautils.modules.misc;

import dev.novautils.gui.Theme;
import dev.novautils.modules.Category;
import dev.novautils.modules.Module;
import dev.novautils.settings.Setting;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.gui.GuiGraphicsExtractor;

/** WASD + sneak/jump indicator, the kind streamers put in a corner. */
public class KeyStrokes extends Module {
    private final Setting<Integer> x = register(Setting.builder(Integer.class, "x")
        .description("Distance from the left edge.").defaultValue(10).range(0, 400).build());

    private final Setting<Integer> y = register(Setting.builder(Integer.class, "y")
        .description("Distance from the bottom edge.").defaultValue(70).range(0, 300).build());

    public KeyStrokes() {
        super("KeyStrokes", "Shows which movement keys you are pressing.", Category.MISC);
    }

    private void key(GuiGraphicsExtractor g, KeyMapping mapping, String label, int px, int py, int w, int h) {
        boolean down = mapping.isDown();
        Theme.roundRect(g, px, py, w, h, down ? Theme.ACCENT : 0xB0202030);
        Theme.border(g, px, py, w, h, down ? 0xFFFFFFFF : 0x40FFFFFF);
        g.text(mc.font, label, px + w / 2 - mc.font.width(label) / 2, py + h / 2 - 4, down ? 0xFFFFFFFF : Theme.TEXT_DIM);
    }

    @Override
    public void onRenderHud(GuiGraphicsExtractor g, DeltaTracker deltaTracker) {
        int size = 16, gap = 2;
        int left = x.get();
        int top = g.guiHeight() - y.get();

        key(g, mc.options.keyUp, "W", left + size + gap, top, size, size);
        key(g, mc.options.keyLeft, "A", left, top + size + gap, size, size);
        key(g, mc.options.keyDown, "S", left + size + gap, top + size + gap, size, size);
        key(g, mc.options.keyRight, "D", left + (size + gap) * 2, top + size + gap, size, size);
        key(g, mc.options.keyShift, "SHIFT", left, top + (size + gap) * 2, size * 3 + gap * 2, size - 4);
        key(g, mc.options.keyJump, "____", left, top + (size + gap) * 2 + size - 2, size * 3 + gap * 2, size - 4);
    }
}
