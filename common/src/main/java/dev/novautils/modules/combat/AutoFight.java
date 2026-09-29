package dev.novautils.modules.combat;

import dev.novautils.modules.Category;
import dev.novautils.modules.Module;
import dev.novautils.settings.Setting;
import dev.novautils.utils.InvUtils;
import dev.novautils.utils.Rotations;
import net.minecraft.tags.ItemTags;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.NeutralMob;
import net.minecraft.world.entity.TamableAnimal;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;

/**
 * Fights mobs for you while you farm or AFK in a grinder.
 * {@code Player} is hard-excluded below (not a setting) - this never attacks a real player.
 *
 * <p>Waits for your weapon's own cooldown by default so every hit is a full-damage hit, looks at
 * the mob server-side before swinging (your camera stays put), won't hit through walls, and by
 * default leaves pets, named mobs, babies and neutral mobs (endermen, piglins, wolves) alone so it
 * doesn't start fights you didn't want.
 */
public class AutoFight extends Module {
    public enum Timing { WEAPON_COOLDOWN, FIXED_DELAY }
    public enum Priority { CLOSEST, LOWEST_HEALTH, CROSSHAIR }

    private final Setting<Timing> timing = register(Setting.builder(Timing.class, "timing")
        .description("WEAPON_COOLDOWN hits at full charge for the item you hold.")
        .defaultValue(Timing.WEAPON_COOLDOWN).build());

    private final Setting<Double> charge = register(Setting.builder(Double.class, "min-charge")
        .description("How charged the attack must be, 1.0 = full damage.")
        .defaultValue(1.0).range(0.2, 1.0)
        .visible(() -> timing.get() == Timing.WEAPON_COOLDOWN).build());

    private final Setting<Integer> delayTicks = register(Setting.builder(Integer.class, "delay-ticks")
        .description("Ticks to wait between attacks.")
        .defaultValue(10).range(1, 40)
        .visible(() -> timing.get() == Timing.FIXED_DELAY).build());

    private final Setting<Double> range = register(Setting.builder(Double.class, "range")
        .description("Max distance to attack mobs at (vanilla reach is 3).")
        .defaultValue(3.0).range(2.0, 6.0).build());

    private final Setting<Priority> priority = register(Setting.builder(Priority.class, "priority")
        .description("Which mob to hit first.")
        .defaultValue(Priority.CLOSEST).build());

    private final Setting<Boolean> rotate = register(Setting.builder(Boolean.class, "rotate")
        .description("Face the mob server-side before hitting (anti-cheats expect this).")
        .defaultValue(true).build());

    private final Setting<Boolean> throughWalls = register(Setting.builder(Boolean.class, "through-walls")
        .description("Hit mobs you can't see.")
        .defaultValue(false).build());

    private final Setting<Boolean> hostiles = register(Setting.builder(Boolean.class, "hostile-mobs")
        .description("Attack hostile mobs (zombies, skeletons, ...).")
        .defaultValue(true).build());

    private final Setting<Boolean> neutrals = register(Setting.builder(Boolean.class, "neutral-mobs")
        .description("Attack neutral mobs (endermen, piglins, wolves, bees...). Off = don't provoke them.")
        .defaultValue(false).build());

    private final Setting<Boolean> passives = register(Setting.builder(Boolean.class, "passive-mobs")
        .description("Attack animals (cows, pigs, chickens...).")
        .defaultValue(false).build());

    private final Setting<Boolean> ignoreNamed = register(Setting.builder(Boolean.class, "ignore-named")
        .description("Leave name-tagged mobs alone.")
        .defaultValue(true).build());

    private final Setting<Boolean> ignoreBabies = register(Setting.builder(Boolean.class, "ignore-babies")
        .description("Leave baby mobs alone.")
        .defaultValue(true).build());

    private final Setting<Boolean> onlyWeapon = register(Setting.builder(Boolean.class, "only-with-weapon")
        .description("Only attack while holding a sword or axe.")
        .defaultValue(false).build());

    private final Setting<Boolean> pauseOnUse = register(Setting.builder(Boolean.class, "pause-while-eating")
        .description("Don't attack while you're eating, drinking or blocking.")
        .defaultValue(true).build());

    private int cooldown;

    public AutoFight() {
        super("AutoFight", "Automatically attacks nearby mobs. Never targets other players.", Category.COMBAT);
    }

    @Override
    public void onEnable() {
        cooldown = 0;
    }

    private boolean ready() {
        if (timing.get() == Timing.FIXED_DELAY) return cooldown-- <= 0;
        return mc.player.getAttackStrengthScale(0.5f) >= charge.get().floatValue();
    }

    private boolean valid(Entity entity) {
        // Hard exclusion: never a real player, regardless of settings.
        if (entity instanceof Player) return false;
        if (!(entity instanceof LivingEntity living) || !living.isAlive() || living.isInvulnerable()) return false;
        if (entity == mc.player || entity.isPassengerOfSameVehicle(mc.player)) return false;

        if (ignoreNamed.get() && entity.hasCustomName()) return false;
        if (ignoreBabies.get() && living.isBaby()) return false;
        if (entity instanceof TamableAnimal t && t.isTame()) return false;

        if (entity instanceof NeutralMob) {
            if (!neutrals.get()) return false;
        } else if (entity instanceof Monster) {
            if (!hostiles.get()) return false;
        } else if (!passives.get()) {
            return false;
        }

        if (!inReach(living)) return false;
        return throughWalls.get() || mc.player.hasLineOfSight(entity);
    }

    private boolean inReach(LivingEntity e) {
        // distance to the closest point of the hitbox, like the server measures it
        Vec3 eyes = mc.player.getEyePosition();
        var box = e.getBoundingBox();
        double cx = Mth.clamp(eyes.x, box.minX, box.maxX);
        double cy = Mth.clamp(eyes.y, box.minY, box.maxY);
        double cz = Mth.clamp(eyes.z, box.minZ, box.maxZ);
        return eyes.distanceToSqr(cx, cy, cz) <= range.get() * range.get();
    }

    private double score(LivingEntity e) {
        return switch (priority.get()) {
            case CLOSEST -> mc.player.distanceToSqr(e);
            case LOWEST_HEALTH -> e.getHealth();
            case CROSSHAIR -> {
                float[] a = Rotations.anglesTo(e.getBoundingBox().getCenter());
                yield Math.abs(Mth.wrapDegrees(a[0] - mc.player.getYRot())) + Math.abs(a[1] - mc.player.getXRot());
            }
        };
    }

    @Override
    public void onTick() {
        if (mc.player == null || mc.level == null || mc.gameMode == null) return;
        if (pauseOnUse.get() && mc.player.isUsingItem()) return;
        if (onlyWeapon.get()) {
            var held = mc.player.getMainHandItem();
            if (!held.is(ItemTags.SWORDS) && !held.is(ItemTags.AXES)) return;
        }
        if (Rotations.busy() || !ready()) return;

        LivingEntity target = null;
        double best = Double.MAX_VALUE;
        for (Entity entity : mc.level.entitiesForRendering()) {
            if (!valid(entity)) continue;
            double s = score((LivingEntity) entity);
            if (s < best) {
                best = s;
                target = (LivingEntity) entity;
            }
        }
        if (target == null) return;

        LivingEntity t = target;
        Runnable hit = () -> {
            if (!t.isAlive() || !inReach(t)) return;
            mc.gameMode.attack(mc.player, t);
            InvUtils.swing();
        };
        cooldown = delayTicks.get();
        if (rotate.get()) {
            // aim at the closest point to the eyes within the hitbox's middle, not just the feet
            var box = t.getBoundingBox();
            Vec3 eyes = mc.player.getEyePosition();
            Vec3 aim = new Vec3(Mth.clamp(eyes.x, box.minX, box.maxX),
                Mth.clamp(eyes.y, box.minY + 0.1, box.maxY - 0.1), Mth.clamp(eyes.z, box.minZ, box.maxZ));
            Rotations.rotate(aim, hit);
        } else {
            hit.run();
        }
    }
}
