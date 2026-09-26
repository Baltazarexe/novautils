package dev.novautils.modules.render;

import dev.novautils.modules.Category;
import dev.novautils.modules.Module;
import dev.novautils.settings.Setting;
import dev.novautils.utils.RenderUtils;
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderContext;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.core.BlockPos;
import net.minecraft.util.ARGB;
import net.minecraft.world.level.block.entity.BaseContainerBlockEntity;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.List;

/** Periodically scans nearby loaded chunks for container block entities and highlights them through walls. */
public class StorageEsp extends Module {
    private final Setting<Integer> chunkRadius = register(Setting.builder(Integer.class, "chunk-radius")
        .description("How many chunks around you to scan for containers.")
        .defaultValue(6)
        .range(1, 16)
        .build());

    private final Setting<Double> opacity = register(Setting.builder(Double.class, "opacity")
        .description("Opacity of the highlight box.")
        .defaultValue(0.4)
        .range(0.05, 1.0)
        .build());

    private final List<BlockPos> found = new ArrayList<>();
    private int rescanTimer = 0;

    public StorageEsp() {
        super("StorageEsp", "Highlights chests, barrels, furnaces and other containers through walls.", Category.RENDER);
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
        rescanTimer = 20;

        found.clear();

        int radius = chunkRadius.get();
        int centerX = mc.player.chunkPosition().x();
        int centerZ = mc.player.chunkPosition().z();

        for (int cx = centerX - radius; cx <= centerX + radius; cx++) {
            for (int cz = centerZ - radius; cz <= centerZ + radius; cz++) {
                if (!mc.level.hasChunk(cx, cz)) continue;

                ChunkAccess chunk = mc.level.getChunk(cx, cz);
                for (var pos : chunk.getBlockEntitiesPos()) {
                    BlockEntity be = mc.level.getBlockEntity(pos);
                    if (be instanceof BaseContainerBlockEntity) {
                        found.add(pos);
                    }
                }
            }
        }
    }

    @Override
    public void onRenderWorld(LevelRenderContext context) {
        if (found.isEmpty()) return;

        Vec3 cameraPos = context.levelState().cameraRenderState.pos;
        int color = ARGB.colorFromFloat((float) (double) opacity.get(), 0.2f, 0.6f, 1f);

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
