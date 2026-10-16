package dev.novautils.modules.misc;

import dev.novautils.gui.Theme;
import dev.novautils.modules.Category;
import dev.novautils.modules.HudModule;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.ItemStack;

/**
 * Durability bars for the armour you are wearing. Drawn with plain rectangles and text on purpose:
 * item icons do not render correctly from a HUD element in this version. Movable in the HUD editor.
 */
public class ArmorHud extends HudModule {
    private static final EquipmentSlot[] SLOTS = {EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET};
    private static final String[] LABELS = {"H", "C", "L", "B"};
    private static final int CELL_W = 34, CELL_H = 15, GAP = 3;

    public ArmorHud() {
        super("ArmorHud", "Shows the durability of the armour you are wearing.", Category.MISC);
    }

    @Override
    protected int defaultX(int screenW, int w) {
        return screenW / 2 - w / 2;
    }

    @Override
    protected int defaultY(int screenH, int h) {
        return screenH - 52 - h;
    }

    @Override
    protected int[] render(GuiGraphics g, int x, int y, boolean right) {
        // one card: each piece as its icon, a thin durability bar and the percentage under it
        int cell = 22, w = PAD * 2 + SLOTS.length * cell - 6, h = PAD * 2 + 16 + 3 + 8;
        panel(g, x, y, w, h);
        for (int i = 0; i < SLOTS.length; i++) {
            ItemStack stack = mc.player.getItemBySlot(SLOTS[i]);
            int cx = x + PAD + i * cell, cy = y + PAD;
            if (stack.isEmpty()) {
                Theme.rounded(g, cx, cy, 16, 16, 3, 0x14FFFFFF);
                g.drawString(mc.font, LABELS[i], cx + 8 - mc.font.width(LABELS[i]) / 2, cy + 4, 0xFF4C5160, false);
                continue;
            }
            g.renderItem(stack, cx, cy);
            if (!stack.isDamageableItem()) continue;
            float left = 1f - stack.getDamageValue() / (float) stack.getMaxDamage();
            int color = left > 0.5f ? 0xFF66E07A : left > 0.2f ? 0xFFFFD050 : 0xFFFF5A5A;
            g.fill(cx, cy + 17, cx + 16, cy + 18, 0x40FFFFFF);
            g.fill(cx, cy + 17, cx + Math.max(1, Math.round(16 * left)), cy + 18, color);
            String pct = String.valueOf(Math.round(left * 100));
            g.drawString(mc.font, pct, cx + 8 - mc.font.width(pct) / 2, cy + 20, color, false);
        }
        return new int[]{w, h};
    }
}
