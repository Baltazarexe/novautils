package dev.novautils.modules.player;

import dev.novautils.modules.Category;
import dev.novautils.modules.Module;
import dev.novautils.settings.Setting;
import dev.novautils.utils.InvUtils;
import dev.novautils.utils.PlatformCompat;
import dev.novautils.utils.Rotations;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.item.enchantment.Enchantments;

/**
 * Repairs your Mending gear with XP bottles: when a worn piece (or what you hold) is damaged past
 * the threshold, it throws bottles straight down at your feet - looking down server-side only, your
 * view doesn't move - until everything is repaired or you run out. Bottles come from the hotbar
 * and it switches back to what you held.
 */
public class AutoMend extends Module {
    private final Setting<Integer> threshold = register(Setting.builder(Integer.class, "repair-below")
        .description("Start when a Mending item is below this % durability.").defaultValue(80).range(10, 99).build());

    private final Setting<Integer> delay = register(Setting.builder(Integer.class, "delay-ticks")
        .description("Ticks between bottles.").defaultValue(2).range(1, 20).build());

    private final Setting<Boolean> handItems = register(Setting.builder(Boolean.class, "hand-items")
        .description("Also repair the items in your hands, not just armour.").defaultValue(true).build());

    private final Setting<Boolean> pauseInMenus = register(Setting.builder(Boolean.class, "pause-in-menus")
        .description("Don't throw while a menu is open.").defaultValue(true).build());

    private static final EquipmentSlot[] ARMOR = {EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET};

    private int cooldown;
    private boolean mending;

    public AutoMend() {
        super("AutoMend", "Throws XP bottles to repair Mending gear.", Category.PLAYER);
    }

    public boolean isMending() {
        return mending;
    }

    private boolean needsRepair(ItemStack s, int belowPercent) {
        if (s.isEmpty() || !s.isDamageableItem() || s.getDamageValue() == 0) return false;
        boolean hasMending = EnchantmentHelper.getItemEnchantmentLevel(Enchantments.MENDING, s) > 0;
        if (!hasMending) return false;
        double left = 100.0 * (s.getMaxDamage() - s.getDamageValue()) / s.getMaxDamage();
        return left < belowPercent;
    }

    /** Started below the threshold: keep going until fully repaired (99.9%). */
    private boolean anythingToMend() {
        int limit = mending ? 100 : threshold.get();
        for (EquipmentSlot slot : ARMOR) if (needsRepair(mc.player.getItemBySlot(slot), limit)) return true;
        if (handItems.get()) {
            return needsRepair(mc.player.getMainHandItem(), limit) || needsRepair(mc.player.getOffhandItem(), limit);
        }
        return false;
    }

    @Override
    public void onTick() {
        var p = mc.player;
        if (p == null || mc.level == null || mc.gameMode == null) return;
        if (pauseInMenus.get() && PlatformCompat.currentScreen(mc) != null) return;
        if (cooldown-- > 0 || Rotations.busy() || p.isUsingItem()) return;

        mending = anythingToMend();
        if (!mending) return;

        int slot = -1;
        var inv = p.getInventory();
        for (int i = 0; i < 9; i++) if (inv.getItem(i).is(Items.EXPERIENCE_BOTTLE)) slot = i;
        if (slot == -1) return;

        int bottleSlot = slot;
        Rotations.rotate(p.getYRot(), 90f, () -> {
            int previous = inv.selected;
            if (bottleSlot != previous) InvUtils.selectHotbar(bottleSlot);
            // the use packet carries the rotation, so face down for this one call
            float realPitch = p.getXRot();
            p.setXRot(90f);
            mc.gameMode.useItem(p, InteractionHand.MAIN_HAND);
            p.setXRot(realPitch);
            if (bottleSlot != previous) InvUtils.selectHotbar(previous);
        });
        cooldown = delay.get();
    }
}
