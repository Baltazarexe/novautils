package dev.novautils.modules.world;

import dev.novautils.gui.Toasts;
import dev.novautils.modules.Category;
import dev.novautils.modules.Module;
import dev.novautils.render.RenderContext;
import dev.novautils.settings.Setting;
import dev.novautils.utils.BlockUtils;
import dev.novautils.utils.RenderUtils;
import dev.novautils.utils.Rotations;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * Builds a simple shape out of the blocks in your hotbar, placed one by one like a player would
 * (against a neighbour, looking at it). The shape is fixed where you stand when you turn it on;
 * it switches itself off when done or when you run out of blocks.
 * <ul>
 *   <li>PLATFORM - a square floor at your feet level - 1, centred on you.</li>
 *   <li>WALL - a wall across your view, two blocks in front of you.</li>
 *   <li>BRIDGE - a 1-wide path straight ahead at your feet level - 1.</li>
 *   <li>BOX - walls and a roof around you (a quick shelter).</li>
 * </ul>
 */
public class AutoBuild extends Module {
    public enum Shape { PLATFORM, WALL, BRIDGE, BOX }

    private final Setting<Shape> shape = register(Setting.builder(Shape.class, "shape")
        .description("What to build.").defaultValue(Shape.PLATFORM).build());

    private final Setting<Integer> size = register(Setting.builder(Integer.class, "size")
        .description("Width (platform, wall, box) or length (bridge).").defaultValue(5).range(1, 21).build());

    private final Setting<Integer> height = register(Setting.builder(Integer.class, "height")
        .description("Wall and box height.").defaultValue(3).range(1, 10)
        .visible(() -> shape.get() == Shape.WALL || shape.get() == Shape.BOX).build());

    private final Setting<Integer> perTick = register(Setting.builder(Integer.class, "blocks-per-tick")
        .description("Blocks placed per tick (1 is safest on strict servers).").defaultValue(1).range(1, 6).build());

    private final List<BlockPos> plan = new ArrayList<>();

    public AutoBuild() {
        super("AutoBuild", "Builds a platform, wall, bridge or shelter from your hotbar blocks.", Category.WORLD);
    }

    @Override
    public void onEnable() {
        plan.clear();
        if (mc.player == null) {
            setEnabled(false);
            return;
        }
        BlockPos feet = mc.player.blockPosition();
        Direction facing = mc.player.getDirection();
        Direction side = facing.getClockWise();
        int n = size.get(), half = n / 2;
        switch (shape.get()) {
            case PLATFORM -> {
                for (int dx = -half; dx < n - half; dx++) {
                    for (int dz = -half; dz < n - half; dz++) plan.add(feet.offset(dx, -1, dz));
                }
            }
            case WALL -> {
                BlockPos base = feet.relative(facing, 2);
                for (int y = 0; y < height.get(); y++) {
                    for (int s = -half; s < n - half; s++) plan.add(base.relative(side, s).above(y));
                }
            }
            case BRIDGE -> {
                for (int k = 1; k <= n; k++) plan.add(feet.below().relative(facing, k));
            }
            case BOX -> {
                int r = Math.max(1, half);
                for (int y = 0; y <= height.get(); y++) {
                    for (int dx = -r; dx <= r; dx++) {
                        for (int dz = -r; dz <= r; dz++) {
                            boolean wall = Math.abs(dx) == r || Math.abs(dz) == r;
                            boolean roof = y == height.get();
                            if (wall || roof) plan.add(feet.offset(dx, y, dz));
                        }
                    }
                }
            }
        }
        // bottom-up, nearest first, so every block has something to stand on when its turn comes
        Vec3 eyes = mc.player.getEyePosition();
        plan.sort(Comparator.<BlockPos>comparingInt(BlockPos::getY)
            .thenComparingDouble(p -> eyes.distanceToSqr(Vec3.atCenterOf(p))));
    }

    @Override
    public void onTick() {
        if (mc.player == null || mc.level == null || Rotations.busy()) return;
        plan.removeIf(p -> !mc.level.getBlockState(p).canBeReplaced());
        if (plan.isEmpty()) {
            Toasts.push("AutoBuild: done", 0xFF66DD77);
            setEnabled(false);
            return;
        }
        int slot = BlockUtils.findHotbar(BlockUtils::isBuildingBlock);
        if (slot < 0) {
            Toasts.push("AutoBuild: no blocks in your hotbar", 0xFFFFAA44);
            setEnabled(false);
            return;
        }
        Vec3 eyes = mc.player.getEyePosition();
        int placed = 0;
        for (BlockPos p : plan) {
            if (placed >= perTick.get()) break;
            if (eyes.distanceToSqr(Vec3.atCenterOf(p)) > 4.5 * 4.5) continue;
            if (!BlockUtils.freeOfEntities(p) || BlockUtils.placeSide(p) == null) continue;
            // with rotation each placement waits for its own movement packet, so one per tick
            boolean rotate = perTick.get() == 1;
            if (BlockUtils.place(p, slot, rotate, true)) placed++;
            if (rotate) break;
        }
    }

    @Override
    public void onRenderWorld(RenderContext context) {
        int shown = 0;
        for (BlockPos p : plan) {
            if (shown++ > 300) break;
            AABB box = new AABB(p);
            RenderUtils.world(context, (pose, buffer) -> RenderUtils.drawBox(pose, buffer, box, 0x30C49BFF));
        }
    }
}
