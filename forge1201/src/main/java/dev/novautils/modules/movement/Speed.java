package dev.novautils.modules.movement;

import dev.novautils.modules.Category;
import dev.novautils.modules.Module;
import dev.novautils.settings.Setting;
import dev.novautils.utils.MoveUtils;
import net.minecraft.world.phys.Vec3;

/**
 * Faster movement, applied inside the physics step so it's smooth and nothing fights it.
 * STRAFE gives full air control at a steady speed (optionally bunny-hopping for you); VANILLA only
 * speeds up ground movement and leaves jumps alone.
 */
public class Speed extends Module {
    public enum Mode { STRAFE, VANILLA }

    private final Setting<Mode> mode = register(Setting.builder(Mode.class, "mode")
        .description("STRAFE: steady speed on ground and in the air. VANILLA: ground only.")
        .defaultValue(Mode.STRAFE).build());

    private final Setting<Double> speed = register(Setting.builder(Double.class, "speed")
        .description("Blocks per tick. Sprinting is about 0.28.")
        .defaultValue(0.36).range(0.1, 1.5).build());

    private final Setting<Boolean> autoJump = register(Setting.builder(Boolean.class, "auto-jump")
        .description("Bunny-hop automatically while moving (STRAFE).")
        .defaultValue(false).visible(() -> mode.get() == Mode.STRAFE).build());

    private final Setting<Boolean> inLiquids = register(Setting.builder(Boolean.class, "in-liquids")
        .description("Also apply while swimming.")
        .defaultValue(false).build());

    private final Setting<Boolean> whileSneaking = register(Setting.builder(Boolean.class, "while-sneaking")
        .description("Also apply while sneaking.")
        .defaultValue(false).build());

    public Speed() {
        super("Speed", "Move faster, with full air control.", Category.MOVEMENT);
    }

    @Override
    public Vec3 onMove(Vec3 m) {
        var p = mc.player;
        if (p == null || !MoveUtils.moving()) return m;
        if (p.getAbilities().flying || p.isFallFlying() || p.isPassenger() || p.onClimbable()) return m;
        if (!inLiquids.get() && (p.isInWater() || p.isInLava())) return m;
        if (!whileSneaking.get() && p.isShiftKeyDown()) return m;

        Vec3 dir = MoveUtils.inputDirection();
        double s = speed.get();

        if (mode.get() == Mode.VANILLA) {
            if (!p.onGround()) return m;
            return new Vec3(dir.x * s, m.y, dir.z * s);
        }

        double y = m.y;
        if (autoJump.get() && p.onGround()) {
            p.jumpFromGround();
            y = p.getDeltaMovement().y;
        }
        return new Vec3(dir.x * s, y, dir.z * s);
    }
}
