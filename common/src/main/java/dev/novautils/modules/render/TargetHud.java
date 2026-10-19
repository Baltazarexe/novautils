package dev.novautils.modules.render;

import dev.novautils.gui.Theme;
import dev.novautils.modules.Category;
import dev.novautils.modules.HudModule;
import dev.novautils.settings.Setting;
import dev.novautils.utils.Friends;
import net.minecraft.client.gui.GuiGraphicsExtractor;
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

    private final Setting<Double> range = register(Setting.builder(Double.class, "range")
        .description("How far away it picks a target (not limited by your interaction reach).").defaultValue(30.0).range(4.0, 100.0).build());

    private LivingEntity last;
    private long lastSeen;

    public TargetHud() {
        super("TargetHud", "Name, health, armour and distance of what you look at.", Category.RENDER);
    }

    /** The creature under the crosshair, found with our own ray so survival's 3-block reach doesn't limit it. Walls block it. */
    private LivingEntity lookedAt() {
        var eye = mc.player.getEyePosition();
        var end = eye.add(mc.player.getLookAngle().scale(range.get()));
        double maxDist = range.get();
        var block = mc.level.clip(new net.minecraft.world.level.ClipContext(eye, end,
            net.minecraft.world.level.ClipContext.Block.COLLIDER, net.minecraft.world.level.ClipContext.Fluid.NONE, mc.player));
        if (block.getType() != net.minecraft.world.phys.HitResult.Type.MISS) maxDist = block.getLocation().distanceTo(eye);
        LivingEntity best = null;
        double bestDist = maxDist;
        var area = new net.minecraft.world.phys.AABB(eye, end).inflate(1.0);
        for (net.minecraft.world.entity.Entity e : mc.level.getEntities((net.minecraft.world.entity.Entity) null, area,
            x -> x instanceof LivingEntity && x != mc.player && x.isAlive() && !x.isSpectator() && !x.isInvisible())) {
            var hit = e.getBoundingBox().inflate(e.getPickRadius() + 0.1).clip(eye, end);
            if (hit.isEmpty()) continue;
            double d = hit.get().distanceTo(eye);
            if (d <= bestDist) {
                bestDist = d;
                best = (LivingEntity) e;
            }
        }
        return best;
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
    protected int[] render(GuiGraphicsExtractor g, int x, int y, boolean right) {
        LivingEntity target = lookedAt();
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

        int w = 150, h = 34;
        Theme.softShadow(g, x, y, w, h, 6, 0.5f);
        Theme.rounded(g, x, y, w, h, 6, 0xE0141519);
        g.text(mc.font, mc.font.plainSubstrByWidth(name, w - 12), x + 6, y + 5, nameColor, true);

        float f = Math.max(0f, Math.min(1f, hp / Math.max(1f, max)));
        int by = y + 17, bw = w - 12;
        float[] anim = target != null ? dev.novautils.utils.HealthDisplay.animated(target) : new float[]{f, f};
        dev.novautils.utils.HealthDisplay.pill(g, x + 6, by, bw, 6, anim[0], anim[1], f, 1f);

        String left = String.format(java.util.Locale.ROOT, "%.1f / %.0f", hp, max);
        g.text(mc.font, left, x + 6, y + 24, Theme.TEXT, false);
        StringBuilder rightText = new StringBuilder();
        if (armor.get() && armorPoints > 0) rightText.append("armor ").append(armorPoints).append("  ");
        if (distance.get()) rightText.append(String.format(java.util.Locale.ROOT, "%.1fm", dist));
        String r = rightText.toString().trim();
        g.text(mc.font, r, x + w - 6 - mc.font.width(r), y + 24, Theme.TEXT_DIM, false);
        return new int[]{w, h};
    }
}
