package dev.novautils.modules.render;

import dev.novautils.modules.Category;
import dev.novautils.modules.Module;
import dev.novautils.render.RenderContext;
import dev.novautils.settings.Setting;
import dev.novautils.utils.EspRender;
import dev.novautils.utils.RenderUtils;
import net.minecraft.core.BlockPos;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientboundBlockUpdatePacket;
import net.minecraft.network.protocol.game.ClientboundSectionBlocksUpdatePacket;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.chunk.LevelChunkSection;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.util.HashSet;
import java.util.Set;

/**
 * Tells freshly generated chunks from ones someone already loaded - how people follow trails to
 * bases on 2b2t. When a chunk is generated, its water and lava start flowing right away, so the
 * server streams "liquid changed" updates for it: those chunks are NEW (red). A chunk that arrives
 * already containing flowing liquid has been loaded before, long enough for it to flow: OLD
 * (green). A line of old chunks through new terrain is somebody's path.
 */
public class NewChunks extends Module {
    private final Setting<Integer> newColor = register(Setting.builder(Integer.class, "new-color")
        .description("Colour of never-loaded chunks.").defaultValue(0x40FF3030).asColor().build());

    private final Setting<Boolean> showOld = register(Setting.builder(Boolean.class, "show-old")
        .description("Also mark chunks someone loaded before (the trail).").defaultValue(true).build());

    private final Setting<Integer> oldColor = register(Setting.builder(Integer.class, "old-color")
        .description("Colour of previously loaded chunks.").defaultValue(0x3030FF60).asColor()
        .visible(() -> showOld.get()).build());

    private final Setting<Integer> yOffset = register(Setting.builder(Integer.class, "height")
        .description("Draw the markers this many blocks below your feet.").defaultValue(2).range(-64, 64).build());

    private final Set<Long> newChunks = new HashSet<>();
    private final Set<Long> oldChunks = new HashSet<>();
    private final Set<Long> seen = new HashSet<>();
    private Object lastLevel;

    public NewChunks() {
        super("NewChunks", "Shows new vs already-loaded chunks (find trails).", Category.RENDER);
    }

    @Override
    public void onDisable() {
        newChunks.clear();
        oldChunks.clear();
        seen.clear();
    }

    private static boolean flowing(BlockState s) {
        var f = s.getFluidState();
        return !f.isEmpty() && !f.isSource();
    }

    private void liquidUpdate(BlockPos pos, BlockState state) {
        if (!flowing(state)) return;
        long key = ChunkPos.pack(pos);
        if (!oldChunks.contains(key)) newChunks.add(key);
    }

    @Override
    public boolean onReceivePacket(Packet<?> packet) {
        if (packet instanceof ClientboundBlockUpdatePacket p) {
            liquidUpdate(p.getPos(), p.getBlockState());
        } else if (packet instanceof ClientboundSectionBlocksUpdatePacket p) {
            p.runUpdates(this::liquidUpdate);
        }
        return false;
    }

    /** A chunk we see for the first time: flowing liquid already in it means it's been loaded before. */
    private void firstSight(LevelChunk chunk, long key) {
        if (newChunks.contains(key)) return;
        for (LevelChunkSection s : chunk.getSections()) {
            if (s == null || s.hasOnlyAir()) continue;
            if (s.maybeHas(NewChunks::flowing)) {
                for (int y = 0; y < 16; y++) for (int z = 0; z < 16; z++) for (int x = 0; x < 16; x++) {
                    if (flowing(s.getBlockState(x, y, z))) {
                        oldChunks.add(key);
                        return;
                    }
                }
            }
        }
    }

    @Override
    public void onTick() {
        if (mc.level == null || mc.player == null) return;
        if (mc.level != lastLevel) {
            lastLevel = mc.level;
            onDisable();
        }
        int r = mc.options.getEffectiveRenderDistance();
        ChunkPos c = mc.player.chunkPosition();
        int budget = 8;
        for (int dx = -r; dx <= r && budget > 0; dx++) {
            for (int dz = -r; dz <= r && budget > 0; dz++) {
                int cx = c.x() + dx, cz = c.z() + dz;
                long key = ChunkPos.pack(cx, cz);
                if (seen.contains(key) || !mc.level.hasChunk(cx, cz)) continue;
                seen.add(key);
                firstSight(mc.level.getChunk(cx, cz), key);
                budget--;
            }
        }
    }

    private void square(RenderContext ctx, Vec3 cam, long key, double y, int color) {
        int x = ChunkPos.getX(key) * 16, z = ChunkPos.getZ(key) * 16;
        AABB box = new AABB(x + 0.1, y, z + 0.1, x + 15.9, y + 0.05, z + 15.9);
        ctx.poseStack().pushPose();
        ctx.poseStack().translate(-cam.x, -cam.y, -cam.z);
        ctx.submitNodeCollector().submitCustomGeometry(ctx.poseStack(), EspRender.filledNoDepth(),
            (pose, buffer) -> RenderUtils.drawBox(pose, buffer, box, color));
        ctx.poseStack().popPose();
    }

    @Override
    public void onRenderWorld(RenderContext context) {
        if (mc.player == null) return;
        Vec3 cam = context.cameraPos();
        double y = Math.floor(mc.player.getY()) - yOffset.get();
        int max = (mc.options.getEffectiveRenderDistance() + 1) * 16;
        double px = mc.player.getX(), pz = mc.player.getZ();
        for (long key : newChunks) {
            if (Math.abs(ChunkPos.getX(key) * 16 + 8 - px) > max || Math.abs(ChunkPos.getZ(key) * 16 + 8 - pz) > max) continue;
            square(context, cam, key, y, newColor.get());
        }
        if (!showOld.get()) return;
        for (long key : oldChunks) {
            if (Math.abs(ChunkPos.getX(key) * 16 + 8 - px) > max || Math.abs(ChunkPos.getZ(key) * 16 + 8 - pz) > max) continue;
            square(context, cam, key, y, oldColor.get());
        }
    }

    public int newCount() {
        return newChunks.size();
    }
}
