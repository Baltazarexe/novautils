package dev.novautils.modules.render;

import dev.novautils.gui.Theme;
import dev.novautils.modules.Category;
import dev.novautils.modules.HudModule;
import dev.novautils.settings.Setting;
import dev.novautils.utils.Friends;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.player.Player;

/**
 * A small panel for the creature under your crosshair: name, a health bar with the numbers,
 * armour points and distance. Information only. Drag it anywhere in the HUD editor.
 */
public class TargetHud extends HudModule {
    private final Setting<Boolean> armor = register(Setting.builder(Boolean.class, "armor")
        .description("Show armour points.").defaultValue(true).build());

    private final Setting<Boolean> distance = register(Setting.builder(Boolean.class, "distance")
        .description("Show how far away it is.").defaultValue(true).build());

    private final Setting<Double> linger = register(Setting.builder(Double.class, "linger-seconds")
        .description("Keep showing the last target this long after you look away.").defaultValue(2.0).range(0.0, 10.0).build());

    private LivingEntity last;
    private long lastSeen;

    public TargetHud() {
        super("TargetHud", "Name, health, armour and distance of what you look at.", Category.RENDER);
    }

    @Override
    protected int defaultX(int screenW, int w) {
        return screenW / 2 + 12;
    }

    @Override
    protected int defaultY(int screenH, int h) {
        return screenH / 2 + 24;
    }

    @Override
    protected int[] render(GuiGraphics g, int x, int y, boolean right) {
        LivingEntity target = mc.crosshairPickEntity instanceof LivingEntity l && l.isAlive() ? l : null;
        long now = System.currentTimeMillis();
        if (target != null) {
            last = target;
            lastSeen = now;
        } else if (last != null && last.isAlive() && now - lastSeen < linger.get() * 1000) {
            target = last;
        }
        String name;
        float hp, max;
        int armorPoints;
        double dist;
        int nameColor;
        if (target == null) {
            if (!editing) return new int[]{0, 0};
            name = "Zombie";
            hp = 14f;
            max = 20f;
            armorPoints = 6;
            dist = 4.2;
            nameColor = 0xFFFFA040;
        } else {
            name = target.getName().getString();
            hp = target.getHealth();
            max = target.getMaxHealth();
            armorPoints = target.getArmorValue();
            dist = mc.player.distanceTo(target);
            nameColor = target instanceof Player p ? (Friends.isFriend(p) ? 0xFF55E6FF : 0xFFFF5A5A)
                : target instanceof Enemy ? 0xFFFFA040 : 0xFF66E07A;
        }

        int w = 124, h = 34;
        Theme.softShadow(g, x, y, w, h, 6, 0.5f);
        Theme.rounded(g, x, y, w, h, 6, 0xE0141519);
        g.drawString(mc.font, mc.font.plainSubstrByWidth(name, w - 12), x + 6, y + 5, nameColor, true);

        float f = Math.max(0f, Math.min(1f, hp / Math.max(1f, max)));
        int by = y + 17, bw = w - 12;
        float[] anim = target != null ? dev.novautils.utils.HealthDisplay.animated(target) : new float[]{f, f};
        dev.novautils.utils.HealthDisplay.pill(g, x + 6, by, bw, 6, anim[0], anim[1], f, 1f);

        String left = String.format(java.util.Locale.ROOT, "%.1f / %.0f", hp, max);
        g.drawString(mc.font, left, x + 6, y + 24, Theme.TEXT, false);
        StringBuilder rightText = new StringBuilder();
        if (armor.get() && armorPoints > 0) rightText.append("armor ").append(armorPoints).append("  ");
        if (distance.get()) rightText.append(String.format(java.util.Locale.ROOT, "%.1fm", dist));
        String r = rightText.toString().trim();
        g.drawString(mc.font, r, x + w - 6 - mc.font.width(r), y + 24, Theme.TEXT_DIM, false);
        return new int[]{w, h};
    }
}
