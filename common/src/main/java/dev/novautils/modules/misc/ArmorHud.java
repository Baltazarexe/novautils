package dev.novautils.modules.misc;

import dev.novautils.gui.Theme;
import dev.novautils.modules.Category;
import dev.novautils.modules.Module;
import dev.novautils.settings.Setting;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.ItemStack;

/**
 * Durability bars for the armour you are wearing, above the hotbar. Drawn with plain rectangles
 * and text on purpose: item icons do not render correctly from a HUD element in this version.
 */
public class ArmorHud extends Module {
    private static final EquipmentSlot[] SLOTS = {EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET};
    private static final String[] LABELS = {"H", "C", "L", "B"};

    private final Setting<Integer> offsetY = register(Setting.builder(Integer.class, "offset-y")
        .description("Pixels above the bottom of the screen.").defaultValue(58).range(20, 160).build());

    public ArmorHud() {
        super("ArmorHud", "Shows the durability of the armour you are wearing.", Category.MISC);
    }

    @Override
    public void onRenderHud(GuiGraphicsExtractor g, DeltaTracker deltaTracker) {
        if (mc.player == null) return;

        int cellW = 34, gap = 3;
        int totalW = SLOTS.length * cellW + (SLOTS.length - 1) * gap;
        int x = g.guiWidth() / 2 - totalW / 2;
        int y = g.guiHeight() - offsetY.get();

        for (int i = 0; i < SLOTS.length; i++) {
            ItemStack stack = mc.player.getItemBySlot(SLOTS[i]);
            int cx = x + i * (cellW + gap);

            Theme.roundRect(g, cx, y, cellW, 15, 0xB0161620);
            Theme.border(g, cx, y, cellW, 15, 0x30FFFFFF);

            if (stack.isEmpty()) {
                g.text(mc.font, LABELS[i], cx + 4, y + 4, Theme.TEXT_OFF);
                continue;
            }

            float left = stack.isDamageableItem()
                ? 1f - stack.getDamageValue() / (float) stack.getMaxDamage()
                : 1f;
            int color = left > 0.5f ? 0xFF55DD66 : left > 0.2f ? 0xFFFFC94A : 0xFFFF5555;

            g.text(mc.font, LABELS[i], cx + 4, y + 3, Theme.TEXT);
            if (stack.isDamageableItem()) {
                String pct = Math.round(left * 100) + "%";
                g.text(mc.font, pct, cx + cellW - 4 - mc.font.width(pct), y + 3, color);
                g.fill(cx + 2, y + 12, cx + cellW - 2, y + 13, Theme.TRACK);
                g.fill(cx + 2, y + 12, cx + 2 + (int) ((cellW - 4) * left), y + 13, color);
            } else {
                g.text(mc.font, "ok", cx + cellW - 4 - mc.font.width("ok"), y + 3, color);
            }
        }
    }
}
