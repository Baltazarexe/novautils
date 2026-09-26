package dev.novautils.modules.movement;

import dev.novautils.modules.Category;
import dev.novautils.modules.Module;
import dev.novautils.settings.Setting;

/** While gliding with an elytra, holding forward pushes you along where you look. */
public class ElytraBoost extends Module {
    private final Setting<Double> strength = register(Setting.builder(Double.class, "strength")
        .description("Acceleration per tick.")
        .defaultValue(0.04).range(0.005, 0.2).build());

    private final Setting<Double> maxSpeed = register(Setting.builder(Double.class, "max-speed")
        .description("Speed cap in blocks per tick.")
        .defaultValue(2.0).range(0.5, 5.0).build());

    public ElytraBoost() {
        super("ElytraBoost", "Accelerate while elytra flying.", Category.MOVEMENT);
    }

    @Override
    public void onTick() {
        if (mc.player == null || !mc.player.isFallFlying() || !mc.options.keyUp.isDown()) return;
        if (mc.player.getDeltaMovement().length() >= maxSpeed.get()) return;
        mc.player.addDeltaMovement(mc.player.getLookAngle().scale(strength.get()));
    }
}
