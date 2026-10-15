package dev.novautils.modules.render;

import dev.novautils.modules.Category;
import dev.novautils.modules.Module;
import dev.novautils.render.RenderContext;
import dev.novautils.settings.Setting;
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

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;
import dev.novautils.NovaUtils;
import dev.novautils.utils.Storage;

import java.io.Reader;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
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

    private final Setting<Boolean> alert = register(Setting.builder(Boolean.class, "alert")
        .description("Toast + sound when a new find shows up (after the first scan).").defaultValue(true).build());

    private final Setting<Boolean> showCount = register(Setting.builder(Boolean.class, "show-count")
        .description("List how many of each are loaded, under the crosshair-left.").defaultValue(true).build());

    private final java.util.Set<Long> known = new java.util.HashSet<>();
    private boolean primed;
    private int passes;
    private final Map<Kind, String> names = new HashMap<>();

    /** A block you asked for with .search add (saved in novautils_search.json). */
    public static final class Custom {
        public String id;
        public int color;
        /** true = a mob / entity type instead of a block. */
        public boolean entity;
    }

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final int[] PALETTE = {0xFFFF8A3D, 0xFF4DD2FF, 0xFFFF5EA8, 0xFF7CFF6B, 0xFFFFE14D, 0xFFB48CFF};
    private final List<Custom> customs = new ArrayList<>();
    private final List<Kind> customKinds = new ArrayList<>();
    private final List<Kind> entityKinds = new ArrayList<>();
    private final Map<net.minecraft.world.entity.EntityType<?>, Kind> byEntity = new HashMap<>();
    private record EntityHit(net.minecraft.world.entity.Entity entity, Kind kind) {}
    private final List<EntityHit> entityHits = new ArrayList<>();
    private final java.util.Set<Integer> seenEntities = new java.util.HashSet<>();

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
        loadCustoms();
    }

    private void kind(String name, int color, boolean on, String... ids) {
        Setting<Boolean> shown = register(Setting.builder(Boolean.class, name)
            .description("Show " + name.replace('-', ' ') + ".").defaultValue(on).build());
        Setting<Integer> c = register(Setting.builder(Integer.class, name + "-color")
            .description("Colour for " + name.replace('-', ' ') + ".")
            .defaultValue(color).asColor().visible(shown::get).build());
        Kind k = new Kind(ids, shown, c);
        kinds.add(k);
        names.put(k, name.replace('-', ' '));
    }

    private Kind kindOf(BlockState state) {
        if (byBlock.isEmpty()) {
            for (Kind k : kinds) {
                for (String id : k.ids()) {
                    BuiltInRegistries.BLOCK.getOptional(net.minecraft.resources.ResourceLocation.withDefaultNamespace(id))
                        .ifPresent(b -> byBlock.put(b, k));
                }
            }
            for (Kind k : customKinds) {
                for (String id : k.ids()) {
                    if (id.startsWith("#")) {
                        net.minecraft.resources.ResourceLocation tagId = net.minecraft.resources.ResourceLocation.tryParse(id.substring(1));
                        if (tagId == null) continue;
                        var tag = net.minecraft.tags.TagKey.create(net.minecraft.core.registries.Registries.BLOCK, tagId);
                        for (Block b : BuiltInRegistries.BLOCK) if (b.defaultBlockState().is(tag)) byBlock.putIfAbsent(b, k);
                        continue;
                    }
                    net.minecraft.resources.ResourceLocation rl = net.minecraft.resources.ResourceLocation.tryParse(id);
                    if (rl != null) BuiltInRegistries.BLOCK.getOptional(rl).ifPresent(b -> byBlock.put(b, k));
                }
            }
        }
        return byBlock.get(state.getBlock());
    }

    private java.nio.file.Path customFile() {
        return Storage.configDir().resolve("novautils_search.json");
    }

    private void loadCustoms() {
        java.nio.file.Path file = customFile();
        if (!Files.exists(file)) return;
        try (Reader r = Files.newBufferedReader(file, StandardCharsets.UTF_8)) {
            List<Custom> loaded = GSON.fromJson(r, new TypeToken<List<Custom>>() {}.getType());
            if (loaded != null) for (Custom c : loaded) if (c != null && c.id != null) customs.add(c);
        } catch (java.io.IOException | RuntimeException e) {
            NovaUtils.LOG.error("Failed to load custom search blocks", e);
        }
        rebuildCustoms();
    }

    private void saveCustoms() {
        try {
            Files.createDirectories(customFile().getParent());
            try (Writer w = Files.newBufferedWriter(customFile(), StandardCharsets.UTF_8)) {
                GSON.toJson(customs, w);
            }
        } catch (java.io.IOException e) {
            NovaUtils.LOG.error("Failed to save custom search blocks", e);
        }
    }

    /** Rebuilds the lookup after the custom list changed and rescans what is loaded. */
    private void rebuildCustoms() {
        customKinds.clear();
        entityKinds.clear();
        byEntity.clear();
        entityHits.clear();
        seenEntities.clear();
        for (Custom c : customs) {
            Kind k = new Kind(new String[]{c.id},
                Setting.builder(Boolean.class, "custom").defaultValue(true).build(),
                Setting.builder(Integer.class, "custom-color").defaultValue(c.color).asColor().build());
            (c.entity ? entityKinds : customKinds).add(k);
            names.put(k, c.id.replace("minecraft:", ""));
        }
        byBlock.clear();
        found.clear();
        known.clear();
        queue.clear();
        primed = false;
        passes = 0;
    }

    private static String full(String id) {
        if (id.startsWith("#")) return "#" + (id.contains(":") ? id.substring(1) : "minecraft:" + id.substring(1));
        return id.contains(":") ? id : "minecraft:" + id;
    }

    /** .search add: a block id or #tag (ex. #c:ores), optional hex colour. Returns an error message or null. */
    public String addCustom(String id, Integer color) {
        String full = full(id);
        if (full.startsWith("#")) {
            net.minecraft.resources.ResourceLocation rl = net.minecraft.resources.ResourceLocation.tryParse(full.substring(1));
            if (rl == null) return "Bad tag " + id;
            var tag = net.minecraft.tags.TagKey.create(net.minecraft.core.registries.Registries.BLOCK, rl);
            int n = 0;
            for (Block b : BuiltInRegistries.BLOCK) if (b.defaultBlockState().is(tag)) n++;
            if (n == 0) return "No blocks in tag " + id;
        } else {
            net.minecraft.resources.ResourceLocation rl = net.minecraft.resources.ResourceLocation.tryParse(full);
            if (rl == null || BuiltInRegistries.BLOCK.getOptional(rl).isEmpty()) return "No block called " + id;
        }
        return put(full, color, false);
    }

    /** .search mob add: an entity id or #tag, optional hex colour. */
    public String addEntity(String id, Integer color) {
        String full = full(id);
        if (full.startsWith("#")) {
            net.minecraft.resources.ResourceLocation rl = net.minecraft.resources.ResourceLocation.tryParse(full.substring(1));
            if (rl == null) return "Bad tag " + id;
            var tag = net.minecraft.tags.TagKey.create(net.minecraft.core.registries.Registries.ENTITY_TYPE, rl);
            int n = 0;
            for (var t : BuiltInRegistries.ENTITY_TYPE) if (t.builtInRegistryHolder().is(tag)) n++;
            if (n == 0) return "No mobs in tag " + id;
        } else {
            net.minecraft.resources.ResourceLocation rl = net.minecraft.resources.ResourceLocation.tryParse(full);
            if (rl == null || BuiltInRegistries.ENTITY_TYPE.getOptional(rl).isEmpty()) return "No mob called " + id;
        }
        return put(full, color, true);
    }

    private String put(String full, Integer color, boolean entity) {
        customs.removeIf(c -> c.id.equals(full) && c.entity == entity);
        Custom c = new Custom();
        c.id = full;
        c.entity = entity;
        c.color = color != null ? color : PALETTE[customs.size() % PALETTE.length];
        customs.add(c);
        saveCustoms();
        rebuildCustoms();
        setEnabled(true);
        return null;
    }

    /** Id of the block under the crosshair, for ".search add" with nothing after it. */
    public String lookedAtBlock() {
        if (mc.hitResult instanceof net.minecraft.world.phys.BlockHitResult b && b.getType() == net.minecraft.world.phys.HitResult.Type.BLOCK) {
            return BuiltInRegistries.BLOCK.getKey(mc.level.getBlockState(b.getBlockPos()).getBlock()).toString();
        }
        return null;
    }

    /** Id of the mob under the crosshair (or the nearest one in front of you, up to 64 blocks). */
    public String lookedAtEntity() {
        net.minecraft.world.entity.Entity e = mc.crosshairPickEntity;
        if (e == null && mc.player != null) {
            var eye = mc.player.getEyePosition();
            var end = eye.add(mc.player.getLookAngle().scale(64));
            double best = Double.MAX_VALUE;
            for (var x : mc.level.getEntities(mc.player, new net.minecraft.world.phys.AABB(eye, end).inflate(1), en -> !en.isSpectator())) {
                var hit = x.getBoundingBox().inflate(0.3).clip(eye, end);
                if (hit.isPresent() && hit.get().distanceTo(eye) < best) {
                    best = hit.get().distanceTo(eye);
                    e = x;
                }
            }
        }
        return e == null ? null : BuiltInRegistries.ENTITY_TYPE.getKey(e.getType()).toString();
    }

    private Kind entityKindOf(net.minecraft.world.entity.Entity e) {
        if (entityKinds.isEmpty()) return null;
        if (byEntity.isEmpty()) {
            for (Kind k : entityKinds) {
                for (String id : k.ids()) {
                    if (id.startsWith("#")) {
                        net.minecraft.resources.ResourceLocation rl = net.minecraft.resources.ResourceLocation.tryParse(id.substring(1));
                        if (rl == null) continue;
                        var tag = net.minecraft.tags.TagKey.create(net.minecraft.core.registries.Registries.ENTITY_TYPE, rl);
                        for (var t : BuiltInRegistries.ENTITY_TYPE) if (t.builtInRegistryHolder().is(tag)) byEntity.putIfAbsent(t, k);
                    } else {
                        net.minecraft.resources.ResourceLocation rl = net.minecraft.resources.ResourceLocation.tryParse(id);
                        if (rl != null) BuiltInRegistries.ENTITY_TYPE.getOptional(rl).ifPresent(t -> byEntity.put(t, k));
                    }
                }
            }
        }
        return byEntity.get(e.getType());
    }

    /** Custom mobs currently loaded, refreshed every tick; new ones (after the first look) get a toast. */
    private void scanEntities() {
        entityHits.clear();
        if (entityKinds.isEmpty()) return;
        java.util.Set<Integer> now = new java.util.HashSet<>();
        for (net.minecraft.world.entity.Entity e : mc.level.entitiesForRendering()) {
            if (e == mc.player || !e.isAlive()) continue;
            Kind k = entityKindOf(e);
            if (k == null) continue;
            entityHits.add(new EntityHit(e, k));
            now.add(e.getId());
            if (!seenEntities.contains(e.getId()) && primed && alert.get()) {
                dev.novautils.gui.Toasts.push("Found " + names.get(k) + " at " + e.blockPosition().getX() + " " + e.blockPosition().getY() + " " + e.blockPosition().getZ(), k.color().get() | 0xFF000000);
            }
        }
        seenEntities.clear();
        seenEntities.addAll(now);
    }

    public boolean removeCustom(String id) {
        String full = full(id);
        boolean removed = customs.removeIf(c -> c.id.equals(full));
        if (removed) {
            saveCustoms();
            rebuildCustoms();
        }
        return removed;
    }

    public int clearCustoms() {
        int n = customs.size();
        customs.clear();
        saveCustoms();
        rebuildCustoms();
        return n;
    }

    public List<String> listCustoms() {
        List<String> out = new ArrayList<>();
        for (Custom c : customs) out.add((c.entity ? "[mob] " : "") + c.id + "  #" + String.format("%06X", c.color & 0xFFFFFF));
        return out;
    }

    @Override
    public void onEnable() {
        found.clear();
        known.clear();
        primed = false;
        passes = 0;
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
        int minY = mc.level.getMinBuildHeight();
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
        java.util.Set<Kind> alerted = new java.util.HashSet<>();
        for (Hit h : hits) {
            long key = h.pos().asLong();
            if (known.add(key) && primed && alert.get() && h.kind().shown().get() && alerted.add(h.kind())) {
                BlockPos p = h.pos();
                dev.novautils.gui.Toasts.push("Found " + names.get(h.kind()) + " at " + p.getX() + " " + p.getY() + " " + p.getZ(), h.kind().color().get() | 0xFF000000);
                mc.getSoundManager().play(net.minecraft.client.resources.sounds.SimpleSoundInstance.forUI(net.minecraft.sounds.SoundEvents.NOTE_BLOCK_BELL.value(), 1.5f));
            }
        }
        if (hits.isEmpty()) found.remove(cp.toLong());
        else found.put(cp.toLong(), hits);
    }

    @Override
    public void onTick() {
        if (mc.level == null || mc.player == null) return;
        scanEntities();
        if (mc.level != lastLevel) {
            lastLevel = mc.level;
            known.clear();
            primed = false;
            passes = 0;
            found.clear();
            queue.clear();
        }

        // refill the work queue with every loaded chunk around us, nearest first
        if (queue.isEmpty()) {
            if (passes++ > 0) primed = true;
            int r = mc.options.getEffectiveRenderDistance();
            ChunkPos center = mc.player.chunkPosition();
            List<long[]> list = new ArrayList<>();
            for (int dx = -r; dx <= r; dx++) {
                for (int dz = -r; dz <= r; dz++) {
                    list.add(new long[]{ChunkPos.asLong(center.x + dx, center.z + dz), dx * dx + dz * dz});
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
        if ((found.isEmpty() && entityHits.isEmpty()) || mc.player == null) return;
        Vec3 cam = context.cameraPos();
        Vec3 start = cam.add(mc.player.getLookAngle().scale(0.8));
        float partial = mc.getTimer().getGameTimeDeltaPartialTick(false);
        for (EntityHit h : entityHits) {
            int c = h.kind().color().get();
            var e = h.entity();
            AABB box = e.getBoundingBox().move(e.getPosition(partial).subtract(e.position()));
            RenderUtils.world(context, (pose, buffer) -> {
                RenderUtils.drawBox(pose, buffer, box, (c & 0xFFFFFF) | 0x50000000);
                if (tracers.get()) RenderUtils.drawRibbon(pose, buffer, start, box.getCenter(), 0.012, c | 0xFF000000);
            });
        }
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

    @Override
    public void onRenderHud(net.minecraft.client.gui.GuiGraphics g, float delta) {
        if (!showCount.get() || (found.isEmpty() && entityHits.isEmpty())) return;
        Map<Kind, Integer> counts = new java.util.LinkedHashMap<>();
        for (Kind k : allKinds()) counts.put(k, 0);
        for (List<Hit> hits : found.values()) for (Hit h : hits) counts.merge(h.kind(), 1, Integer::sum);
        for (EntityHit h : entityHits) counts.merge(h.kind(), 1, Integer::sum);
        int y = g.guiHeight() / 2 - 20;
        for (Kind k : allKinds()) {
            int n = counts.get(k);
            if (n == 0 || !k.shown().get()) continue;
            String t = names.get(k) + ": " + n;
            g.drawString(mc.font, t, 4, y, k.color().get() | 0xFF000000, true);
            y += mc.font.lineHeight + 1;
        }
    }

    private List<Kind> allKinds() {
        List<Kind> all = new ArrayList<>(kinds);
        all.addAll(customKinds);
        all.addAll(entityKinds);
        return all;
    }

    /** Custom mobs currently highlighted (for tests). */
    public int entityCount() {
        return entityHits.size();
    }

    /** Number of matching blocks currently known (for tests and the HUD). */
    public int count() {
        int n = 0;
        for (List<Hit> hits : found.values()) for (Hit h : hits) if (h.kind().shown().get()) n++;
        return n;
    }
}
