package dev.novautils.modules.render;

import dev.novautils.modules.Category;
import dev.novautils.modules.Module;
import dev.novautils.render.RenderContext;
import dev.novautils.settings.Setting;
import dev.novautils.utils.RenderUtils;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.List;

/**
 * Highlights ores through terrain. Every ore can be switched on/off on its own and has its own
 * colour (the colour row only shows while that ore is on). Deepslate and nether variants follow
 * their base ore.
 *
 * <p>"exposed-only" keeps ores that touch air/water - useful on servers with anti-xray, which
 * hides real ores and scatters fake ones inside solid stone.
 *
 * <p>Two ways to show them (mode): SEE_THROUGH: everything that isn't an
 * ore you picked (plus spawners and chests) simply isn't drawn, so you look straight through the
 * world at the ores themselves, fully lit. BOXES keeps the world and draws coloured boxes around
 * ores through it. BOTH does both. SEE_THROUGH works with plain Minecraft and with Sodium (XrayMixin,
 * SodiumXrayMixin): chunk meshes are rebuilt without the hidden blocks.
 */
public class Xray extends Module {
    private static final int DEFAULT_ALPHA = 0x73000000;

    /** One toggleable ore: {@code match} is a substring of the block id ("" = fallback for any other ore). */
    private record Ore(String match, Setting<Boolean> shown, Setting<Integer> color) {}

    private record Found(BlockPos pos, Ore ore) {}

    public enum Mode { SEE_THROUGH, BOXES, BOTH }

    private final Setting<Mode> mode = register(Setting.builder(Mode.class, "mode")
        .description("SEE_THROUGH: hide everything but ores. BOXES: boxes through walls. BOTH.")
        .defaultValue(Mode.SEE_THROUGH).build());

    private final Setting<Boolean> showFluids = register(Setting.builder(Boolean.class, "show-fluids")
        .description("SEE_THROUGH: keep water and lava visible.")
        .defaultValue(false).visible(() -> mode.get() != Mode.BOXES).build());

    private final Setting<Boolean> extras = register(Setting.builder(Boolean.class, "spawners-and-chests")
        .description("SEE_THROUGH: also keep spawners, chests, barrels and shulkers visible.")
        .defaultValue(true).visible(() -> mode.get() != Mode.BOXES).build());

    private final Setting<Integer> radius = register(Setting.builder(Integer.class, "radius")
        .description("Horizontal scan radius in blocks. Higher costs more when it rescans.")
        .defaultValue(16).range(6, 32).build());

    private final Setting<Integer> height = register(Setting.builder(Integer.class, "height")
        .description("How many blocks above and below you to scan.")
        .defaultValue(12).range(4, 48).build());

    private final Setting<Boolean> exposedOnly = register(Setting.builder(Boolean.class, "exposed-only")
        .description("Only ores touching air or liquid. Filters out anti-xray fake ores.")
        .defaultValue(false).build());

    private final List<Ore> ores = new ArrayList<>();
    private final List<Found> found = new ArrayList<>();
    private int rescanTimer = 0;

    public Xray() {
        super("Xray", "Highlights ores through terrain. Pick which ores and their colours.", Category.RENDER);

        ore("diamond_ore", "diamond", 0x4AE8E0, true);
        ore("ancient_debris", "ancient-debris", 0xA070C8, true);
        ore("emerald_ore", "emerald", 0x3BE05A, true);
        ore("gold_ore", "gold", 0xFFD84A, true);
        ore("iron_ore", "iron", 0xE0C8A0, true);
        ore("redstone_ore", "redstone", 0xFF3B3B, true);
        ore("lapis_ore", "lapis", 0x3B6BFF, true);
        ore("copper_ore", "copper", 0xE07A3C, false);
        ore("coal_ore", "coal", 0x585858, false);
        ore("quartz_ore", "quartz", 0xF0EAE2, false);
        ore("", "all-other-ores", 0xFFB347, true);
    }

    private void ore(String match, String name, int rgb, boolean onByDefault) {
        Setting<Boolean> shown = register(Setting.builder(Boolean.class, name)
            .description("Show " + name.replace('-', ' ') + ".")
            .defaultValue(onByDefault)
            .build());
        Setting<Integer> color = register(Setting.builder(Integer.class, name + "-color")
            .description("Colour for " + name.replace('-', ' ') + ".")
            .defaultValue(DEFAULT_ALPHA | rgb)
            .asColor()
            .visible(shown::get)
            .build());
        ores.add(new Ore(match, shown, color));
    }

    // ---- see-through state, read by the chunk-building threads (so: immutable snapshots + volatile)

    private static volatile boolean seeThrough;
    private static volatile boolean hideFluids;
    private static volatile java.util.Set<net.minecraft.world.level.block.Block> keep = java.util.Set.of();
    private String appliedConfig;

    private static final String[] EXTRA_BLOCKS = {"spawner", "trial_spawner", "vault", "chest", "trapped_chest",
        "ender_chest", "barrel", "shulker_box", "end_portal_frame"};

    /** Chunk meshing: should this block be left out (SEE_THROUGH active and it isn't one we keep)? */
    public static boolean hides(BlockState state) {
        return seeThrough && !state.isAir() && !keep.contains(state.getBlock());
    }

    /** Chunk meshing: should water/lava be left out? */
    public static boolean hidesFluids() {
        return seeThrough && hideFluids;
    }

    /** LightmapMixin: light everything up while see-through is on, or the ores are pitch black. */
    public static boolean forcesBright() {
        return seeThrough;
    }

    private String currentConfig() {
        StringBuilder sb = new StringBuilder(mode.get().name()).append(showFluids.get()).append(extras.get());
        for (Ore ore : ores) sb.append(ore.shown().get() ? '1' : '0');
        return sb.toString();
    }

    /** Recompute what stays visible and rebuild the world's chunk meshes if anything changed. */
    private void applySeeThrough() {
        String config = isEnabled() ? currentConfig() : "off";
        if (config.equals(appliedConfig)) return;
        appliedConfig = config;

        boolean on = isEnabled() && mode.get() != Mode.BOXES;
        java.util.Set<net.minecraft.world.level.block.Block> blocks = new java.util.HashSet<>();
        if (on) {
            for (var block : BuiltInRegistries.BLOCK) {
                Ore ore = oreOf(block.defaultBlockState());
                if (ore != null && ore.shown().get()) blocks.add(block);
                if (extras.get()) {
                    String path = BuiltInRegistries.BLOCK.getKey(block).getPath();
                    for (String e : EXTRA_BLOCKS) if (path.equals(e) || path.endsWith("_" + e)) blocks.add(block);
                }
            }
        }
        keep = java.util.Set.copyOf(blocks);
        hideFluids = !showFluids.get();
        boolean changed = seeThrough != on || on;
        seeThrough = on;
        if (changed && mc.level != null) dev.novautils.utils.PlatformCompat.reloadChunks(mc);
    }

    @Override
    public void onEnable() {
        rescanTimer = 0;
        appliedConfig = null;
        applySeeThrough();
    }

    @Override
    public void onDisable() {
        found.clear();
        appliedConfig = null;
        applySeeThrough();
    }

    /** Ore verdict per block (a tag lookup per scanned position would be slow); cleared on level change. */
    private final java.util.Map<net.minecraft.world.level.block.Block, java.util.Optional<Ore>> oreCache = new java.util.HashMap<>();
    private Object cacheLevel;

    /**
     * Any ore at all, modded ones included: a block tag that is an ore tag (c:ores, forge:ores,
     * ores/iron, minecraft:coal_ores...), or a name that says "ore".
     */
    public static boolean looksLikeOre(BlockState state, String path) {
        if (path.contains("_ore") || path.startsWith("ore_") || path.endsWith("_ores") || path.equals("ancient_debris")) return true;
        return BuiltInRegistries.BLOCK.wrapAsHolder(state.getBlock()).tags().anyMatch(tag -> {
            String p = tag.location().getPath();
            return p.equals("ores") || p.startsWith("ores/") || p.endsWith("_ores");
        });
    }

    /** Which ore bucket a block falls in, or null when it is not an ore at all. */
    private Ore oreOf(BlockState state) {
        if (state.isAir()) return null;
        if (mc.level != cacheLevel) {
            cacheLevel = mc.level;
            oreCache.clear();
        }
        var cached = oreCache.get(state.getBlock());
        if (cached != null) return cached.orElse(null);
        Ore result = classify(state);
        oreCache.put(state.getBlock(), java.util.Optional.ofNullable(result));
        return result;
    }

    private Ore classify(BlockState state) {

        ResourceLocation key = BuiltInRegistries.BLOCK.getKey(state.getBlock());
        if (key == null) return null;
        String path = key.getPath();

        Ore fallback = null;
        for (Ore ore : ores) {
            if (ore.match().isEmpty()) {
                fallback = ore;
            } else if (path.contains(ore.match())) {
                return ore;
            }
        }
        return looksLikeOre(state, path) ? fallback : null;
    }

    private boolean exposed(BlockPos pos) {
        for (Direction d : Direction.values()) {
            BlockState neighbor = mc.level.getBlockState(pos.relative(d));
            if (neighbor.isAir() || !neighbor.getFluidState().isEmpty()) return true;
        }
        return false;
    }

    @Override
    public void onTick() {
        if (mc.level == null || mc.player == null) return;
        applySeeThrough();
        // with a shaderpack the kept ores render dark, so add the coloured boxes on top
        if (mode.get() == Mode.SEE_THROUGH && !dev.novautils.utils.ShaderCompat.shadersActive()) {
            found.clear();
            return;
        }
        if (rescanTimer-- > 0) return;
        rescanTimer = 40;

        found.clear();

        BlockPos center = mc.player.blockPosition();
        int r = radius.get(), h = height.get();
        int minX = center.getX() - r, maxX = center.getX() + r;
        int minZ = center.getZ() - r, maxZ = center.getZ() + r;
        int lowY = center.getY() - h, highY = center.getY() + h;
        int levelMinY = mc.level.getMinBuildHeight();
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();

        // chunk by chunk, 16x16x16 section by section: a section whose palette has no ore at all is
        // skipped without reading a single block (most of them), so big radii don't stutter
        for (int cx = minX >> 4; cx <= maxX >> 4; cx++) {
            for (int cz = minZ >> 4; cz <= maxZ >> 4; cz++) {
                if (!mc.level.hasChunk(cx, cz)) continue;
                var sections = mc.level.getChunk(cx, cz).getSections();
                for (int i = 0; i < sections.length; i++) {
                    var section = sections[i];
                    int sy = levelMinY + i * 16;
                    if (section == null || section.hasOnlyAir() || sy + 15 < lowY || sy > highY) continue;
                    if (!section.maybeHas(st -> { Ore o = oreOf(st); return o != null && o.shown().get(); })) continue;
                    for (int y = 0; y < 16; y++) {
                        int wy = sy + y;
                        if (wy < lowY || wy > highY) continue;
                        for (int z = 0; z < 16; z++) {
                            int wz = (cz << 4) + z;
                            if (wz < minZ || wz > maxZ) continue;
                            for (int x = 0; x < 16; x++) {
                                int wx = (cx << 4) + x;
                                if (wx < minX || wx > maxX) continue;
                                Ore ore = oreOf(section.getBlockState(x, y, z));
                                if (ore == null || !ore.shown().get()) continue;
                                pos.set(wx, wy, wz);
                                if (exposedOnly.get() && !exposed(pos)) continue;
                                found.add(new Found(pos.immutable(), ore));
                            }
                        }
                    }
                }
            }
        }
    }

    /** Found blocks per ore bucket from the last scan (for the count in the menu tooltip). */
    public int count() {
        return found.size();
    }

    @Override
    public void onRenderWorld(RenderContext context) {
        if (found.isEmpty()) return;

        Vec3 cameraPos = context.cameraPos();

        for (Found entry : found) {
            // Checked at draw time too, so switching an ore off or recolouring it is instant
            // instead of waiting for the next rescan.
            if (!entry.ore().shown().get()) continue;
            int color = entry.ore().color().get();
            AABB box = new AABB(entry.pos());

                        RenderUtils.world(context, (pose, buffer) -> RenderUtils.drawBox(pose, buffer, box, color));
        }
    }
}
