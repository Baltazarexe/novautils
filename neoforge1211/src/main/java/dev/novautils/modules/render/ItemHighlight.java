package dev.novautils.modules.render;

import dev.novautils.render.RenderContext;
import dev.novautils.modules.Category;
import dev.novautils.modules.Module;
import dev.novautils.settings.Setting;
import dev.novautils.utils.RenderUtils;
import dev.novautils.utils.ARGB;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/** Draws a box through walls over dropped items. */
public class ItemHighlight extends Module {
    private final Setting<Double> opacity = register(Setting.builder(Double.class, "opacity")
        .description("Opacity of the boxes.")
        .defaultValue(0.5).range(0.05, 1.0).build());

    public ItemHighlight() {
        super("ItemHighlight", "Highlights dropped items through walls.", Category.RENDER);
    }

    @Override
    public void onRenderWorld(RenderContext context) {
        if (mc.level == null) return;
        Vec3 cam = context.cameraPos();
        int color = ARGB.colorFromFloat((float) (double) opacity.get(), 0.3f, 0.9f, 1f);

        for (Entity e : mc.level.entitiesForRendering()) {
            if (!(e instanceof ItemEntity)) continue;
            AABB box = e.getBoundingBox().inflate(0.05);
            RenderUtils.world(context, (pose, buffer) -> RenderUtils.drawBox(pose, buffer, box, color));
        }
    }
}
