package dev.novautils.modules.movement;

import dev.novautils.modules.Category;
import dev.novautils.modules.Module;
import dev.novautils.settings.Setting;
import dev.novautils.utils.MoveUtils;
import net.minecraft.world.phys.Vec3;

/** Climb walls: walk into one and you go up it like a ladder. */
public class Spider extends Module {
    private final Setting<Double> speed = register(Setting.builder(Double.class, "speed")
        .description("Climb speed in blocks per tick.")
        .defaultValue(0.2).range(0.05, 1.0).build());

    public Spider() {
        super("Spider", "Climb walls while pushing against them.", Category.MOVEMENT);
    }

    @Override
    public Vec3 onMove(Vec3 m) {
        var p = mc.player;
        if (p == null || !p.horizontalCollision || !MoveUtils.moving() || p.isShiftKeyDown()) return m;
        p.setDeltaMovement(p.getDeltaMovement().x, speed.get(), p.getDeltaMovement().z);
        p.resetFallDistance();
        return new Vec3(m.x, speed.get(), m.z);
    }
}
