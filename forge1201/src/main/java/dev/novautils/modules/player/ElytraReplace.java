package dev.novautils.modules.player;

import dev.novautils.modules.Category;
import dev.novautils.modules.Module;
import dev.novautils.settings.Setting;
import dev.novautils.utils.InvUtils;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

/**
 * Swaps your elytra for a fresh one from the inventory before it breaks - the difference between
 * a long highway flight and falling out of the sky. Works mid-flight.
 */
public class ElytraReplace extends Module {
    private static final int CHEST_MENU_SLOT = 6;

    private final Setting<Integer> durability = register(Setting.builder(Integer.class, "min-durability")
        .description("Swap when the worn elytra has this many uses left or fewer.").defaultValue(10).range(1, 100).build());

    private int cooldown;

    public ElytraReplace() {
        super("ElytraReplace", "Swaps in a fresh elytra before yours breaks.", Category.PLAYER);
    }

    private static int left(ItemStack s) {
        return s.getMaxDamage() - s.getDamageValue();
    }

    @Override
    public void onTick() {
        if (!InvUtils.canClick() || cooldown-- > 0) return;
        ItemStack worn = mc.player.inventoryMenu.getSlot(CHEST_MENU_SLOT).getItem();
        if (!worn.is(Items.ELYTRA) || left(worn) > durability.get()) return;

        int best = -1, bestLeft = durability.get();
        for (int menuSlot = 9; menuSlot < 45; menuSlot++) {
            ItemStack s = mc.player.inventoryMenu.getSlot(menuSlot).getItem();
            if (s.is(Items.ELYTRA) && left(s) > bestLeft) {
                best = menuSlot;
                bestLeft = left(s);
            }
        }
        if (best == -1) return;
        InvUtils.pickup(best);
        InvUtils.pickup(CHEST_MENU_SLOT);
        InvUtils.pickup(best);
        cooldown = 10;
    }
}
