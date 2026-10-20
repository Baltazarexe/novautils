package dev.novautils.modules.combat;

import dev.novautils.modules.Category;
import dev.novautils.modules.Module;
import dev.novautils.settings.Setting;
import dev.novautils.utils.InvUtils;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ServerboundClientTickEndPacket;
import net.minecraft.network.protocol.game.ServerboundMovePlayerPacket;
import net.minecraft.network.protocol.game.ServerboundPlayerCommandPacket;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;

/**
 * Every hit on a mob is a critical (x1.5 damage). Mobs only - hits on a {@code Player} are left
 * exactly as vanilla makes them (hard rule in code, not a setting), same as AutoFight.
 * <ul>
 *   <li>MINI_JUMP - holds the hit, does a tiny hop and lands it on the way down, which is a real
 *       vanilla crit made of normal movement. About 0.1 s later than the click.</li>
 *   <li>PACKET - no visible hop. The server now runs your movement once per client tick (when
 *       the "tick end" packet arrives) and kicks for a second position packet in the same tick,
 *       so this works in step with it: in one tick your position packet says you rose a hair, in
 *       the next that you're falling, and the hit goes out at the start of the tick after, when
 *       the server has counted the fall. One position packet per tick, always.</li>
 * </ul>
 * Vanilla never crits while sprinting, so by default it drops the sprint for that one hit.
 */
public class Criticals extends Module {
    public enum Mode { MINI_JUMP, PACKET }

    private final Setting<Mode> mode = register(Setting.builder(Mode.class, "mode")
        .description("MINI_JUMP: a tiny real hop. PACKET: no hop, only the server sees it.")
        .defaultValue(Mode.MINI_JUMP).build());

    private final Setting<Boolean> stopSprint = register(Setting.builder(Boolean.class, "stop-sprint")
        .description("Crits don't happen while sprinting; stop sprinting for the hit.")
        .defaultValue(true).build());

    private final Setting<Boolean> onlyCharged = register(Setting.builder(Boolean.class, "only-charged")
        .description("Only on (nearly) fully charged hits - weak hits can't crit anyway.")
        .defaultValue(true).build());

    private static final int IDLE = 0, UP = 1, DOWN = 2, HIT = 3, RESYNC = 4;

    private Entity pending;
    private int pendingTicks;
    private boolean bypass;

    /** PACKET state: which tick of the crit we're in. */
    private int stage = IDLE;
    private boolean movedThisTick;
    private boolean sendingOwn;
    /**
     * A position packet already went out THIS client tick (seen even while idle). The server
     * kicks ("Invalid move player packet received") for a second one before the tick-end packet,
     * so a crit that starts after the vanilla packet of the tick must not add its own.
     */
    private boolean sawPosThisTick;
    /** The crit started after this tick's position packet: wait out the tick before UP. */
    private boolean defer;
    private double baseY;
    private int stageAge;

    public Criticals() {
        super("Criticals", "Every hit on a mob is a critical hit. Never applies to players.", Category.COMBAT);
    }

    @Override
    public void onDisable() {
        pending = null;
        stage = IDLE;
        defer = false;
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
     * sent later, once the server sees us falling).
     */
    public boolean beforeAttack(Entity target) {
        if (bypass || pending != null || stage != IDLE || !canCrit(target)) return false;
        var p = mc.player;
        dropSprint(p);
        pending = target;
        pendingTicks = 0;

        if (mode.get() == Mode.PACKET) {
            stage = UP;
            stageAge = 0;
            movedThisTick = sawPosThisTick;
            defer = sawPosThisTick;
            baseY = p.getY();
            return true;
        }

        // MINI_JUMP: a small hop now, the hit once we're on the way down
        var v = p.getDeltaMovement();
        p.setDeltaMovement(v.x, 0.11, v.z);
        return true;
    }

    /**
     * Sprinting blocks crits, and what counts is what the SERVER believes. setSprinting(false)
     * only changes the client; the "stopped sprinting" packet normally goes out next tick, after
     * the attack, and with the sprint key held the game turns sprint back on anyway. Tested live
     * (26.3): while running, Criticals did nothing in both modes. So tell the server right now.
     */
    private void dropSprint(net.minecraft.client.player.LocalPlayer p) {
        if (!p.isSprinting() && !mc.options.keySprint.isDown()) return;
        p.setSprinting(false);
        mc.getConnection().send(new ServerboundPlayerCommandPacket(p, ServerboundPlayerCommandPacket.Action.STOP_SPRINTING));
    }

    private void hit() {
        var p = mc.player;
        Entity target = pending;
        pending = null;
        if (p == null || target == null || !target.isAlive() || p.distanceToSqr(target) > 36) return;
        dropSprint(p);
        bypass = true;
        try {
            mc.gameMode.attack(p, target);
        } finally {
            bypass = false;
        }
        InvUtils.swing();
    }

    private void sendOwn(Packet<?> packet) {
        sendingOwn = true;
        try {
            mc.getConnection().send(packet);
        } finally {
            sendingOwn = false;
        }
    }

    /** Our position for this tick: up a hair (UP), back where we were but in the air (DOWN). */
    private ServerboundMovePlayerPacket spoof(double x, double z, float yaw, float pitch) {
        double y = stage == UP ? baseY + 0.0625 : baseY;
        return new ServerboundMovePlayerPacket.PosRot(x, y, z, yaw, pitch, false, mc.player.horizontalCollision);
    }

    @Override
    public boolean onSendPacket(Packet<?> packet) {
        if (!sendingOwn) {
            if (packet instanceof ServerboundMovePlayerPacket m && m.hasPosition()) sawPosThisTick = true;
            else if (packet instanceof ServerboundClientTickEndPacket && stage == IDLE) sawPosThisTick = false;
        }
        if (stage == IDLE || sendingOwn || mc.player == null || mc.getConnection() == null) return false;
        var p = mc.player;

        if (packet instanceof ServerboundMovePlayerPacket move) {
            switch (stage) {
                case UP, DOWN -> {
                    // never two position packets in one tick: drop a duplicate outright
                    if (movedThisTick) return move.hasPosition();
                    // this tick's position packet becomes ours
                    sendOwn(spoof(move.getX(p.getX()), move.getZ(p.getZ()), move.getYRot(p.getYRot()), move.getXRot(p.getXRot())));
                    movedThisTick = true;
                    return true;
                }
                case HIT -> {
                    // first thing in a new tick: the server has counted the fall, land the hit
                    // before this (real, on the ground) position goes out
                    hit();
                    stage = RESYNC;
                    movedThisTick = true;
                    return false;
                }
                default -> {
                    movedThisTick = true;
                    return false;
                }
            }
        }

        if (packet instanceof ServerboundClientTickEndPacket) {
            switch (stage) {
                case UP, DOWN -> {
                    if (defer) {
                        // the vanilla position packet of this tick is already out: start next tick
                        defer = false;
                    } else {
                        if (!movedThisTick) sendOwn(spoof(p.getX(), p.getZ(), p.getYRot(), p.getXRot()));
                        stage = stage == UP ? DOWN : HIT;
                    }
                }
                case HIT -> {
                    // a tick without movement: the hit goes out now, the fall was counted last tick
                    hit();
                    stage = RESYNC;
                }
                case RESYNC -> {
                    if (!movedThisTick) sendOwn(new ServerboundMovePlayerPacket.StatusOnly(p.onGround(), p.horizontalCollision));
                    stage = IDLE;
                }
                default -> { }
            }
            movedThisTick = false;
            sawPosThisTick = false;
            stageAge = 0;
        }
        return false;
    }

    @Override
    public void onTick() {
        if (stage != IDLE) {
            // safety: never get stuck if tick-end packets stop (disconnect, loading screen)
            if (++stageAge > 10) {
                stage = IDLE;
                pending = null;
                defer = false;
            }
            return;
        }
        if (pending == null || mc.player == null || mc.gameMode == null) return;
        var p = mc.player;
        pendingTicks++;
        boolean falling = !p.onGround() && p.getDeltaMovement().y < 0 && p.fallDistance > 0;
        if (!falling && pendingTicks < 8) return;
        hit();
    }
}
