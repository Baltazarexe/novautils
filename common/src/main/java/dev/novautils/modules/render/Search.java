package dev.novautils.modules.render;

import dev.novautils.modules.Category;
import dev.novautils.modules.Module;
import dev.novautils.render.RenderContext;
import dev.novautils.settings.Setting;
import dev.novautils.utils.EspRender;
import dev.novautils.utils.RenderUtils;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.chunk.LevelChunkSection;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Finds rare blocks in every loaded chunk and highlights them through walls: spawners (find dungeons
 * and trial chambers), nether and end portals, end portal frames (strongholds), beacons, dragon eggs,
 * budding amethyst, conduits. Chunks are scanned a few per tick, skipping any 16x16x16 section that
 * can't contain a match, so even a big render distance doesn't stutter.
 */
public class Search extends Module {
    private record Kind(String[] ids, Setting<Boolean> shown, Setting<Integer> color) {}
    private record Hit(BlockPos pos, Kind kind) {}

    private final List<Kind> kinds = new ArrayList<>();
    private final Map<Block, Kind> byBlock = new HashMap<>();

    private final Setting<Boolean> tracers = register(Setting.builder(Boolean.class, "tracers")
        .description("Also draw a line to each find.").defaultValue(false).build());

    private final Setting<Integer> maxShown = register(Setting.builder(Integer.class, "max-shown")
        .description("Draw at most this many (portals are many blocks each).").defaultValue(400).range(50, 3000).build());

    private final Map<Long, List<Hit>> found = new HashMap<>();
    private final List<Long> queue = new ArrayList<>();
    private Object lastLevel;

    public Search() {
        super("Search", "Highlights spawners, portals, beacons and other rare blocks.", Category.RENDER);
        kind("spawners", 0x80FF4040, true, "spawner", "trial_spawner", "vault");
        kind("nether-portals", 0x60A040FF, true, "nether_portal");
        kind("end-portals", 0x8040FFB0, true, "end_portal_frame", "end_portal", "end_gateway");
        kind("beacons", 0x8040E0FF, true, "beacon");
        kind("dragon-egg", 0x80B040FF, true, "dragon_egg");
        kind("budding-amethyst", 0x60C080FF, false, "budding_amethyst");
        kind("conduits", 0x8040A0FF, false, "conduit");
    }

    private void kind(String name, int color, boolean on, String... ids) {
        Setting<Boolean> shown = register(Setting.builder(Boolean.class, name)
            .description("Show " + name.replace('-', ' ') + ".").defaultValue(on).build());
        Setting<Integer> c = register(Setting.builder(Integer.class, name + "-color")
            .description("Colour for " + name.replace('-', ' ') + ".")
            .defaultValue(color).asColor().visible(shown::get).build());
        kinds.add(new Kind(ids, shown, c));
    }

    private Kind kindOf(BlockState state) {
        if (byBlock.isEmpty()) {
            for (Kind k : kinds) {
                for (String id : k.ids()) {
                    BuiltInRegistries.BLOCK.getOptional(net.minecraft.resources.Identifier.withDefaultNamespace(id))
                        .ifPresent(b -> byBlock.put(b, k));
                }
            }
        }
        return byBlock.get(state.getBlock());
    }

    @Override
    public void onEnable() {
        found.clear();
        queue.clear();
        lastLevel = null;
    }

    @Override
    public void onDisable() {
        found.clear();
        queue.clear();
    }

    private void scan(LevelChunk chunk) {
        List<Hit> hits = new ArrayList<>();
        LevelChunkSection[] sections = chunk.getSections();
        int minY = mc.level.getMinY();
        ChunkPos cp = chunk.getPos();
        for (int i = 0; i < sections.length; i++) {
            LevelChunkSection s = sections[i];
            if (s == null || s.hasOnlyAir() || !s.maybeHas(st -> kindOf(st) != null)) continue;
            for (int y = 0; y < 16; y++) {
                for (int z = 0; z < 16; z++) {
                    for (int x = 0; x < 16; x++) {
                        Kind k = kindOf(s.getBlockState(x, y, z));
                        if (k != null) {
                            hits.add(new Hit(new BlockPos(cp.getMinBlockX() + x, minY + i * 16 + y, cp.getMinBlockZ() + z), k));
                        }
                    }
                }
            }
        }
        if (hits.isEmpty()) found.remove(cp.pack());
        else found.put(cp.pack(), hits);
    }

    @Override
    public void onTick() {
        if (mc.level == null || mc.player == null) return;
        if (mc.level != lastLevel) {
            lastLevel = mc.level;
            found.clear();
            queue.clear();
        }

        // refill the work queue with every loaded chunk around us, nearest first
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
            // forget chunks that unloaded
            Set<Long> keep = new java.util.HashSet<>(queue);
            found.keySet().retainAll(keep);
        }

        for (int n = 0; n < 6 && !queue.isEmpty(); n++) {
            long key = queue.remove(0);
            int cx = ChunkPos.getX(key), cz = ChunkPos.getZ(key);
            if (!mc.level.hasChunk(cx, cz)) {
                found.remove(key);
                continue;
            }
            scan(mc.level.getChunk(cx, cz));
        }
    }

    @Override
    public void onRenderWorld(RenderContext context) {
        if (found.isEmpty() || mc.player == null) return;
        Vec3 cam = context.cameraPos();
        Vec3 start = cam.add(mc.player.getLookAngle().scale(0.8));
        int drawn = 0;
        for (List<Hit> hits : found.values()) {
            for (Hit h : hits) {
                if (!h.kind().shown().get()) continue;
                if (drawn++ >= maxShown.get()) return;
                int c = h.kind().color().get();
                AABB box = new AABB(h.pos());
                RenderUtils.world(context, (pose, buffer) -> {
                    RenderUtils.drawBox(pose, buffer, box, c);
                    if (tracers.get()) RenderUtils.drawRibbon(pose, buffer, start, box.getCenter(), 0.012, c | 0xFF000000);
                });
            }
        }
    }

    /** Number of matching blocks currently known (for tests and the HUD). */
    public int count() {
        int n = 0;
        for (List<Hit> hits : found.values()) for (Hit h : hits) if (h.kind().shown().get()) n++;
        return n;
    }
}
