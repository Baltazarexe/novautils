package dev.novautils.modules.render;

import dev.novautils.render.RenderContext;
import dev.novautils.modules.Category;
import dev.novautils.modules.Module;
import dev.novautils.settings.Setting;
import dev.novautils.utils.EspRender;
import dev.novautils.utils.RenderUtils;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.vehicle.ContainerEntity;
import net.minecraft.world.level.block.entity.BaseContainerBlockEntity;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.List;

/**
 * Highlights containers through walls: chests, barrels, furnaces and the like from the world, plus
 * container vehicles (chest/hopper/furnace minecarts and chest boats), which are entities and so
 * are tracked live instead of from the block scan.
 */
public class StorageEsp extends Module {
    private final Setting<Integer> chunkRadius = register(Setting.builder(Integer.class, "chunk-radius")
        .description("How many chunks around you to scan for containers.")
        .defaultValue(6).range(1, 16).build());

    private final Setting<Boolean> blocks = register(Setting.builder(Boolean.class, "blocks")
        .description("Highlight container blocks.").defaultValue(true).build());

    private final Setting<Integer> blockColor = register(Setting.builder(Integer.class, "block-color")
        .description("Colour for chests, barrels, furnaces and similar.")
        .defaultValue(0x663399FF).asColor().build());

    private final Setting<Boolean> vehicles = register(Setting.builder(Boolean.class, "minecarts-and-boats")
        .description("Highlight chest/hopper/furnace minecarts and chest boats.")
        .defaultValue(true).build());

    private final Setting<Integer> vehicleColor = register(Setting.builder(Integer.class, "vehicle-color")
        .description("Colour for container minecarts and boats.")
        .defaultValue(0x80FF8C42).asColor().build());

    private final List<BlockPos> found = new ArrayList<>();
    private int rescanTimer = 0;

    public StorageEsp() {
        super("StorageEsp", "Highlights chests, barrels, furnaces and chest minecarts through walls.", Category.RENDER);
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
        if (mc.level == null || mc.player == null || !blocks.get()) return;
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
                    if (be instanceof BaseContainerBlockEntity) found.add(pos);
                }
            }
        }
    }

    private void box(RenderContext context, Vec3 cam, AABB box, int color) {
        context.poseStack().pushPose();
        context.poseStack().translate(-cam.x, -cam.y, -cam.z);
        context.submitNodeCollector().submitCustomGeometry(context.poseStack(), EspRender.filledNoDepth(),
            (pose, buffer) -> RenderUtils.drawBox(pose, buffer, box, color));
        context.poseStack().popPose();
    }

    @Override
    public void onRenderWorld(RenderContext context) {
        if (mc.level == null) return;
        Vec3 cam = context.cameraPos();

        if (blocks.get()) {
            int color = blockColor.get();
            for (BlockPos pos : found) box(context, cam, new AABB(pos), color);
        }

        if (vehicles.get()) {
            int color = vehicleColor.get();
            float partial = mc.getDeltaTracker().getGameTimeDeltaPartialTick(false);
            for (Entity entity : mc.level.entitiesForRendering()) {
                if (!(entity instanceof ContainerEntity)) continue;
                AABB bounds = entity.getBoundingBox()
                    .move(entity.getPosition(partial).subtract(entity.position()))
                    .inflate(0.05);
                box(context, cam, bounds, color);
            }
        }
    }
}
