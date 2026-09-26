package dev.novautils.modules.render;

import dev.novautils.modules.Category;
import dev.novautils.modules.Module;
import dev.novautils.settings.Setting;
import dev.novautils.utils.RenderUtils;
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderContext;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.core.BlockPos;
import net.minecraft.util.ARGB;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.List;

/**
 * Not a true chunk-transparency x-ray (that needs mixing into the chunk mesh builder, which we
 * haven't verified safe for this MC version yet). Instead this scans nearby loaded terrain for
 * ore blocks and highlights them through walls, same technique as {@link StorageEsp}. Practically
 * gets you the same result (spot ores through stone) without touching rendering internals.
 */
public class Xray extends Module {
    private static final int VERTICAL_HALF_RANGE = 12;

    private final Setting<Integer> radius = register(Setting.builder(Integer.class, "radius")
        .description("Horizontal scan radius in blocks. Higher = more lag when it rescans.")
        .defaultValue(16)
        .range(6, 32)
        .build());

    private final Setting<Double> opacity = register(Setting.builder(Double.class, "opacity")
        .description("Opacity of the ore highlight box.")
        .defaultValue(0.45)
        .range(0.05, 1.0)
        .build());

    private final List<BlockPos> found = new ArrayList<>();
    private int rescanTimer = 0;

    public Xray() {
        super("Xray", "Highlights ore blocks through terrain.", Category.RENDER);
    }

    @Override
    public void onEnable() {
        rescanTimer = 0;
    }

    @Override
    public void onDisable() {
        found.clear();
    }

    @Override
    public void onTick() {
        if (mc.level == null || mc.player == null) return;
        if (rescanTimer-- > 0) return;
        rescanTimer = 40;

        found.clear();

        BlockPos center = mc.player.blockPosition();
        int r = radius.get();

        for (int x = -r; x <= r; x++) {
            for (int z = -r; z <= r; z++) {
                for (int y = -VERTICAL_HALF_RANGE; y <= VERTICAL_HALF_RANGE; y++) {
                    BlockPos pos = center.offset(x, y, z);
                    if (!mc.level.hasChunkAt(pos)) continue;

                    BlockState state = mc.level.getBlockState(pos);
                    if (isOre(state)) {
                        found.add(pos.immutable());
                    }
                }
            }
        }
    }

    private boolean isOre(BlockState state) {
        if (state.isAir()) return false;

        var key = net.minecraft.core.registries.BuiltInRegistries.BLOCK.getKey(state.getBlock());
        if (key == null) return false;

        String path = key.getPath();
        return path.contains("_ore") || path.equals("ancient_debris");
    }

    @Override
    public void onRenderWorld(LevelRenderContext context) {
        if (found.isEmpty()) return;

        Vec3 cameraPos = context.levelState().cameraRenderState.pos;
        int color = ARGB.colorFromFloat((float) (double) opacity.get(), 1f, 0.9f, 0.2f);

        for (BlockPos pos : found) {
            AABB box = new AABB(pos);

            context.poseStack().pushPose();
            context.poseStack().translate(-cameraPos.x, -cameraPos.y, -cameraPos.z);

            context.submitNodeCollector().submitCustomGeometry(context.poseStack(), dev.novautils.utils.EspRender.filledNoDepth(),
                (pose, buffer) -> RenderUtils.drawBox(pose, buffer, box, color));

            context.poseStack().popPose();
        }
    }
}
