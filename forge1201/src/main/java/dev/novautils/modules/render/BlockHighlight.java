package dev.novautils.modules.render;

import dev.novautils.modules.Category;
import dev.novautils.modules.Module;
import dev.novautils.render.RenderContext;
import dev.novautils.settings.Setting;
import dev.novautils.utils.RenderUtils;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;

/**
 * A coloured selection on the block you're looking at, following its real shape (stairs, slabs,
 * fences...): thin outline or a soft filled box, in one colour or cycling through the rainbow.
 * Draws over the vanilla black outline.
 */
public class BlockHighlight extends Module {
    public enum Style { OUTLINE, FILLED }

    private final Setting<Style> style = register(Setting.builder(Style.class, "style")
        .description("OUTLINE: just the edges. FILLED: edges plus a soft fill.").defaultValue(Style.FILLED).build());

    private final Setting<Integer> color = register(Setting.builder(Integer.class, "color")
        .description("Highlight colour (its alpha is the fill strength).").defaultValue(0x608B9CFF).asColor().build());

    private final Setting<Boolean> rainbow = register(Setting.builder(Boolean.class, "rainbow")
        .description("Cycle the colour through the rainbow.").defaultValue(false).build());

    private final Setting<Integer> thickness = register(Setting.builder(Integer.class, "thickness")
        .description("Edge thickness (outline style).").defaultValue(2).range(1, 5)
        .visible(() -> style.get() == Style.OUTLINE).build());

    public BlockHighlight() {
        super("BlockHighlight", "A coloured selection on the block you look at.", Category.RENDER);
    }

    @Override
    public void onRenderWorld(RenderContext context) {
        if (mc.level == null || !(mc.hitResult instanceof BlockHitResult hit) || hit.getType() != HitResult.Type.BLOCK) return;
        BlockPos pos = hit.getBlockPos();
        BlockState state = mc.level.getBlockState(pos);
        if (state.isAir()) return;

        int c = color.get();
        if (rainbow.get()) {
            float hue = (System.currentTimeMillis() % 4000) / 4000f;
            c = (c & 0xFF000000) | (java.awt.Color.HSBtoRGB(hue, 0.6f, 1f) & 0xFFFFFF);
        }
        final int fin = c;
        var shape = state.getShape(mc.level, pos);
        var boxes = shape.isEmpty() ? java.util.List.of(new AABB(0, 0, 0, 1, 1, 1)) : shape.toAabbs();
        double w = 0.004 * thickness.get();
        boolean filled = style.get() == Style.FILLED;
        for (AABB b : boxes) {
            AABB box = b.move(pos).inflate(0.002);
            RenderUtils.world(context, (pose, buffer) -> {
                if (filled) RenderUtils.drawBox(pose, buffer, box, fin);
                else RenderUtils.drawEdges(pose, buffer, box, fin | 0xFF000000, w);
            });
        }
    }
}
