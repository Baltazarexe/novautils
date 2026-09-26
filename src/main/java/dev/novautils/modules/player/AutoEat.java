package dev.novautils.modules.player;

import dev.novautils.modules.Category;
import dev.novautils.modules.Module;
import dev.novautils.settings.Setting;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.protocol.game.ServerboundSetCarriedItemPacket;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;

/**
 * Eats food from your hotbar when hunger drops low, then puts your selected slot back.
 * Only looks at the hotbar (slots 1-9), it won't dig through the main inventory.
 */
public class AutoEat extends Module {
    private final Setting<Integer> hungerThreshold = register(Setting.builder(Integer.class, "hunger-threshold")
        .description("Eat when food level is at or below this (max is 20).")
        .defaultValue(14)
        .range(1, 19)
        .build());

    private int previousSlot = -1;

    public AutoEat() {
        super("AutoEat", "Eats food from your hotbar when you're hungry.", Category.MISC);
    }

    @Override
    public void onDisable() {
        restoreSlot();
    }

    private void selectSlot(int slot) {
        mc.player.getInventory().setSelectedSlot(slot);
        if (mc.getConnection() != null) {
            mc.getConnection().send(new ServerboundSetCarriedItemPacket(slot));
        }
    }

    private void restoreSlot() {
        if (previousSlot != -1 && mc.player != null) {
            selectSlot(previousSlot);
        }
        previousSlot = -1;
    }

    @Override
    public void onTick() {
        if (mc.player == null || mc.gameMode == null) return;

        // Finished eating: put the original slot back (switching earlier would cancel the meal).
        if (previousSlot != -1) {
            if (!mc.player.isUsingItem()) restoreSlot();
            return;
        }

        if (mc.player.getFoodData().getFoodLevel() > hungerThreshold.get()) return;
        if (mc.player.isUsingItem()) return;

        for (int slot = 0; slot < 9; slot++) {
            ItemStack stack = mc.player.getInventory().getItem(slot);
            if (stack.isEmpty() || !stack.has(DataComponents.FOOD)) continue;

            previousSlot = mc.player.getInventory().getSelectedSlot();
            selectSlot(slot);
            mc.gameMode.useItem(mc.player, InteractionHand.MAIN_HAND);
            return;
        }
    }
}
