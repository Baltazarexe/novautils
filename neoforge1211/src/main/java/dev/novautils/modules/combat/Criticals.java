package dev.novautils.modules.combat;

import dev.novautils.modules.Category;
import dev.novautils.modules.Module;
import dev.novautils.settings.Setting;
import dev.novautils.utils.InvUtils;
import net.minecraft.network.protocol.game.ServerboundMovePlayerPacket;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;

/**
 * Every hit on a mob is a critical (x1.5 damage). Mobs only - hits on a {@code Player} are left
 * exactly as vanilla makes them (hard rule in code, not a setting), same as AutoFight.
 * <ul>
 *   <li>MINI_JUMP - holds the hit, does a tiny hop and lands it on the way down, which is a real
 *       vanilla crit made of normal movement. About 0.1 s later than the click; works on every
 *       server.</li>
 *   <li>PACKET - instant: tells the server you hopped up a hair and came back down, right before
 *       the hit. Only for servers older than 26.3: from 26.3 on, vanilla kicks for more than one
 *       position packet per tick ("Invalid move player packet").</li>
 * </ul>
 * Vanilla never crits while sprinting, so by default it drops the sprint for that one hit.
 */
public class Criticals extends Module {
    public enum Mode { MINI_JUMP, PACKET }

    private final Setting<Mode> mode = register(Setting.builder(Mode.class, "mode")
        .description("MINI_JUMP: works everywhere. PACKET: instant, but kicks on 26.3+ servers.")
        .defaultValue(Mode.MINI_JUMP).build());

    private final Setting<Boolean> stopSprint = register(Setting.builder(Boolean.class, "stop-sprint")
        .description("Crits don't happen while sprinting; stop sprinting for the hit.")
        .defaultValue(true).build());

    private final Setting<Boolean> onlyCharged = register(Setting.builder(Boolean.class, "only-charged")
        .description("Only on (nearly) fully charged hits - weak hits can't crit anyway.")
        .defaultValue(true).build());

    private Entity pending;
    private int pendingTicks;
    private boolean bypass;

    public Criticals() {
        super("Criticals", "Every hit on a mob is a critical hit. Never applies to players.", Category.COMBAT);
    }

    @Override
    public void onDisable() {
        pending = null;
    }

    private boolean canCrit(Entity target) {
        var p = mc.player;
        if (p == null || mc.getConnection() == null || mc.gameMode == null) return false;
        // Hard exclusion: never on real players, regardless of settings.
        if (target instanceof Player || !(target instanceof LivingEntity)) return false;
        if (!p.onGround() || p.isInWater() || p.isInLava() || p.onClimbable() || p.isPassenger()
            || p.hasEffect(MobEffects.BLINDNESS) || p.getAbilities().flying) return false;
        if (onlyCharged.get() && p.getAttackStrengthScale(0.5f) < 0.9f) return false;
        if (p.isSprinting() && !stopSprint.get()) return false;
        return true;
    }

    /**
     * GameModeMixin, right before an attack goes out. Returns true to hold the attack back (it is
     * sent later by {@link #onTick} once we're falling).
     */
    public boolean beforeAttack(Entity target) {
        if (bypass || pending != null || !canCrit(target)) return false;
        var p = mc.player;
        if (p.isSprinting()) p.setSprinting(false);

        if (mode.get() == Mode.PACKET) {
            double x = p.getX(), y = p.getY(), z = p.getZ();
            boolean hc = p.horizontalCollision;
            mc.getConnection().send(new ServerboundMovePlayerPacket.Pos(x, y + 0.0625, z, false));
            mc.getConnection().send(new ServerboundMovePlayerPacket.Pos(x, y, z, false));
            return false;
        }

        // MINI_JUMP: a small hop now, the hit once we're on the way down
        var v = p.getDeltaMovement();
        p.setDeltaMovement(v.x, 0.11, v.z);
        pending = target;
        pendingTicks = 0;
        return true;
    }

    @Override
    public void onTick() {
        if (pending == null || mc.player == null || mc.gameMode == null) return;
        var p = mc.player;
        pendingTicks++;
        boolean falling = !p.onGround() && p.getDeltaMovement().y < 0 && p.fallDistance > 0;
        if (!falling && pendingTicks < 8) return;

        Entity target = pending;
        pending = null;
        if (!target.isAlive() || p.distanceToSqr(target) > 36) return;
        if (p.isSprinting()) p.setSprinting(false);
        bypass = true;
        try {
            mc.gameMode.attack(p, target);
        } finally {
            bypass = false;
        }
        InvUtils.swing();
    }
}
