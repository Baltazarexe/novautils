package dev.novautils.modules.misc;

import dev.novautils.gui.Theme;
import dev.novautils.modules.Category;
import dev.novautils.modules.HudModule;
import dev.novautils.settings.Setting;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.world.item.ItemStack;

/** Your main inventory (the 27 slots above the hotbar) on screen, with counts. Drag it in the HUD editor. */
public class InventoryHud extends HudModule {
    private static final int CELL = 18;

    private final Setting<Boolean> background = register(Setting.builder(Boolean.class, "background")
        .description("Dark panel behind the slots.").defaultValue(true).build());

    public InventoryHud() {
        super("InventoryHUD", "Shows your inventory on screen.", Category.MISC);
    }

    @Override
    protected int defaultX(int screenW, int w) {
        return screenW / 2 - w / 2;
    }

    @Override
    protected int defaultY(int screenH, int h) {
        return 4;
    }

    @Override
    protected int[] render(GuiGraphics g, int x, int y, boolean right) {
        int w = 9 * CELL + 4, h = 3 * CELL + 4;
        if (background.get()) {
            Theme.roundRect(g, x, y, w, h, 0x90101018);
            Theme.border(g, x, y, w, h, 0x30FFFFFF);
        }
        var inv = mc.player.getInventory();
        for (int i = 0; i < 27; i++) {
            ItemStack stack = inv.getItem(9 + i);
            int cx = x + 2 + (i % 9) * CELL, cy = y + 2 + (i / 9) * CELL;
            g.fill(cx, cy, cx + CELL - 1, cy + CELL - 1, 0x30FFFFFF);
            if (stack.isEmpty()) continue;
            g.renderItem(stack, cx + 1, cy + 1);
            g.renderItemDecorations(mc.font, stack, cx + 1, cy + 1);
        }
        return new int[]{w, h};
    }
}
