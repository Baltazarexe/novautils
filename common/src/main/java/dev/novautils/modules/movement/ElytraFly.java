package dev.novautils.modules.movement;

import dev.novautils.modules.Category;
import dev.novautils.modules.Module;
import dev.novautils.settings.Setting;
import dev.novautils.utils.MoveUtils;
import net.minecraft.network.protocol.game.ServerboundPlayerCommandPacket;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.Items;
import net.minecraft.world.phys.Vec3;

/**
 * Elytra flight without fireworks.
 * <ul>
 *   <li>CONTROL - WASD steers, jump/sneak go up/down, let go to hover. Easy, but servers that
 *       check elytra physics (2b2t does) will rubber-band it.</li>
 *   <li>BOOST - normal gliding physics, holding forward adds a push along your look direction up to
 *       a speed cap. Much closer to vanilla, so far less likely to be corrected.</li>
 * </ul>
 * Auto-takeoff opens the elytra for you as soon as you start falling with one equipped.
 */
public class ElytraFly extends Module {
    public enum Mode { CONTROL, BOOST }

    private final Setting<Mode> mode = register(Setting.builder(Mode.class, "mode")
        .description("CONTROL: fly freely. BOOST: vanilla gliding plus a forward push.")
        .defaultValue(Mode.BOOST).build());

    private final Setting<Double> speed = register(Setting.builder(Double.class, "speed")
        .description("CONTROL: flying speed. BOOST: top speed. Blocks per tick.")
        .defaultValue(1.8).range(0.2, 5.0).build());

    private final Setting<Double> vertical = register(Setting.builder(Double.class, "vertical-speed")
        .description("Up/down speed with jump/sneak (CONTROL).")
        .defaultValue(0.6).range(0.1, 2.0)
        .visible(() -> mode.get() == Mode.CONTROL).build());

    private final Setting<Double> accel = register(Setting.builder(Double.class, "acceleration")
        .description("Push per tick while holding forward (BOOST).")
        .defaultValue(0.05).range(0.005, 0.3)
        .visible(() -> mode.get() == Mode.BOOST).build());

    private final Setting<Boolean> hover = register(Setting.builder(Boolean.class, "hover")
        .description("Stay in place when no key is held (CONTROL).")
        .defaultValue(true).visible(() -> mode.get() == Mode.CONTROL).build());

    private final Setting<Boolean> autoTakeoff = register(Setting.builder(Boolean.class, "auto-takeoff")
        .description("Open the elytra automatically once you're falling.")
        .defaultValue(true).build());

    public ElytraFly() {
        super("ElytraFly", "Fly with an elytra without fireworks.", Category.MOVEMENT);
    }

    private boolean wearingElytra() {
        return mc.player.getItemBySlot(EquipmentSlot.CHEST).is(Items.ELYTRA);
    }

    @Override
    public void onTick() {
        var p = mc.player;
        if (p == null || mc.getConnection() == null) return;

        if (autoTakeoff.get() && !p.isFallFlying() && !p.onGround() && !p.isInWater()
            && p.getDeltaMovement().y < -0.1 && wearingElytra() && !p.getAbilities().flying) {
            if (p.tryToStartFallFlying()) {
                mc.getConnection().send(new ServerboundPlayerCommandPacket(p, ServerboundPlayerCommandPacket.Action.START_FALL_FLYING));
            }
        }

        if (mode.get() == Mode.BOOST && p.isFallFlying() && mc.options.keyUp.isDown()
            && p.getDeltaMovement().length() < speed.get()) {
            p.addDeltaMovement(p.getLookAngle().scale(accel.get()));
        }
    }

    @Override
    public Vec3 onMove(Vec3 m) {
        var p = mc.player;
        if (p == null || !p.isFallFlying() || mode.get() != Mode.CONTROL) return m;

        Vec3 dir = MoveUtils.inputDirection();
        double y = 0;
        if (mc.options.keyJump.isDown()) y = vertical.get();
        else if (mc.options.keyShift.isDown()) y = -vertical.get();

        if (dir == Vec3.ZERO && y == 0 && !hover.get()) return m;
        Vec3 out = new Vec3(dir.x * speed.get(), y, dir.z * speed.get());
        // keep vanilla's velocity in step, otherwise glide physics pile up speed behind our back
        p.setDeltaMovement(out);
        return out;
    }
}
