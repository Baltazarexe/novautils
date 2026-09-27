package dev.novautils.modules.player;

import dev.novautils.modules.Category;
import dev.novautils.modules.Module;
import dev.novautils.settings.Setting;
import dev.novautils.utils.InvUtils;
import net.minecraft.world.item.ItemStack;

/** Refills a hotbar stack from your main inventory when it runs low. */
public class AutoReplenish extends Module {
    private final Setting<Integer> threshold = register(Setting.builder(Integer.class, "threshold")
        .description("Refill when a stack has this many items or fewer.")
        .defaultValue(8).range(1, 32).build());

    private int cooldown;

    public AutoReplenish() {
        super("AutoReplenish", "Refills low hotbar stacks from your inventory.", Category.PLAYER);
    }

    @Override
    public void onTick() {
        if (!InvUtils.canClick()) return;
        if (cooldown-- > 0) return;

        var inv = mc.player.getInventory();
        for (int hot = 0; hot < 9; hot++) {
            ItemStack stack = inv.getItem(hot);
            if (stack.isEmpty() || stack.getMaxStackSize() <= 1) continue;
            if (stack.getCount() > threshold.get() || stack.getCount() >= stack.getMaxStackSize()) continue;

            for (int src = 9; src < 36; src++) {
                ItemStack other = inv.getItem(src);
                if (!other.isEmpty() && ItemStack.isSameItemSameComponents(stack, other)) {
                    InvUtils.swap(InvUtils.menuSlot(src), hot);
                    cooldown = 5;
                    return;
                }
            }
        }
    }
}
