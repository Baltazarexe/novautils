package dev.novautils.modules.render;

import dev.novautils.gui.Theme;
import dev.novautils.gui.Toasts;
import dev.novautils.gui.UiFont;
import dev.novautils.modules.Category;
import dev.novautils.modules.Module;
import dev.novautils.render.RenderContext;
import dev.novautils.settings.Setting;
import dev.novautils.utils.RenderUtils;
import dev.novautils.utils.ScreenSpace;
import dev.novautils.utils.Storage;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.AbstractFurnaceBlock;
import net.minecraft.world.level.block.AnvilBlock;
import net.minecraft.world.level.block.BarrelBlock;
import net.minecraft.world.level.block.BaseRailBlock;
import net.minecraft.world.level.block.BeaconBlock;
import net.minecraft.world.level.block.BedBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.BrewingStandBlock;
import net.minecraft.world.level.block.ChestBlock;
import net.minecraft.world.level.block.ComparatorBlock;
import net.minecraft.world.level.block.DaylightDetectorBlock;
import net.minecraft.world.level.block.DispenserBlock;
import net.minecraft.world.level.block.EnchantingTableBlock;
import net.minecraft.world.level.block.EnderChestBlock;
import net.minecraft.world.level.block.HopperBlock;
import net.minecraft.world.level.block.LeverBlock;
import net.minecraft.world.level.block.NetherPortalBlock;
import net.minecraft.world.level.block.ObserverBlock;
import net.minecraft.world.level.block.RedstoneLampBlock;
import net.minecraft.world.level.block.RedstoneTorchBlock;
import net.minecraft.world.level.block.RepeaterBlock;
import net.minecraft.world.level.block.ShulkerBoxBlock;
import net.minecraft.world.level.block.piston.PistonBaseBlock;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.chunk.LevelChunkSection;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Finds places where people have been: it reads the chunks you have loaded and looks for blocks the
 * world doesn't generate on its own - storage, machines, redstone, building blocks, rails and portals.
 * Clues close together form a "discovery"; one with enough different kinds of clue is outlined,
 * announced with a toast, written to novautils_bases.csv and, when you look at it, explained in a
 * small card (what was found, how much, how far). Villages and other structures can match too, so
 * the card says so: loaded objects, not proof of a base.
 */
public class BaseFinder extends Module {
    public enum Clue {
        STORAGE("Storage", 0xFFFFC94A), MACHINES("Machines", 0xFFFF9F43), REDSTONE("Redstone", 0xFFFF4D6D),
        BUILDING("Building", 0xFF6BA6FF), TRAVEL("Travel", 0xFF66E07A);

        final String label;
        final int color;

        Clue(String label, int color) {
            this.label = label;
            this.color = color;
        }
    }

    private final Setting<Boolean> storage = register(Setting.builder(Boolean.class, "storage")
        .description("Chests, barrels, shulker boxes, ender chests, hoppers.").defaultValue(true).build());
    private final Setting<Boolean> machines = register(Setting.builder(Boolean.class, "machines")
        .description("Pistons, observers, dispensers, droppers, furnaces, anvils, enchanting tables, brewing stands, beacons.").defaultValue(true).build());
    private final Setting<Boolean> redstone = register(Setting.builder(Boolean.class, "redstone")
        .description("Redstone dust, repeaters, comparators, redstone torches, lamps, levers, daylight sensors.").defaultValue(true).build());
    private final Setting<Boolean> building = register(Setting.builder(Boolean.class, "building")
        .description("Beds, glass, concrete, wool, glazed terracotta, signs, banners.").defaultValue(true).build());
    private final Setting<Boolean> travel = register(Setting.builder(Boolean.class, "travel")
        .description("Rails and nether portals.").defaultValue(true).build());
    private final Setting<Boolean> skipStructures = register(Setting.builder(Boolean.class, "skip-structures")
        .description("Ignore clues next to blocks that only generated structures have: spawners, trial chamber vaults, tuff bricks and copper bulbs, ancient city sculk, village bells, mineshaft cobwebs, stronghold blocks, suspicious sand.").defaultValue(true).build());

    private final Setting<Integer> minTypes = register(Setting.builder(Integer.class, "min-clue-types")
        .description("How many different kinds of clue have to be together (1 = any pile of one kind).").defaultValue(2).range(1, 5).build());
    private final Setting<Integer> minObjects = register(Setting.builder(Integer.class, "min-objects")
        .description("How many clue blocks a discovery needs.").defaultValue(8).range(2, 100).build());
    private final Setting<Integer> spread = register(Setting.builder(Integer.class, "spread")
        .description("How far apart (blocks) clues can be and still count as one place.").defaultValue(12).range(4, 48).build());

    private final Setting<Boolean> outlines = register(Setting.builder(Boolean.class, "outlines")
        .description("Outline the clue blocks of each discovery, coloured by kind.").defaultValue(true).build());
    private final Setting<Boolean> card = register(Setting.builder(Boolean.class, "card")
        .description("Show a card about the discovery you are looking at.").defaultValue(true).build());
    private final Setting<Double> cardSize = register(Setting.builder(Double.class, "card-size")
        .description("Size of the card. It also shrinks with a big GUI scale so it never covers the screen.").defaultValue(1.0).range(0.5, 1.5).visible(card::get).build());
    private final Setting<Boolean> alert = register(Setting.builder(Boolean.class, "alert")
        .description("Toast and a sound for every new discovery.").defaultValue(true).build());
    private final Setting<Boolean> log = register(Setting.builder(Boolean.class, "log-to-file")
        .description("Write discoveries to config/novautils_bases.csv (time, dimension, position, what was found).").defaultValue(true).build());

    private record Hit(BlockPos pos, Block block, Clue clue) {}

    private record Discovery(Vec3 center, List<Hit> hits, Map<Block, Integer> blocks, Map<Clue, Integer> clues) {}

    /** Clue blocks per chunk, rescanned a few chunks per tick. */
    private final Map<Long, List<Hit>> found = new HashMap<>();
    private final List<Long> queue = new ArrayList<>();
    private final Map<Block, Clue> cache = new IdentityHashMap<>();
    /** Blocks only generated structures have, per chunk (skip-structures). */
    private final Map<Long, List<BlockPos>> markers = new HashMap<>();
    private final Map<Block, Boolean> markerCache = new IdentityHashMap<>();
    private final Set<Long> announced = new HashSet<>();
    private List<Discovery> discoveries = List.of();
    private Level lastLevel;
    private int rebuildIn;

    public BaseFinder() {
        super("BaseFinder", "Finds places players built: storage, machines, redstone and builds close together, with a card.", Category.RENDER);
    }

    private boolean on(Clue c) {
        return switch (c) {
            case STORAGE -> storage.get();
            case MACHINES -> machines.get();
            case REDSTONE -> redstone.get();
            case BUILDING -> building.get();
            case TRAVEL -> travel.get();
        };
    }

    /** What kind of clue a block is, or null for blocks the world generates everywhere. */
    private Clue clueOf(Block b) {
        Clue cached = cache.get(b);
        if (cached != null || cache.containsKey(b)) return cached;
        Clue c = classify(b);
        cache.put(b, c);
        return c;
    }

    private static Clue classify(Block b) {
        if (b instanceof ChestBlock || b instanceof BarrelBlock || b instanceof ShulkerBoxBlock || b instanceof EnderChestBlock || b instanceof HopperBlock) return Clue.STORAGE;
        if (b instanceof PistonBaseBlock || b instanceof ObserverBlock || b instanceof DispenserBlock || b instanceof AbstractFurnaceBlock
            || b instanceof AnvilBlock || b instanceof EnchantingTableBlock || b instanceof BrewingStandBlock || b instanceof BeaconBlock) return Clue.MACHINES;
        if (b == Blocks.REDSTONE_WIRE || b instanceof RepeaterBlock || b instanceof ComparatorBlock || b instanceof RedstoneTorchBlock
            || b instanceof RedstoneLampBlock || b instanceof LeverBlock || b instanceof DaylightDetectorBlock || b == Blocks.REDSTONE_BLOCK) return Clue.REDSTONE;
        if (b instanceof BaseRailBlock || b instanceof NetherPortalBlock) return Clue.TRAVEL;
        if (b instanceof BedBlock) return Clue.BUILDING;
        String path = BuiltInRegistries.BLOCK.getKey(b).getPath();
        if (path.endsWith("_concrete") || path.endsWith("_wool") || path.endsWith("_glazed_terracotta") || path.endsWith("glass")
            || path.endsWith("_sign") || path.endsWith("_banner")) return Clue.BUILDING;
        return null;
    }

    /** Blocks the world only places inside structures (trial chambers, ancient cities, villages, mineshafts...). */
    private boolean isMarker(Block b) {
        Boolean known = markerCache.get(b);
        if (known != null) return known;
        String p = BuiltInRegistries.BLOCK.getKey(b).getPath();
        boolean m = p.equals("spawner") || p.equals("trial_spawner") || p.equals("vault") || p.equals("heavy_core")
            || p.contains("tuff_brick") || p.equals("chiseled_tuff") || p.startsWith("polished_tuff")
            || p.contains("copper_bulb") || p.contains("copper_grate")
            || p.equals("sculk_shrieker") || p.equals("sculk_catalyst") || p.equals("reinforced_deepslate")
            || p.equals("bell") || p.equals("cobweb") || p.startsWith("infested_") || p.equals("end_portal_frame")
            || p.equals("suspicious_sand") || p.equals("suspicious_gravel");
        markerCache.put(b, m);
        return m;
    }

    @Override
    public void onDisable() {
        markers.clear();
        found.clear();
        queue.clear();
        discoveries = List.of();
    }

    private void scan(LevelChunk chunk) {
        List<Hit> hits = new ArrayList<>();
        List<BlockPos> marks = new ArrayList<>();
        LevelChunkSection[] sections = chunk.getSections();
        int minY = mc.level.getMinY();
        ChunkPos cp = chunk.getPos();
        for (int i = 0; i < sections.length && hits.size() < 2048; i++) {
            LevelChunkSection s = sections[i];
            if (s == null || s.hasOnlyAir() || !s.maybeHas(st -> clueOf(st.getBlock()) != null || (skipStructures.get() && isMarker(st.getBlock())))) continue;
            for (int y = 0; y < 16; y++) {
                for (int z = 0; z < 16; z++) {
                    for (int x = 0; x < 16; x++) {
                        Block b = s.getBlockState(x, y, z).getBlock();
                        Clue c = clueOf(b);
                        if (c == null && skipStructures.get() && isMarker(b)) marks.add(new BlockPos(cp.getMinBlockX() + x, minY + i * 16 + y, cp.getMinBlockZ() + z));
                        if (c != null) hits.add(new Hit(new BlockPos(cp.getMinBlockX() + x, minY + i * 16 + y, cp.getMinBlockZ() + z), b, c));
                    }
                }
            }
        }
        if (hits.isEmpty()) found.remove(cp.pack());
        else found.put(cp.pack(), hits);
        if (marks.isEmpty()) markers.remove(cp.pack());
        else markers.put(cp.pack(), marks);
    }

    @Override
    public void onTick() {
        if (mc.level == null || mc.player == null) return;
        if (mc.level != lastLevel) {
            lastLevel = mc.level;
            found.clear();
            queue.clear();
            announced.clear();
            markers.clear();
            discoveries = List.of();
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
            markers.keySet().retainAll(new HashSet<>(queue));
        }
        for (int n = 0; n < 6 && !queue.isEmpty(); n++) {
            long key = queue.remove(0);
            int cx = ChunkPos.getX(key), cz = ChunkPos.getZ(key);
            if (!mc.level.hasChunk(cx, cz)) {
                found.remove(key);
                markers.remove(key);
                continue;
            }
            scan(mc.level.getChunk(cx, cz));
        }
        if (--rebuildIn <= 0) {
            rebuildIn = 10;
            rebuild();
        }
    }

    /** Groups the clue blocks into discoveries: clues within "spread" of each other belong together. */
    private void rebuild() {
        int cell = spread.get();
        Map<Long, List<Hit>> cells = new HashMap<>();
        for (List<Hit> hs : found.values()) {
            for (Hit h : hs) {
                if (!on(h.clue())) continue;
                long k = BlockPos.asLong(Math.floorDiv(h.pos().getX(), cell), Math.floorDiv(h.pos().getY(), cell), Math.floorDiv(h.pos().getZ(), cell));
                cells.computeIfAbsent(k, x -> new ArrayList<>()).add(h);
            }
        }
        Set<Long> markerCells = new HashSet<>();
        if (skipStructures.get()) {
            for (List<BlockPos> ms : markers.values()) for (BlockPos m : ms)
                markerCells.add(BlockPos.asLong(Math.floorDiv(m.getX(), cell), Math.floorDiv(m.getY(), cell), Math.floorDiv(m.getZ(), cell)));
        }
        List<Discovery> out = new ArrayList<>();
        Set<Long> seen = new HashSet<>();
        for (Long start : cells.keySet()) {
            if (!seen.add(start)) continue;
            List<Hit> members = new ArrayList<>();
            boolean structure = false;
            ArrayDeque<Long> stack = new ArrayDeque<>();
            stack.push(start);
            while (!stack.isEmpty()) {
                long c = stack.pop();
                members.addAll(cells.get(c));
                int cx = BlockPos.getX(c), cy = BlockPos.getY(c), cz = BlockPos.getZ(c);
                for (int dx = -1; dx <= 1; dx++) for (int dy = -1; dy <= 1; dy++) for (int dz = -1; dz <= 1; dz++) {
                    long n = BlockPos.asLong(cx + dx, cy + dy, cz + dz);
                    if (markerCells.contains(n)) structure = true;
                    if (cells.containsKey(n) && seen.add(n)) stack.push(n);
                }
            }
            Map<Clue, Integer> clues = new EnumMap<>(Clue.class);
            Map<Block, Integer> blocks = new HashMap<>();
            double sx = 0, sy = 0, sz = 0;
            for (Hit h : members) {
                clues.merge(h.clue(), 1, Integer::sum);
                blocks.merge(h.block(), 1, Integer::sum);
                sx += h.pos().getX() + 0.5;
                sy += h.pos().getY() + 0.5;
                sz += h.pos().getZ() + 0.5;
            }
            if (structure) continue; // a trial chamber, ancient city, village, mineshaft...
            if (clues.size() < minTypes.get() || members.size() < minObjects.get()) continue;
            Discovery d = new Discovery(new Vec3(sx / members.size(), sy / members.size(), sz / members.size()), members, blocks, clues);
            out.add(d);
            announce(d);
        }
        discoveries = out;
    }

    private void announce(Discovery d) {
        long key = BlockPos.asLong((int) Math.floor(d.center().x / 48), (int) Math.floor(d.center().y / 48), (int) Math.floor(d.center().z / 48));
        for (int dx = -1; dx <= 1; dx++) for (int dy = -1; dy <= 1; dy++) for (int dz = -1; dz <= 1; dz++) {
            if (announced.contains(BlockPos.asLong(BlockPos.getX(key) + dx, BlockPos.getY(key) + dy, BlockPos.getZ(key) + dz))) return;
        }
        announced.add(key);
        BlockPos p = BlockPos.containing(d.center());
        String summary = summary(d);
        if (alert.get()) {
            Toasts.push("Discovery at " + p.getX() + " " + p.getY() + " " + p.getZ() + ": " + summary, Theme.accent());
            mc.getSoundManager().play(net.minecraft.client.resources.sounds.SimpleSoundInstance.forUI(net.minecraft.sounds.SoundEvents.NOTE_BLOCK_CHIME.value(), 1.2f));
        }
        if (log.get()) {
            Path file = Storage.configDir().resolve("novautils_bases.csv");
            String dim = mc.level.dimension().identifier().toString();
            String line = java.time.LocalDateTime.now().withNano(0) + "," + dim + "," + p.getX() + "," + p.getY() + "," + p.getZ() + ","
                + d.clues().size() + "," + d.hits().size() + ",\"" + summary + "\"" + System.lineSeparator();
            try {
                boolean fresh = !Files.exists(file);
                Files.createDirectories(file.getParent());
                if (fresh) Files.writeString(file, "time,dimension,x,y,z,clue_types,objects,found" + System.lineSeparator(), StandardCharsets.UTF_8);
                Files.writeString(file, line, StandardCharsets.UTF_8, StandardOpenOption.APPEND);
            } catch (IOException e) {
                dev.novautils.NovaUtils.LOG.warn("BaseFinder: could not write {}", file, e);
            }
        }
    }

    /** "17 Machines, 22 Redstone" - biggest kinds first. */
    private static String summary(Discovery d) {
        List<Map.Entry<Clue, Integer>> list = new ArrayList<>(d.clues().entrySet());
        list.sort((a, b) -> b.getValue() - a.getValue());
        StringBuilder sb = new StringBuilder();
        for (Map.Entry<Clue, Integer> e : list) {
            if (sb.length() > 0) sb.append(", ");
            sb.append(e.getValue()).append(' ').append(e.getKey().label);
        }
        return sb.toString();
    }

    /** Discoveries in this dimension (in-game test). */
    public int discoveryCount() {
        return discoveries.size();
    }

    @Override
    public void onRenderWorld(RenderContext context) {
        if (!outlines.get() || discoveries.isEmpty()) return;
        List<Discovery> list = discoveries;
        RenderUtils.world(context, (pose, buffer) -> {
            int drawn = 0;
            for (Discovery d : list) {
                for (Hit h : d.hits()) {
                    if (drawn++ > 2500) return;
                    BlockPos p = h.pos();
                    RenderUtils.drawEdges(pose, buffer, new AABB(p).deflate(0.02), (h.clue().color & 0xFFFFFF) | 0xC0000000, 0.012);
                }
            }
        });
    }

    @Override
    public void onRenderHud(GuiGraphicsExtractor g, DeltaTracker delta) {
        if (!card.get() || discoveries.isEmpty() || mc.player == null || dev.novautils.utils.PlatformCompat.currentScreen(mc) != null) return;
        if (!ScreenSpace.begin()) return;
        // the discovery closest to the crosshair, if it's anywhere near it
        Discovery focus = null;
        double[] at = null;
        double best = 140 * 140;
        double cx = g.guiWidth() / 2.0, cy = g.guiHeight() / 2.0;
        for (Discovery d : discoveries) {
            double[] s = ScreenSpace.project(d.center());
            if (s == null) continue;
            double dd = (s[0] - cx) * (s[0] - cx) + (s[1] - cy) * (s[1] - cy);
            if (dd < best) {
                best = dd;
                focus = d;
                at = s;
            }
        }
        if (focus != null) drawCard(g, focus, at);
    }

    private void drawCard(GuiGraphicsExtractor g, Discovery d, double[] at) {
        List<Map.Entry<Block, Integer>> blocks = new ArrayList<>(d.blocks().entrySet());
        blocks.sort((a, b) -> b.getValue() - a.getValue());
        List<Map.Entry<Clue, Integer>> clues = new ArrayList<>(d.clues().entrySet());
        clues.sort((a, b) -> b.getValue() - a.getValue());
        Block top = blocks.get(0).getKey();
        Clue topClue = clueOf(top);

        String together = d.clues().size() + (d.clues().size() == 1 ? " clue type" : " clue types") + " together / " + d.hits().size() + " nearby objects";
        int w = Math.max(196, UiFont.width(together) + 16), icons = Math.min(4, blocks.size());
        int rows = (clues.size() + 1) / 2;
        int h = 81 + rows * 12 + 20;
        float s = (float) (cardSize.get() * Math.min(1.0, 2.0 / mc.getWindow().getGuiScale()));
        int px = (int) Math.min(at[0] + 24, g.guiWidth() - w * s - 4), py = (int) Math.max(4, Math.min(at[1] - h * s / 2.0, g.guiHeight() - h * s - 4));
        g.pose().pushMatrix();
        g.pose().translate(px, py);
        g.pose().scale(s, s);
        int x = 0, y = 0;

        Theme.softShadow(g, x, y, w, h, 6, 0.8f);
        Theme.rounded(g, x, y, w, h, 6, 0xEE15171C);
        Theme.rounded(g, x + 7, y + 7, 2, 9, 1, 0xFFE8EAF0);
        UiFont.text(g, "BASEFINDER / DISCOVERIES", x + 13, y + 8, Theme.TEXT, false);
        String dist = Math.round(mc.player.position().distanceTo(d.center())) + "m";
        UiFont.text(g, dist, x + w - 8 - UiFont.width(dist), y + 8, Theme.TEXT, false);
        UiFont.text(g, top.getName().getString(), x + 8, y + 22, topClue == null ? Theme.accent() : topClue.color, false);
        UiFont.text(g, together, x + 8, y + 38, Theme.TEXT_DIM, false);

        // the most common blocks, with how many of each
        for (int i = 0; i < icons; i++) {
            Block b = blocks.get(i).getKey();
            int ix = x + 8 + i * 30, iy = y + 51;
            Theme.rounded(g, ix, iy, 26, 22, 3, 0xFF22252D);
            ItemStack stack = new ItemStack(b);
            if (!stack.isEmpty()) g.item(stack, ix + 2, iy + 2);
            else Theme.rounded(g, ix + 5, iy + 5, 10, 10, 2, clueOf(b).color);
            String n = String.valueOf(blocks.get(i).getValue());
            UiFont.text(g, n, ix + 25 - UiFont.width(n), iy + 13, 0xFFFFFFFF, true);
            g.fill(ix, iy + 24, ix + 26, iy + 26, clueOf(b).color);
        }

        // totals per kind of clue
        for (int i = 0; i < clues.size(); i++) {
            int tx = x + 8 + (i % 2) * ((w - 16) / 2), ty = y + 81 + (i / 2) * 12;
            Clue c = clues.get(i).getKey();
            g.fill(tx, ty + 1, tx + 4, ty + 6, c.color);
            UiFont.text(g, clues.get(i).getValue() + " " + c.label, tx + 8, ty, Theme.TEXT, false);
        }

        int fy = y + h - 13;
        g.fill(x + 8, fy - 4, x + w - 8, fy - 3, 0x18FFFFFF);
        UiFont.text(g, "Loaded objects / not proof of a base", x + 8, fy, Theme.TEXT_OFF, false);
        g.pose().popMatrix();
    }
}
