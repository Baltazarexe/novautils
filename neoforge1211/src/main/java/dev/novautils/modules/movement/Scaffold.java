package dev.novautils.modules.movement;

import dev.novautils.modules.Category;
import dev.novautils.modules.Module;
import dev.novautils.settings.Setting;
import dev.novautils.utils.BlockUtils;
import dev.novautils.utils.Rotations;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.phys.Vec3;

/**
 * Builds the floor under you as you walk. Looks one tick ahead so fast movement doesn't outrun it,
 * places a support block first when there's nothing directly next to the gap, faces the block it
 * places against (silently), skips sand/gravel/containers, and keeps you from walking off while
 * it catches up. Hold jump to tower straight up.
 */
public class Scaffold extends Module {
    private final Setting<Boolean> rotate = register(Setting.builder(Boolean.class, "rotate")
        .description("Face the block you place against (server side only, your view doesn't move).")
        .defaultValue(true).build());

    private final Setting<Boolean> tower = register(Setting.builder(Boolean.class, "fast-tower")
        .description("Holding jump pillars up faster than normal jumping.")
        .defaultValue(true).build());

    private final Setting<Boolean> safe = register(Setting.builder(Boolean.class, "safe-walk")
        .description("Don't walk off edges while scaffolding.")
        .defaultValue(true).build());

    private final Setting<Boolean> swapBack = register(Setting.builder(Boolean.class, "swap-back")
        .description("Switch back to the item you were holding after each block.")
        .defaultValue(true).build());

    private final Setting<Integer> extend = register(Setting.builder(Integer.class, "extend")
        .description("Also place this many blocks ahead in the direction you move.")
        .defaultValue(0).range(0, 3).build());

    public Scaffold() {
        super("Scaffold", "Places blocks under you as you walk.", Category.MOVEMENT);
    }

    public boolean safeWalk() {
        return safe.get();
    }

    private BlockPos under(double dx, double dz) {
        var p = mc.player;
        return BlockPos.containing(p.getX() + dx, p.getY() - 0.5, p.getZ() + dz);
    }

    /** Tries {@code pos}, and if nothing touches it, a neighbour of it that does (a support block). */
    private boolean placeWithSupport(BlockPos pos, int slot) {
        if (!BlockUtils.replaceable(pos)) return false;
        if (BlockUtils.placeSide(pos) != null) return BlockUtils.place(pos, slot, rotate.get(), swapBack.get());
        for (Direction d : new Direction[]{Direction.DOWN, Direction.NORTH, Direction.SOUTH, Direction.EAST, Direction.WEST}) {
            BlockPos support = pos.relative(d);
            if (BlockUtils.replaceable(support) && BlockUtils.placeSide(support) != null) {
                return BlockUtils.place(support, slot, rotate.get(), swapBack.get());
            }
        }
        return false;
    }

    @Override
    public void onTick() {
        var p = mc.player;
        if (p == null || mc.level == null || mc.gameMode == null || Rotations.busy()) return;
        int slot = BlockUtils.findHotbar(BlockUtils::isBuildingBlock);
        if (slot == -1) return;

        Vec3 v = p.getDeltaMovement();
        // one tick of look-ahead
        if (placeWithSupport(under(v.x, v.z), slot)) return;
        if (placeWithSupport(under(0, 0), slot)) return;

        for (int i = 1; i <= extend.get(); i++) {
            double h = Math.hypot(v.x, v.z);
            if (h < 0.01) break;
            if (placeWithSupport(under(v.x / h * i, v.z / h * i), slot)) return;
        }

        if (tower.get() && mc.options.keyJump.isDown() && !mc.options.keyUp.isDown()
            && !BlockUtils.replaceable(under(0, 0)) && p.getDeltaMovement().y < 0.1 && !p.onGround()) {
            p.setDeltaMovement(v.x * 0.3, 0.42, v.z * 0.3);
        }
    }
}
