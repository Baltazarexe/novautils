package dev.novautils.modules.movement;

import dev.novautils.modules.Category;
import dev.novautils.modules.Module;
import dev.novautils.settings.Setting;
import dev.novautils.utils.InvUtils;
import dev.novautils.utils.MoveUtils;
import dev.novautils.utils.Rotations;
import net.minecraft.network.protocol.game.ServerboundPlayerCommandPacket;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.Items;
import net.minecraft.world.phys.Vec3;

/**
 * Elytra flight.
 * <ul>
 *   <li>CONTROL - WASD steers, jump/sneak go up/down, let go to hover. Easy, but servers that
 *       check elytra physics (2b2t does) will rubber-band it.</li>
 *   <li>BOOST - normal gliding physics, holding forward adds a push along your look direction up to
 *       a speed cap. Much closer to vanilla, so far less likely to be corrected.</li>
 *   <li>BOUNCE - the 2b2t highway method: glide along the ground, jumping and re-opening the elytra
 *       every time you touch down, with the server-side pitch held low. Fast with no fireworks.</li>
 * </ul>
 * Auto-takeoff opens the elytra for you as soon as you start falling with one equipped, and
 * auto-firework fires a rocket from the hotbar whenever you slow down while flying.
 */
public class ElytraFly extends Module {
    public enum Mode { CONTROL, BOOST, BOUNCE }

    private final Setting<Mode> mode = register(Setting.builder(Mode.class, "mode")
        .description("CONTROL: fly freely. BOOST: vanilla gliding plus a push. BOUNCE: glide-jump along the ground (2b2t).")
        .defaultValue(Mode.BOOST).build());

    private final Setting<Double> speed = register(Setting.builder(Double.class, "speed")
        .description("CONTROL: flying speed. BOOST: top speed. Blocks per tick.")
        .defaultValue(1.8).range(0.2, 5.0)
        .visible(() -> mode.get() != Mode.BOUNCE).build());

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

    private final Setting<Double> bouncePitch = register(Setting.builder(Double.class, "bounce-pitch")
        .description("Pitch the server sees while bouncing (higher = flatter along the ground).")
        .defaultValue(85.0).range(0.0, 90.0)
        .visible(() -> mode.get() == Mode.BOUNCE).build());

    private final Setting<Boolean> bounceAutoWalk = register(Setting.builder(Boolean.class, "bounce-auto-walk")
        .description("Keep going forward without holding W (BOUNCE).")
        .defaultValue(false).visible(() -> mode.get() == Mode.BOUNCE).build());

    private final Setting<Boolean> autoTakeoff = register(Setting.builder(Boolean.class, "auto-takeoff")
        .description("Open the elytra automatically once you're falling.")
        .defaultValue(true).build());

    private final Setting<Boolean> autoFirework = register(Setting.builder(Boolean.class, "auto-firework")
        .description("Fire a rocket from the hotbar when you slow down while flying.")
        .defaultValue(false).build());

    private final Setting<Double> fireworkSpeed = register(Setting.builder(Double.class, "firework-below")
        .description("Fire when slower than this (blocks per tick).")
        .defaultValue(0.8).range(0.1, 3.0).visible(() -> autoFirework.get()).build());

    private final Setting<Integer> fireworkDelay = register(Setting.builder(Integer.class, "firework-delay")
        .description("Minimum ticks between rockets.")
        .defaultValue(40).range(10, 200).visible(() -> autoFirework.get()).build());

    private int fireworkCooldown;

    public ElytraFly() {
        super("ElytraFly", "Elytra flight: control, boost, 2b2t bounce, auto rockets.", Category.MOVEMENT);
    }

    private boolean wearingElytra() {
        return mc.player.getItemBySlot(EquipmentSlot.CHEST).is(Items.ELYTRA);
    }

    private void startFlying() {
        var p = mc.player;
        if (p.tryToStartFallFlying()) {
            mc.getConnection().send(new ServerboundPlayerCommandPacket(p, ServerboundPlayerCommandPacket.Action.START_FALL_FLYING));
        }
    }

    @Override
    public void onDisable() {
        if (mc.options != null && bounceAutoWalk.get()) mc.options.keyUp.setDown(false);
    }

    @Override
    public void onTick() {
        var p = mc.player;
        if (p == null || mc.getConnection() == null) return;
        if (fireworkCooldown > 0) fireworkCooldown--;

        if (mode.get() == Mode.BOUNCE) {
            bounce();
        } else if (autoTakeoff.get() && !p.isFallFlying() && !p.onGround() && !p.isInWater()
            && p.getDeltaMovement().y < -0.1 && wearingElytra() && !p.getAbilities().flying) {
            startFlying();
        }

        if (mode.get() == Mode.BOOST && p.isFallFlying() && mc.options.keyUp.isDown()
            && p.getDeltaMovement().length() < speed.get()) {
            p.addDeltaMovement(p.getLookAngle().scale(accel.get()));
        }

        if (autoFirework.get() && p.isFallFlying() && fireworkCooldown == 0
            && p.getDeltaMovement().length() < fireworkSpeed.get()) {
            useFirework();
        }
    }

    private void bounce() {
        var p = mc.player;
        if (!wearingElytra() || p.isInWater() || p.getAbilities().flying) return;
        if (bounceAutoWalk.get()) mc.options.keyUp.setDown(true);
        if (!mc.options.keyUp.isDown()) return;
        p.setSprinting(true);
        if (p.onGround()) p.jumpFromGround();
        else if (!p.isFallFlying()) startFlying();
        // the server sees the low pitch; your camera is untouched
        if (!Rotations.busy()) Rotations.rotate(p.getYRot(), bouncePitch.get().floatValue(), null);
    }

    private void useFirework() {
        var inv = mc.player.getInventory();
        InteractionHand hand;
        int slot = -1, original = inv.selected;
        if (mc.player.getOffhandItem().is(Items.FIREWORK_ROCKET)) {
            hand = InteractionHand.OFF_HAND;
        } else {
            for (int i = 0; i < 9; i++) {
                if (inv.getItem(i).is(Items.FIREWORK_ROCKET)) {
                    slot = i;
                    break;
                }
            }
            if (slot < 0) return;
            hand = InteractionHand.MAIN_HAND;
        }
        if (slot >= 0 && slot != original) InvUtils.selectHotbar(slot);
        mc.gameMode.useItem(mc.player, hand);
        if (slot >= 0 && slot != original) InvUtils.selectHotbar(original);
        fireworkCooldown = fireworkDelay.get();
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
