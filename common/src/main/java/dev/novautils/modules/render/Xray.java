package dev.novautils.modules.render;

import dev.novautils.render.RenderContext;
import dev.novautils.modules.Category;
import dev.novautils.modules.Module;
import dev.novautils.settings.Setting;
import dev.novautils.utils.EspRender;
import dev.novautils.utils.RenderUtils;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Highlights ores through terrain. Each ore has its own colour, editable in the menu (click a
 * colour row to open R/G/B/A sliders). Deepslate and nether variants follow their base ore.
 *
 * <p>This scans loaded blocks and draws boxes rather than making stone transparent, which would
 * need a chunk-mesh mixin.
 */
public class Xray extends Module {
    private static final int VERTICAL_HALF_RANGE = 12;
    private static final int DEFAULT_ALPHA = 0x73000000;

    private record Found(BlockPos pos, int color) {}

    private final Setting<Integer> radius = register(Setting.builder(Integer.class, "radius")
        .description("Horizontal scan radius in blocks. Higher costs more when it rescans.")
        .defaultValue(16).range(6, 32).build());

    /** Ore key (a substring of the block id) to its colour setting, in menu order. */
    private final Map<String, Setting<Integer>> colors = new LinkedHashMap<>();

    private final List<Found> found = new ArrayList<>();
    private int rescanTimer = 0;

    public Xray() {
        super("Xray", "Highlights ores through terrain, each with its own colour.", Category.RENDER);

        color("diamond_ore", "diamond", 0x4AE8E0);
        color("ancient_debris", "ancient-debris", 0xA070C8);
        color("emerald_ore", "emerald", 0x3BE05A);
        color("gold_ore", "gold", 0xFFD84A);
        color("iron_ore", "iron", 0xE0C8A0);
        color("redstone_ore", "redstone", 0xFF3B3B);
        color("lapis_ore", "lapis", 0x3B6BFF);
        color("copper_ore", "copper", 0xE07A3C);
        color("coal_ore", "coal", 0x585858);
        color("quartz_ore", "quartz", 0xF0EAE2);
        color("", "other-ores", 0xFFB347);
    }

    private void color(String key, String name, int rgb) {
        colors.put(key, register(Setting.builder(Integer.class, name)
            .description("Colour for " + name.replace('-', ' ') + ".")
            .defaultValue(DEFAULT_ALPHA | rgb)
            .asColor()
            .build()));
    }

    @Override
    public void onEnable() {
        rescanTimer = 0;
    }

    @Override
    public void onDisable() {
        found.clear();
    }

    /** Returns the configured colour for a block, or 0 when it is not an ore. */
    private int colorOf(BlockState state) {
        if (state.isAir()) return 0;

        Identifier key = BuiltInRegistries.BLOCK.getKey(state.getBlock());
        if (key == null) return 0;
        String path = key.getPath();

        for (Map.Entry<String, Setting<Integer>> entry : colors.entrySet()) {
            String match = entry.getKey();
            if (!match.isEmpty() && path.contains(match)) return entry.getValue().get();
        }
        return path.contains("_ore") ? colors.get("").get() : 0;
    }

    @Override
    public void onTick() {
        if (mc.level == null || mc.player == null) return;
        if (rescanTimer-- > 0) return;
        rescanTimer = 40;

        found.clear();

        BlockPos center = mc.player.blockPosition();
        int r = radius.get();

        for (int x = -r; x <= r; x++) {
            for (int z = -r; z <= r; z++) {
                for (int y = -VERTICAL_HALF_RANGE; y <= VERTICAL_HALF_RANGE; y++) {
                    BlockPos pos = center.offset(x, y, z);
                    if (!mc.level.hasChunkAt(pos)) continue;

                    int color = colorOf(mc.level.getBlockState(pos));
                    if (color != 0) found.add(new Found(pos.immutable(), color));
                }
            }
        }
    }

    @Override
    public void onRenderWorld(RenderContext context) {
        if (found.isEmpty()) return;

        Vec3 cameraPos = context.cameraPos();

        for (Found entry : found) {
            AABB box = new AABB(entry.pos());

            context.poseStack().pushPose();
            context.poseStack().translate(-cameraPos.x, -cameraPos.y, -cameraPos.z);
            context.submitNodeCollector().submitCustomGeometry(context.poseStack(), EspRender.filledNoDepth(),
                (pose, buffer) -> RenderUtils.drawBox(pose, buffer, box, entry.color()));
            context.poseStack().popPose();
        }
    }
}
