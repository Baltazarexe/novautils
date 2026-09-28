package dev.novautils.modules.render;

import dev.novautils.modules.Category;
import dev.novautils.modules.Module;
import dev.novautils.render.RenderContext;
import dev.novautils.settings.Setting;
import dev.novautils.utils.EspRender;
import dev.novautils.utils.RenderUtils;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
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
 * <p>This scans loaded blocks and draws boxes rather than making stone transparent, which would
 * need a chunk-mesh mixin.
 */
public class Xray extends Module {
    private static final int DEFAULT_ALPHA = 0x73000000;

    /** One toggleable ore: {@code match} is a substring of the block id ("" = fallback for any other ore). */
    private record Ore(String match, Setting<Boolean> shown, Setting<Integer> color) {}

    private record Found(BlockPos pos, Ore ore) {}

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
        ore("", "other-ores", 0xFFB347, false);
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

    @Override
    public void onEnable() {
        rescanTimer = 0;
    }

    @Override
    public void onDisable() {
        found.clear();
    }

    /** Which ore bucket a block falls in, or null when it is not an ore at all. */
    private Ore oreOf(BlockState state) {
        if (state.isAir()) return null;

        Identifier key = BuiltInRegistries.BLOCK.getKey(state.getBlock());
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
        return path.contains("_ore") ? fallback : null;
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
        if (rescanTimer-- > 0) return;
        rescanTimer = 40;

        found.clear();

        BlockPos center = mc.player.blockPosition();
        int r = radius.get(), h = height.get();

        for (int x = -r; x <= r; x++) {
            for (int z = -r; z <= r; z++) {
                for (int y = -h; y <= h; y++) {
                    BlockPos pos = center.offset(x, y, z);
                    if (!mc.level.hasChunkAt(pos)) continue;

                    Ore ore = oreOf(mc.level.getBlockState(pos));
                    if (ore == null || !ore.shown().get()) continue;
                    if (exposedOnly.get() && !exposed(pos)) continue;
                    found.add(new Found(pos.immutable(), ore));
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

            context.poseStack().pushPose();
            context.poseStack().translate(-cameraPos.x, -cameraPos.y, -cameraPos.z);
            context.submitNodeCollector().submitCustomGeometry(context.poseStack(), EspRender.filledNoDepth(),
                (pose, buffer) -> RenderUtils.drawBox(pose, buffer, box, color));
            context.poseStack().popPose();
        }
    }
}
