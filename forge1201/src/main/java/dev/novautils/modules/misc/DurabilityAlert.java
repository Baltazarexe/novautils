package dev.novautils.modules.misc;

import dev.novautils.gui.Toasts;
import dev.novautils.modules.Category;
import dev.novautils.modules.Module;
import dev.novautils.settings.Setting;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.ItemStack;

import java.util.EnumMap;
import java.util.Map;

/**
 * Warns once (toast + sound) when a piece of armour, the elytra or the tool in your hand drops
 * below a durability threshold, so nothing breaks by surprise. It warns again only after that
 * item got repaired above the threshold or you swapped it.
 */
public class DurabilityAlert extends Module {
    private final Setting<Integer> threshold = register(Setting.builder(Integer.class, "threshold")
        .description("Warn below this much durability left, in percent.").defaultValue(15).range(1, 60).build());

    private final Setting<Boolean> hands = register(Setting.builder(Boolean.class, "hands")
        .description("Also watch the items in your hands.").defaultValue(true).build());

    private final Setting<Boolean> sound = register(Setting.builder(Boolean.class, "sound")
        .description("Play a sound with the warning.").defaultValue(true).build());

    private static final EquipmentSlot[] ARMOR = {EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET};
    private static final EquipmentSlot[] HANDS = {EquipmentSlot.MAINHAND, EquipmentSlot.OFFHAND};

    /** The item we already warned about in each slot. */
    private final Map<EquipmentSlot, ItemStack> warned = new EnumMap<>(EquipmentSlot.class);

    public DurabilityAlert() {
        super("DurabilityAlert", "Warns when armour, elytra or your tool is about to break.", Category.MISC);
    }

    @Override
    public void onEnable() {
        warned.clear();
    }

    private void check(EquipmentSlot slot) {
        ItemStack stack = mc.player.getItemBySlot(slot);
        if (stack.isEmpty() || !stack.isDamageableItem()) {
            warned.remove(slot);
            return;
        }
        float left = 1f - stack.getDamageValue() / (float) stack.getMaxDamage();
        ItemStack before = warned.get(slot);
        boolean same = before != null && ItemStack.isSameItem(before, stack);
        if (left * 100 >= threshold.get()) {
            if (same) warned.remove(slot);
            return;
        }
        if (same) return;
        warned.put(slot, stack.copy());
        int uses = stack.getMaxDamage() - stack.getDamageValue();
        Toasts.push(stack.getHoverName().getString() + " low: " + uses + " uses left", 0xFFFF7A4A);
        if (sound.get()) mc.getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.ITEM_BREAK, 1.4f, 0.6f));
    }

    @Override
    public void onTick() {
        if (mc.player == null || mc.player.tickCount % 10 != 0) return;
        for (EquipmentSlot s : ARMOR) check(s);
        if (hands.get()) for (EquipmentSlot s : HANDS) check(s);
    }
}
