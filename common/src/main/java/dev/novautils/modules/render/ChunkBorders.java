package dev.novautils.modules.render;

import dev.novautils.modules.Category;
import dev.novautils.modules.Module;
import dev.novautils.render.RenderContext;
import dev.novautils.settings.Setting;
import dev.novautils.utils.RenderUtils;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.levelgen.WorldgenRandom;
import net.minecraft.world.phys.AABB;

/**
 * Outlines the chunk you're standing in (and optionally its neighbours), and fills slime chunks in
 * green. Slime chunks need the world seed: known automatically in singleplayer, or set it with
 * {@code .seed <number>} for a server whose seed you know.
 */
public class ChunkBorders extends Module {
    private final Setting<Boolean> neighbours = register(Setting.builder(Boolean.class, "neighbours")
        .description("Also outline the 8 chunks around yours.").defaultValue(false).build());

    private final Setting<Boolean> slime = register(Setting.builder(Boolean.class, "slime-chunks")
        .description("Highlight slime chunks (needs the seed).").defaultValue(true).build());

    private final Setting<Integer> slimeRange = register(Setting.builder(Integer.class, "slime-range")
        .description("Slime chunks within this many chunks.").defaultValue(4).range(1, 12)
        .visible(() -> slime.get()).build());

    private final Setting<Integer> color = register(Setting.builder(Integer.class, "color")
        .description("Border colour.").defaultValue(0x80FFD84A).asColor().build());

    /** Seed typed with .seed, for servers. Empty = only singleplayer. */
    private final Setting<String> seed = register(Setting.builder(String.class, "seed")
        .defaultValue("").visible(() -> false).build());

    public ChunkBorders() {
        super("ChunkBorders", "Chunk outlines and slime chunks.", Category.RENDER);
    }

    public void setSeed(String value) {
        seed.set(value);
    }

    /** The world seed if we know it. */
    public Long seed() {
        if (mc.getSingleplayerServer() != null) return mc.getSingleplayerServer().overworld().getSeed();
        if (seed.get().isEmpty()) return null;
        try {
            return Long.parseLong(seed.get());
        } catch (NumberFormatException e) {
            return (long) seed.get().hashCode(); // text seeds work like in "create world"
        }
    }

    public static boolean isSlimeChunk(long seed, int cx, int cz) {
        return WorldgenRandom.seedSlimeChunk(cx, cz, seed, 987234911L).nextInt(10) == 0;
    }

    @Override
    public void onRenderWorld(RenderContext context) {
        if (mc.player == null || mc.level == null) return;
        ChunkPos here = mc.player.chunkPosition();
        int c = color.get();
        double y0 = mc.player.getY() - 16, y1 = mc.player.getY() + 16;
        int r = neighbours.get() ? 1 : 0;
        for (int dx = -r; dx <= r; dx++) {
            for (int dz = -r; dz <= r; dz++) {
                int bx = (here.x() + dx) << 4, bz = (here.z() + dz) << 4;
                AABB box = new AABB(bx, y0, bz, bx + 16, y1, bz + 16);
                int cc = dx == 0 && dz == 0 ? c : (c & 0xFFFFFF) | 0x30000000;
                RenderUtils.world(context, (pose, buffer) -> RenderUtils.drawBox(pose, buffer, box, cc));
            }
        }

        Long s = slime.get() && mc.level.dimension() == Level.OVERWORLD ? seed() : null;
        if (s == null) return;
        int sr = slimeRange.get();
        double fy = Math.floor(mc.player.getY()) + 0.02;
        for (int dx = -sr; dx <= sr; dx++) {
            for (int dz = -sr; dz <= sr; dz++) {
                int cx = here.x() + dx, cz = here.z() + dz;
                if (!isSlimeChunk(s, cx, cz)) continue;
                AABB box = new AABB(cx << 4, fy, cz << 4, (cx << 4) + 16, fy + 0.05, (cz << 4) + 16);
                RenderUtils.world(context, (pose, buffer) -> RenderUtils.drawBox(pose, buffer, box, 0x4040FF60));
            }
        }
    }
}
