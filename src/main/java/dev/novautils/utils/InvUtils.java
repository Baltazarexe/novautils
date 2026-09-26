package dev.novautils.utils;

import net.minecraft.client.Minecraft;
import net.minecraft.network.protocol.game.ServerboundSetCarriedItemPacket;
import net.minecraft.world.inventory.ContainerInput;

/** Inventory helpers. "Menu slot" = index in the player's InventoryMenu (hotbar 36-44, main 9-35, armor 5-8, offhand 45). */
public final class InvUtils {
    private InvUtils() {}

    public static boolean canClick() {
        Minecraft mc = Minecraft.getInstance();
        return mc.player != null && mc.gameMode != null && mc.player.containerMenu == mc.player.inventoryMenu;
    }

    /** Inventory index (0-8 hotbar, 9-35 main) -> InventoryMenu slot. */
    public static int menuSlot(int invIndex) {
        return invIndex < 9 ? 36 + invIndex : invIndex;
    }

    private static void click(int menuSlot, int button, ContainerInput type) {
        Minecraft mc = Minecraft.getInstance();
        mc.gameMode.handleContainerInput(mc.player.inventoryMenu.containerId, menuSlot, button, type, mc.player);
    }

    /** Swap a menu slot with a hotbar slot (button 0-8) or the offhand (button 40). */
    public static void swap(int menuSlot, int button) {
        click(menuSlot, button, ContainerInput.SWAP);
    }

    public static void quickMove(int menuSlot) {
        click(menuSlot, 0, ContainerInput.QUICK_MOVE);
    }

    public static void pickup(int menuSlot) {
        click(menuSlot, 0, ContainerInput.PICKUP);
    }

    public static void selectHotbar(int slot) {
        Minecraft mc = Minecraft.getInstance();
        mc.player.getInventory().setSelectedSlot(slot);
        if (mc.getConnection() != null) mc.getConnection().send(new ServerboundSetCarriedItemPacket(slot));
    }
}
