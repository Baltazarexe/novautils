package dev.novautils.modules.combat;

import dev.novautils.modules.Category;
import dev.novautils.modules.Module;
import dev.novautils.settings.Setting;
import dev.novautils.utils.InvUtils;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.ItemAttributeModifiers;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.item.enchantment.Enchantments;

/**
 * Wears your best armour. Pieces are ranked by their real armour + toughness values plus
 * protection enchantments (the kind you pick favours blast protection for crystal PvP areas), so a
 * Protection IV diamond piece beats a plain one. Never swaps in cursed-of-binding pieces, keeps an
 * elytra on if you're wearing one (optional), and skips nearly broken pieces.
 */
public class AutoArmor extends Module {
    public enum Prefer { PROTECTION, BLAST_PROTECTION, FIRE_PROTECTION, PROJECTILE_PROTECTION }

    private static final EquipmentSlot[] SLOTS = {EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET};

    private final Setting<Prefer> prefer = register(Setting.builder(Prefer.class, "prefer")
        .description("Which protection enchantment counts the most.")
        .defaultValue(Prefer.PROTECTION).build());

    private final Setting<Boolean> keepElytra = register(Setting.builder(Boolean.class, "keep-elytra")
        .description("Don't replace an elytra you're wearing with a chestplate.")
        .defaultValue(true).build());

    private final Setting<Integer> minDurability = register(Setting.builder(Integer.class, "min-durability")
        .description("Ignore pieces with less than this % durability left.")
        .defaultValue(5).range(0, 50).build());

    private final Setting<Integer> delay = register(Setting.builder(Integer.class, "delay-ticks")
        .description("Ticks between inventory clicks.")
        .defaultValue(4).range(1, 20).build());

    private int cooldown;

    public AutoArmor() {
        super("AutoArmor", "Automatically wears your best armor.", Category.COMBAT);
    }

    private int level(ItemStack stack, ResourceKey<Enchantment> key) {
        var lookup = mc.level.registryAccess().lookupOrThrow(Registries.ENCHANTMENT);
        return lookup.get(key).map(h -> EnchantmentHelper.getItemEnchantmentLevel(h, stack)).orElse(0);
    }

    /** Flat bonus of one attribute from an item's attribute modifiers (1.21.1 has no per-attribute compute). */
    private static double sum(ItemAttributeModifiers mods, net.minecraft.core.Holder<net.minecraft.world.entity.ai.attributes.Attribute> attribute) {
        double s = 0;
        for (var entry : mods.modifiers()) {
            if (entry.attribute().equals(attribute) && entry.modifier().operation() == net.minecraft.world.entity.ai.attributes.AttributeModifier.Operation.ADD_VALUE) s += entry.modifier().amount();
        }
        return s;
    }

    /** -1 = not wearable in this slot (or a piece we refuse to wear). */
    private double score(ItemStack stack, EquipmentSlot slot) {
        if (stack.isEmpty()) return -1;
        if (!(stack.getItem() instanceof net.minecraft.world.item.ArmorItem armorItem) || armorItem.getEquipmentSlot() != slot) return -1;
        if (level(stack, Enchantments.BINDING_CURSE) > 0) return -1;
        if (stack.isDamageableItem()) {
            double left = 1.0 - stack.getDamageValue() / (double) stack.getMaxDamage();
            if (left * 100 < minDurability.get()) return -1;
        }

        ItemAttributeModifiers mods = stack.getOrDefault(DataComponents.ATTRIBUTE_MODIFIERS, ItemAttributeModifiers.EMPTY);
        double armor = sum(mods, Attributes.ARMOR);
        double tough = sum(mods, Attributes.ARMOR_TOUGHNESS);
        double knock = sum(mods, Attributes.KNOCKBACK_RESISTANCE);

        ResourceKey<Enchantment> preferred = switch (prefer.get()) {
            case PROTECTION -> Enchantments.PROTECTION;
            case BLAST_PROTECTION -> Enchantments.BLAST_PROTECTION;
            case FIRE_PROTECTION -> Enchantments.FIRE_PROTECTION;
            case PROJECTILE_PROTECTION -> Enchantments.PROJECTILE_PROTECTION;
        };
        double ench = level(stack, preferred) * 2.0
            + (level(stack, Enchantments.PROTECTION) + level(stack, Enchantments.BLAST_PROTECTION)
            + level(stack, Enchantments.FIRE_PROTECTION) + level(stack, Enchantments.PROJECTILE_PROTECTION)) * 0.5
            + level(stack, Enchantments.UNBREAKING) * 0.1 + level(stack, Enchantments.MENDING) * 0.2;
        return armor * 10 + tough * 4 + knock * 10 + ench;
    }

    @Override
    public void onTick() {
        if (!InvUtils.canClick() || mc.level == null) return;
        if (cooldown-- > 0) return;

        for (int i = 0; i < 4; i++) {
            EquipmentSlot slot = SLOTS[i];
            int armorMenuSlot = 5 + i;
            ItemStack worn = mc.player.inventoryMenu.getSlot(armorMenuSlot).getItem();
            if (slot == EquipmentSlot.CHEST && keepElytra.get() && worn.is(Items.ELYTRA)) continue;
            if (!worn.isEmpty() && level(worn, Enchantments.BINDING_CURSE) > 0) continue;

            double current = score(worn, slot);
            int bestSlot = -1;
            double bestScore = current;
            for (int menuSlot = 9; menuSlot < 45; menuSlot++) {
                double s = score(mc.player.inventoryMenu.getSlot(menuSlot).getItem(), slot);
                if (s > bestScore) {
                    bestScore = s;
                    bestSlot = menuSlot;
                }
            }
            if (bestSlot == -1) continue;

            // take the old piece off first (next pass puts the new one on), else shift-click it on
            if (!worn.isEmpty()) InvUtils.quickMove(armorMenuSlot);
            else InvUtils.quickMove(bestSlot);
            cooldown = delay.get();
            return;
        }
    }
}
