package dev.novautils.modules.movement;

import dev.novautils.modules.Category;
import dev.novautils.modules.Module;
import dev.novautils.settings.Setting;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.level.material.Fluids;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.EntityCollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * Walk on water (and optionally lava). SOLID gives the top layer of liquid a floor, for you only
 * (LiquidBlockMixin asks {@link #floorFor}), so you walk on it like a block; FLOAT keeps you
 * bobbing at the surface instead. Hold sneak to go under. Falling from high up you go into the
 * water instead of landing on it, so the water still saves you from fall damage.
 */
public class Jesus extends Module {
    public enum Mode { SOLID, FLOAT }

    private final Setting<Mode> mode = register(Setting.builder(Mode.class, "mode")
        .description("SOLID: walk on the surface. FLOAT: swim up and stay at the top.")
        .defaultValue(Mode.SOLID).build());

    private final Setting<Boolean> lava = register(Setting.builder(Boolean.class, "lava")
        .description("Also walk on lava.")
        .defaultValue(true).build());

    public Jesus() {
        super("Jesus", "Walk on water and lava.", Category.MOVEMENT);
    }

    /** Collision shape to give a liquid block, or null for vanilla's (none). */
    public VoxelShape floorFor(BlockState state, BlockGetter level, BlockPos pos, CollisionContext ctx) {
        if (mode.get() != Mode.SOLID || mc.player == null) return null;
        if (!(ctx instanceof EntityCollisionContext ec) || ec.getEntity() != mc.player) return null;

        var p = mc.player;
        if (p.isShiftKeyDown() || p.isFallFlying() || p.fallDistance > 3) return null;
        if (p.isInWater() || p.isInLava()) return null;

        FluidState fluid = state.getFluidState();
        if (fluid.is(Fluids.LAVA) || fluid.is(Fluids.FLOWING_LAVA)) {
            if (!lava.get()) return null;
        }
        // only the surface layer, never a floor in the middle of a waterfall or deep water
        if (!level.getFluidState(pos.above()).isEmpty()) return null;

        VoxelShape floor = Shapes.block();
        return ctx.isAbove(floor, pos, true) ? floor : null;
    }

    @Override
    public Vec3 onMove(Vec3 m) {
        var p = mc.player;
        if (p == null || p.isShiftKeyDown()) return m;
        boolean inLiquid = p.isInWater() || (lava.get() && p.isInLava());
        if (!inLiquid) return m;

        // under water with nothing above the head: rise to the surface (both modes)
        boolean deep = !mc.level.getFluidState(BlockPos.containing(p.getX(), p.getY() + 0.9, p.getZ())).isEmpty();
        double y = deep ? 0.11 : (mode.get() == Mode.SOLID ? 0.1 : 0.0);
        p.setDeltaMovement(p.getDeltaMovement().x, y, p.getDeltaMovement().z);
        return new Vec3(m.x, y, m.z);
    }
}
