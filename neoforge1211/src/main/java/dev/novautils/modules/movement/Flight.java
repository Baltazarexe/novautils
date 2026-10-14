package dev.novautils.modules.movement;

import dev.novautils.modules.Category;
import dev.novautils.modules.Module;
import dev.novautils.settings.Setting;
import dev.novautils.utils.MoveUtils;
import net.minecraft.world.phys.Vec3;

/**
 * Flight. VELOCITY moves you directly (WASD + jump/sneak, you stop dead when you let go);
 * ABILITIES turns on creative-style flying. Anti-kick dips you down a hair every couple of seconds
 * so vanilla's "flying is not enabled" check never sees you hovering long enough to kick.
 */
public class Flight extends Module {
    public enum Mode { VELOCITY, ABILITIES }

    private final Setting<Mode> mode = register(Setting.builder(Mode.class, "mode")
        .description("VELOCITY: direct control, stops instantly. ABILITIES: creative flying.")
        .defaultValue(Mode.VELOCITY).build());

    private final Setting<Double> speed = register(Setting.builder(Double.class, "speed")
        .description("Horizontal speed in blocks per tick (1.0 = 20 blocks/s).")
        .defaultValue(1.0).range(0.1, 5.0).build());

    private final Setting<Double> vertical = register(Setting.builder(Double.class, "vertical-speed")
        .description("Up/down speed in blocks per tick (VELOCITY).")
        .defaultValue(0.6).range(0.1, 3.0)
        .visible(() -> mode.get() == Mode.VELOCITY).build());

    private final Setting<Boolean> antiKick = register(Setting.builder(Boolean.class, "anti-kick")
        .description("Drop 0.04 blocks every 40 ticks so vanilla servers don't kick you for flying.")
        .defaultValue(true).build());

    private int ticks;

    public Flight() {
        super("Flight", "Fly in survival.", Category.MOVEMENT);
    }

    @Override
    public void onEnable() {
        ticks = 0;
    }

    @Override
    public void onDisable() {
        if (mc.player == null) return;
        var abilities = mc.player.getAbilities();
        if (!abilities.instabuild) {
            abilities.flying = false;
            abilities.mayfly = false;
        }
        abilities.setFlyingSpeed(0.05f);
        mc.player.onUpdateAbilities();
        if (mode.get() == Mode.VELOCITY) mc.player.setDeltaMovement(Vec3.ZERO);
    }

    @Override
    public void onTick() {
        if (mc.player == null) return;
        ticks++;
        if (mode.get() == Mode.ABILITIES) {
            var abilities = mc.player.getAbilities();
            abilities.mayfly = true;
            abilities.flying = true;
            abilities.setFlyingSpeed((float) (speed.get() / 20.0));
        }
    }

    @Override
    public Vec3 onMove(Vec3 m) {
        var p = mc.player;
        if (p == null || p.isPassenger()) return m;

        if (mode.get() == Mode.VELOCITY) {
            Vec3 dir = MoveUtils.inputDirection();
            double y = 0;
            if (mc.options.keyJump.isDown()) y += vertical.get();
            if (mc.options.keyShift.isDown()) y -= vertical.get();
            // no leftover momentum and no gravity build-up while flying
            p.setDeltaMovement(Vec3.ZERO);
            m = new Vec3(dir.x * speed.get(), y, dir.z * speed.get());
        }

        if (antiKick.get() && ticks % 40 == 0 && m.y >= -0.04) {
            m = new Vec3(m.x, -0.04, m.z);
        }
        return m;
    }
}
