package dev.novautils.modules.render;

import dev.novautils.render.RenderContext;
import dev.novautils.modules.Category;
import dev.novautils.modules.Module;
import dev.novautils.settings.Setting;
import dev.novautils.utils.RenderUtils;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.core.BlockPos;
import net.minecraft.util.ARGB;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.List;

/** Base for ESPs that scan terrain around the player every couple of seconds and draw boxes over the results. */
public abstract class BlockScanEsp extends Module {
    protected final Setting<Integer> radius;
    protected final Setting<Double> opacity = register(Setting.builder(Double.class, "opacity")
        .description("Opacity of the boxes.")
        .defaultValue(0.4).range(0.05, 1.0).build());

    private final List<AABB> boxes = new ArrayList<>();
    private int timer;

    protected BlockScanEsp(String name, String description, int defaultRadius, int maxRadius) {
        super(name, description, Category.RENDER);
        this.radius = register(Setting.builder(Integer.class, "radius")
            .description("Scan radius in blocks. Higher costs more when it rescans.")
            .defaultValue(defaultRadius).range(4, maxRadius).build());
    }

    /** Add boxes (world coordinates) for everything interesting near {@code center}. */
    protected abstract void scan(BlockPos center, List<AABB> out);

    protected abstract float[] rgb();

    @Override
    public void onEnable() {
        timer = 0;
    }

    @Override
    public void onDisable() {
        boxes.clear();
    }

    @Override
    public void onTick() {
        if (mc.level == null || mc.player == null) return;
        if (timer-- > 0) return;
        timer = 40;

        boxes.clear();
        scan(mc.player.blockPosition(), boxes);
    }

    @Override
    public void onRenderWorld(RenderContext context) {
        if (boxes.isEmpty()) return;

        Vec3 cam = context.cameraPos();
        float[] c = rgb();
        int color = ARGB.colorFromFloat((float) (double) opacity.get(), c[0], c[1], c[2]);

        for (AABB box : boxes) {
            RenderUtils.world(context, (pose, buffer) -> RenderUtils.drawBox(pose, buffer, box, color));
        }
    }
}
