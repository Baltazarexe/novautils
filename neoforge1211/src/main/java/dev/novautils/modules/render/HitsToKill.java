package dev.novautils.modules.render;

import dev.novautils.gui.Theme;
import dev.novautils.modules.Category;
import dev.novautils.modules.Module;
import dev.novautils.settings.Setting;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attributes;

/**
 * Under the crosshair, for the mob you're looking at: its health and roughly how many full-charge
 * hits of what you're holding it still takes. Armour and enchantments aren't counted, so it's an
 * estimate ("~"), but it's right for most mobs and tells you when to switch weapons.
 */
public class HitsToKill extends Module {
    private final Setting<Boolean> crits = register(Setting.builder(Boolean.class, "count-crits")
        .description("Count hits as critical (x1.5), e.g. when jumping or with Criticals.").defaultValue(false).build());

    private final Setting<Boolean> bar = register(Setting.builder(Boolean.class, "health-bar")
        .description("A small health bar under the text.").defaultValue(true).build());

    public HitsToKill() {
        super("HitsToKill", "Shows a mob's health and how many hits it still needs.", Category.RENDER);
    }

    /** Held weapon damage. The server doesn't sync attack damage to the client, so add it up from the item. */
    public double damage() {
        var stack = mc.player.getMainHandItem();
        double base = mc.player.getAttributeBaseValue(Attributes.ATTACK_DAMAGE);
        double[] add = {0, 0, 1}; // flat, x base, x total
        stack.forEachModifier(net.minecraft.world.entity.EquipmentSlot.MAINHAND, (attr, mod) -> {
            if (attr.value() != Attributes.ATTACK_DAMAGE.value()) return;
            switch (mod.operation()) {
                case ADD_VALUE -> add[0] += mod.amount();
                case ADD_MULTIPLIED_BASE -> add[1] += mod.amount();
                case ADD_MULTIPLIED_TOTAL -> add[2] *= 1 + mod.amount();
            }
        });
        double dmg = (base + add[0]) * (1 + add[1]) * add[2];
        int sharp = mc.level.registryAccess().lookupOrThrow(net.minecraft.core.registries.Registries.ENCHANTMENT)
            .get(net.minecraft.world.item.enchantment.Enchantments.SHARPNESS)
            .map(h -> net.minecraft.world.item.enchantment.EnchantmentHelper.getItemEnchantmentLevel(h, stack)).orElse(0);
        if (sharp > 0) dmg += 0.5 * sharp + 0.5;
        return dmg;
    }

    @Override
    public void onRenderHud(GuiGraphics g, float deltaTracker) {
        if (mc.player == null || !(mc.crosshairPickEntity instanceof LivingEntity target) || !target.isAlive()) return;
        float hp = target.getHealth() + target.getAbsorptionAmount();
        double dmg = damage();
        if (crits.get()) dmg *= 1.5;
        int hits = dmg <= 0 ? 0 : (int) Math.ceil(hp / dmg);

        String text = String.format("%.1f❤  ~%d hit%s", hp, hits, hits == 1 ? "" : "s");
        int color = hits <= 1 ? 0xFF66DD77 : hits <= 3 ? 0xFFFFD84A : 0xFFFF7A7A;
        int cx = g.guiWidth() / 2, y = g.guiHeight() / 2 + 10;
        int w = mc.font.width(text);
        Theme.rounded(g, cx - w / 2 - 4, y - 2, w + 8, mc.font.lineHeight + (bar.get() ? 6 : 3), 3, 0x90000000);
        g.drawString(mc.font, text, cx - w / 2, y, color, true);
        if (bar.get()) {
            float f = Math.max(0f, Math.min(1f, target.getHealth() / target.getMaxHealth()));
            int by = y + mc.font.lineHeight + 1;
            g.fill(cx - w / 2, by, cx + w / 2, by + 2, Theme.TRACK);
            g.fill(cx - w / 2, by, cx - w / 2 + (int) (w * f), by + 2, color);
        }
    }
}
