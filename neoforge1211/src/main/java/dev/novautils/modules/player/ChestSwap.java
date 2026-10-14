package dev.novautils.modules.player;

import dev.novautils.modules.Category;
import dev.novautils.modules.Module;
import dev.novautils.utils.InvUtils;
import net.minecraft.core.component.DataComponents;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

/** One-shot: swaps your worn chest piece between elytra and chestplate, then turns itself off. Bind a key to it. */
public class ChestSwap extends Module {
    private static final int CHEST_MENU_SLOT = 6;

    public ChestSwap() {
        super("ChestSwap", "Swap between elytra and chestplate (bind a key).", Category.PLAYER);
    }

    private static boolean isChestPlate(ItemStack stack) {
        return stack.getItem() instanceof net.minecraft.world.item.ArmorItem a && a.getEquipmentSlot() == EquipmentSlot.CHEST;
    }

    @Override
    public void onEnable() {
        if (InvUtils.canClick()) {
            boolean wearingElytra = mc.player.inventoryMenu.getSlot(CHEST_MENU_SLOT).getItem().is(Items.ELYTRA);
            for (int menuSlot = 9; menuSlot < 45; menuSlot++) {
                ItemStack stack = mc.player.inventoryMenu.getSlot(menuSlot).getItem();
                boolean match = wearingElytra ? isChestPlate(stack) : stack.is(Items.ELYTRA);
                if (match) {
                    InvUtils.pickup(menuSlot);
                    InvUtils.pickup(CHEST_MENU_SLOT);
                    InvUtils.pickup(menuSlot);
                    break;
                }
            }
        }
        setEnabled(false);
    }
}
