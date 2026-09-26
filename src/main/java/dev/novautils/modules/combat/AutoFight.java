package dev.novautils.modules.combat;

import dev.novautils.modules.Category;
import dev.novautils.modules.Module;
import dev.novautils.settings.Setting;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.entity.player.Player;

/**
 * Automatically attacks nearby mobs so you don't have to click repeatedly while farming/grinding.
 * {@code Player} is hard-excluded from targeting below (not just a default setting) - this module
 * will never attack another real player, on purpose.
 */
public class AutoFight extends Module {
    private final Setting<Double> range = register(Setting.builder(Double.class, "range")
        .description("Max distance to attack mobs at.")
        .defaultValue(4.0)
        .range(2.0, 6.0)
        .build());

    private final Setting<Boolean> hostiles = register(Setting.builder(Boolean.class, "hostile-mobs")
        .description("Attack hostile mobs (zombies, skeletons, ...).")
        .defaultValue(true)
        .build());

    private final Setting<Boolean> passives = register(Setting.builder(Boolean.class, "passive-mobs")
        .description("Attack passive/neutral mobs and animals.")
        .defaultValue(false)
        .build());

    private final Setting<Integer> delayTicks = register(Setting.builder(Integer.class, "delay-ticks")
        .description("Ticks to wait between attacks.")
        .defaultValue(10)
        .range(4, 40)
        .build());

    private int cooldown = 0;

    public AutoFight() {
        super("AutoFight", "Automatically attacks nearby mobs. Never targets other players.", Category.COMBAT);
    }

    @Override
    public void onTick() {
        if (mc.player == null || mc.level == null || mc.gameMode == null) return;
        if (cooldown-- > 0) return;

        double rangeSq = range.get() * range.get();
        LivingEntity target = null;
        double closest = Double.MAX_VALUE;

        for (Entity entity : mc.level.entitiesForRendering()) {
            // Hard exclusion: this module must never target real players, regardless of settings.
            if (entity instanceof Player) continue;
            if (!(entity instanceof LivingEntity living) || !living.isAlive()) continue;

            boolean isHostile = living instanceof Monster;
            if (isHostile && !hostiles.get()) continue;
            if (!isHostile && !passives.get()) continue;

            double distSq = mc.player.distanceToSqr(living);
            if (distSq > rangeSq) continue;

            if (distSq < closest) {
                closest = distSq;
                target = living;
            }
        }

        if (target != null) {
            mc.gameMode.attack(mc.player, target);
            mc.player.swing(InteractionHand.MAIN_HAND);
            cooldown = delayTicks.get();
        }
    }
}
