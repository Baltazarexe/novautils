package dev.novautils.modules.movement;

import dev.novautils.modules.Category;
import dev.novautils.modules.Module;
import dev.novautils.settings.Setting;
import net.minecraft.world.phys.Vec3;

/**
 * Stops you falling out of the world. Once you drop below the bottom of the world it pushes you
 * back up like a jump, again and again, until you're above it - no teleporting, which servers
 * would just reject.
 */
public class AntiVoid extends Module {
    private final Setting<Integer> margin = register(Setting.builder(Integer.class, "margin")
        .description("Blocks below the world's bottom before it kicks in.")
        .defaultValue(1).range(0, 20).build());

    public AntiVoid() {
        super("AntiVoid", "Bounces you back up if you fall into the void.", Category.MOVEMENT);
    }

    @Override
    public Vec3 onMove(Vec3 m) {
        var p = mc.player;
        if (p == null || mc.level == null || p.getAbilities().flying) return m;
        if (p.getY() > mc.level.getMinBuildHeight() - margin.get() || m.y > 0) return m;
        p.setDeltaMovement(p.getDeltaMovement().x, 0.42, p.getDeltaMovement().z);
        p.resetFallDistance();
        return new Vec3(m.x, 0.42, m.z);
    }
}
