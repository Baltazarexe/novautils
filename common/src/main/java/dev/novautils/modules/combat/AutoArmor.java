package dev.novautils.modules.combat;

import dev.novautils.modules.Category;
import dev.novautils.modules.Module;
import dev.novautils.utils.InvUtils;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.equipment.Equippable;

/** Wears the best armor piece you are carrying (ranked by material). Elytra is left alone. */
public class AutoArmor extends Module {
    private static final EquipmentSlot[] SLOTS = {EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET};

    private int cooldown;

    public AutoArmor() {
        super("AutoArmor", "Automatically wears your best armor.", Category.COMBAT);
    }

    /** -1 = not armor for this slot. */
    private static int score(ItemStack stack, EquipmentSlot slot) {
        if (stack.isEmpty()) return -1;
        Equippable eq = stack.get(DataComponents.EQUIPPABLE);
        if (eq == null || eq.slot() != slot) return -1;

        String path = BuiltInRegistries.ITEM.getKey(stack.getItem()).getPath();
        if (path.contains("elytra")) return -1;
        if (path.startsWith("netherite")) return 6;
        if (path.startsWith("diamond")) return 5;
        if (path.startsWith("iron")) return 4;
        if (path.startsWith("chainmail")) return 3;
        if (path.startsWith("golden")) return 2;
        if (path.startsWith("copper")) return 2;
        if (path.startsWith("leather") || path.startsWith("turtle")) return 1;
        return 0;
    }

    @Override
    public void onTick() {
        if (!InvUtils.canClick()) return;
        if (cooldown-- > 0) return;

        for (int i = 0; i < 4; i++) {
            EquipmentSlot slot = SLOTS[i];
            int armorMenuSlot = 5 + i;
            int current = score(mc.player.inventoryMenu.getSlot(armorMenuSlot).getItem(), slot);

            int bestSlot = -1;
            int bestScore = current;
            for (int menuSlot = 9; menuSlot < 45; menuSlot++) {
                int s = score(mc.player.inventoryMenu.getSlot(menuSlot).getItem(), slot);
                if (s > bestScore) {
                    bestScore = s;
                    bestSlot = menuSlot;
                }
            }
            if (bestSlot == -1) continue;

            if (current >= 0) InvUtils.quickMove(armorMenuSlot); // take the old piece off first
            else InvUtils.quickMove(bestSlot);                   // shift-click equips into the empty slot
            cooldown = 4;
            return;
        }
    }
}
