package dev.novautils.modules.render;

import dev.novautils.modules.Category;
import dev.novautils.modules.Module;
import dev.novautils.render.RenderContext;
import dev.novautils.settings.Setting;
import dev.novautils.utils.RenderUtils;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.chunk.LevelChunkSection;
import net.minecraft.world.phys.AABB;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;

/**
 * Holes people dug: straight 1x1 shafts going down, walled in on all four sides the whole way. The
 * world almost never makes those (caves are wide and crooked), while players digging down for ores
 * or to a hidden base leave them everywhere, so they point at activity. Scans the loaded chunks a
 * couple per tick and outlines every shaft at least "min-depth" blocks deep.
 */
public class DigEsp extends Module {
    private final Setting<Integer> minDepth = register(Setting.builder(Integer.class, "min-depth")
        .description("How deep a 1x1 shaft has to be to count.").defaultValue(8).range(4, 64).build());
    private final Setting<Boolean> ladders = register(Setting.builder(Boolean.class, "count-ladders")
        .description("Shafts with ladders or scaffolding in them count too.").defaultValue(true).build());
    private final Setting<Integer> color = register(Setting.builder(Integer.class, "color")
        .description("Shaft colour.").defaultValue(0xFFC08BFF).asColor().build());
    private final Setting<Boolean> fill = register(Setting.builder(Boolean.class, "fill")
        .description("Tint the inside of the shaft, not just its edges.").defaultValue(true).build());

    /** A shaft: column (x, z) open from y = bottom to y = top, both inclusive. */
    private record Shaft(int x, int z, int bottom, int top) {}

    private final Map<Long, List<Shaft>> found = new HashMap<>();
    private final List<Long> queue = new ArrayList<>();
    private Level lastLevel;

    public DigEsp() {
        super("DigEsp", "Shows 1x1 shafts people dug straight down: a sign someone was there.", Category.RENDER);
    }

    @Override
    public void onDisable() {
        found.clear();
        queue.clear();
    }

    private boolean open(BlockState s) {
        if (s.isAir()) return true;
        return ladders.get() && (s.is(Blocks.LADDER) || s.is(Blocks.SCAFFOLDING));
    }

    private static boolean wall(BlockState s) {
        return s.isSolid();
    }

    private void scan(LevelChunk chunk) {
        LevelChunkSection[] sections = chunk.getSections();
        int minY = mc.level.getMinY();
        ChunkPos cp = chunk.getPos();
        int bx = cp.getMinBlockX(), bz = cp.getMinBlockZ();
        int topSection = sections.length - 1;
        while (topSection >= 0 && (sections[topSection] == null || sections[topSection].hasOnlyAir())) topSection--;
        List<Shaft> shafts = new ArrayList<>();
        if (topSection >= 0) {
            int topY = minY + topSection * 16 + 15;
            for (int x = 0; x < 16; x++) {
                for (int z = 0; z < 16; z++) {
                    int run = 0;
                    for (int y = topY; y >= minY; y--) {
                        boolean cell = open(at(chunk, sections, minY, bx + x, y, bz + z))
                            && wall(at(chunk, sections, minY, bx + x + 1, y, bz + z)) && wall(at(chunk, sections, minY, bx + x - 1, y, bz + z))
                            && wall(at(chunk, sections, minY, bx + x, y, bz + z + 1)) && wall(at(chunk, sections, minY, bx + x, y, bz + z - 1));
                        if (cell) {
                            run++;
                            continue;
                        }
                        if (run >= minDepth.get()) shafts.add(new Shaft(bx + x, bz + z, y + 1, y + run));
                        run = 0;
                    }
                    if (run >= minDepth.get()) shafts.add(new Shaft(bx + x, bz + z, minY, minY + run - 1));
                }
            }
        }
        if (shafts.isEmpty()) found.remove(cp.pack());
        else found.put(cp.pack(), shafts);
    }

    /** Block state, read straight from the chunk's sections when the spot is inside it. */
    private static BlockState at(LevelChunk chunk, LevelChunkSection[] sections, int minY, int x, int y, int z) {
        ChunkPos cp = chunk.getPos();
        if ((x >> 4) != cp.x() || (z >> 4) != cp.z()) return mc.level.getBlockState(new net.minecraft.core.BlockPos(x, y, z));
        int i = (y - minY) >> 4;
        if (i < 0 || i >= sections.length || sections[i] == null || sections[i].hasOnlyAir()) return Blocks.AIR.defaultBlockState();
        return sections[i].getBlockState(x & 15, y & 15, z & 15);
    }

    @Override
    public void onTick() {
        if (mc.level == null || mc.player == null) return;
        if (mc.level != lastLevel) {
            lastLevel = mc.level;
            found.clear();
            queue.clear();
        }
        if (queue.isEmpty()) {
            int r = mc.options.getEffectiveRenderDistance();
            ChunkPos center = mc.player.chunkPosition();
            List<long[]> list = new ArrayList<>();
            for (int dx = -r; dx <= r; dx++) {
                for (int dz = -r; dz <= r; dz++) {
                    list.add(new long[]{ChunkPos.pack(center.x() + dx, center.z() + dz), dx * dx + dz * dz});
                }
            }
            list.sort((a, b) -> Long.compare(a[1], b[1]));
            for (long[] e : list) queue.add(e[0]);
            found.keySet().retainAll(new HashSet<>(queue));
        }
        for (int n = 0; n < 2 && !queue.isEmpty(); n++) {
            long key = queue.remove(0);
            int cx = ChunkPos.getX(key), cz = ChunkPos.getZ(key);
            if (!mc.level.hasChunk(cx, cz)) {
                found.remove(key);
                continue;
            }
            scan(mc.level.getChunk(cx, cz));
        }
    }

    /** Shafts found around you (in-game test). */
    public int shaftCount() {
        int n = 0;
        for (List<Shaft> l : found.values()) n += l.size();
        return n;
    }

    @Override
    public void onRenderWorld(RenderContext context) {
        if (found.isEmpty()) return;
        int c = color.get();
        List<Shaft> list = new ArrayList<>();
        for (List<Shaft> l : found.values()) list.addAll(l);
        RenderUtils.world(context, (pose, buffer) -> {
            for (Shaft s : list) {
                AABB box = new AABB(s.x() + 0.05, s.bottom(), s.z() + 0.05, s.x() + 0.95, s.top() + 1, s.z() + 0.95);
                if (fill.get()) RenderUtils.drawFilledBox(pose, buffer, box, (c & 0xFFFFFF) | 0x30000000);
                RenderUtils.drawEdges(pose, buffer, box, (c & 0xFFFFFF) | 0xE0000000, 0.02);
            }
        });
    }
}
