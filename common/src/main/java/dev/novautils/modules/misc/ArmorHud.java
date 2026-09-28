package dev.novautils.modules.misc;

import dev.novautils.gui.Theme;
import dev.novautils.modules.Category;
import dev.novautils.modules.HudModule;
import net.minecraft.client.gui.GuiGraphicsExtractor;
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
        return screenH - 58;
    }

    @Override
    protected int[] render(GuiGraphicsExtractor g, int x, int y, boolean right) {
        for (int i = 0; i < SLOTS.length; i++) {
            ItemStack stack = mc.player.getItemBySlot(SLOTS[i]);
            int cx = x + i * (CELL_W + GAP);

            Theme.roundRect(g, cx, y, CELL_W, CELL_H, 0xB0161620);
            Theme.border(g, cx, y, CELL_W, CELL_H, 0x30FFFFFF);

            if (stack.isEmpty()) {
                g.text(mc.font, LABELS[i], cx + 4, y + 4, Theme.TEXT_OFF);
                continue;
            }

            float left = stack.isDamageableItem() ? 1f - stack.getDamageValue() / (float) stack.getMaxDamage() : 1f;
            int color = left > 0.5f ? 0xFF55DD66 : left > 0.2f ? 0xFFFFC94A : 0xFFFF5555;

            g.text(mc.font, LABELS[i], cx + 4, y + 3, Theme.TEXT);
            if (stack.isDamageableItem()) {
                String pct = Math.round(left * 100) + "%";
                g.text(mc.font, pct, cx + CELL_W - 4 - mc.font.width(pct), y + 3, color);
                g.fill(cx + 2, y + 12, cx + CELL_W - 2, y + 13, Theme.TRACK);
                g.fill(cx + 2, y + 12, cx + 2 + (int) ((CELL_W - 4) * left), y + 13, color);
            } else {
                g.text(mc.font, "ok", cx + CELL_W - 4 - mc.font.width("ok"), y + 3, color);
            }
        }
        return new int[]{SLOTS.length * CELL_W + (SLOTS.length - 1) * GAP, CELL_H};
    }
}
