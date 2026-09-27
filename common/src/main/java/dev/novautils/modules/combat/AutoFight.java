package dev.novautils.modules.combat;

import dev.novautils.modules.Category;
import dev.novautils.modules.Module;
import dev.novautils.settings.Setting;
import dev.novautils.utils.InvUtils;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.entity.player.Player;

/**
 * Automatically attacks nearby mobs so you don't have to click repeatedly while farming.
 * {@code Player} is hard-excluded from targeting below (not just a default setting) - this module
 * will never attack another real player, on purpose.
 *
 * <p>By default it waits for the same attack cooldown the game gives you, which depends on the
 * weapon in your hand, so every hit lands at full damage.
 */
public class AutoFight extends Module {
    public enum Timing {
        /** Wait for the held weapon's own cooldown to recharge. */
        WEAPON_COOLDOWN,
        /** Fixed number of ticks between hits. */
        FIXED_DELAY
    }

    private final Setting<Timing> timing = register(Setting.builder(Timing.class, "timing")
        .description("WEAPON_COOLDOWN hits at full charge for the item you hold.")
        .defaultValue(Timing.WEAPON_COOLDOWN).build());

    private final Setting<Double> charge = register(Setting.builder(Double.class, "min-charge")
        .description("How charged the attack must be, 1.0 = full damage.")
        .defaultValue(1.0).range(0.2, 1.0)
        .visible(() -> timing.get() == Timing.WEAPON_COOLDOWN)
        .build());

    private final Setting<Integer> delayTicks = register(Setting.builder(Integer.class, "delay-ticks")
        .description("Ticks to wait between attacks.")
        .defaultValue(10).range(1, 40)
        .visible(() -> timing.get() == Timing.FIXED_DELAY)
        .build());

    private final Setting<Double> range = register(Setting.builder(Double.class, "range")
        .description("Max distance to attack mobs at.")
        .defaultValue(4.0).range(2.0, 6.0).build());

    private final Setting<Boolean> hostiles = register(Setting.builder(Boolean.class, "hostile-mobs")
        .description("Attack hostile mobs (zombies, skeletons, ...).")
        .defaultValue(true).build());

    private final Setting<Boolean> passives = register(Setting.builder(Boolean.class, "passive-mobs")
        .description("Attack passive/neutral mobs and animals.")
        .defaultValue(false).build());

    private int cooldown = 0;

    public AutoFight() {
        super("AutoFight", "Automatically attacks nearby mobs. Never targets other players.", Category.COMBAT);
    }

    private boolean ready() {
        if (timing.get() == Timing.FIXED_DELAY) return cooldown-- <= 0;
        // Vanilla charge: 0 right after a swing, 1 once the weapon has fully recharged.
        return mc.player.getAttackStrengthScale(0f) >= charge.get().floatValue();
    }

    @Override
    public void onEnable() {
        cooldown = 0;
    }

    @Override
    public void onTick() {
        if (mc.player == null || mc.level == null || mc.gameMode == null) return;
        if (!ready()) return;

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
            InvUtils.swing();
            // attack() resets the vanilla ticker; this only matters in FIXED_DELAY mode.
            cooldown = delayTicks.get();
        }
    }
}
