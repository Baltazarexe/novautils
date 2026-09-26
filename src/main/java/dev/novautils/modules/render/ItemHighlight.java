package dev.novautils.modules.render;

import dev.novautils.modules.Category;
import dev.novautils.modules.Module;
import dev.novautils.settings.Setting;
import dev.novautils.utils.RenderUtils;
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderContext;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.util.ARGB;
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
    public void onRenderWorld(LevelRenderContext context) {
        if (mc.level == null) return;
        Vec3 cam = context.levelState().cameraRenderState.pos;
        int color = ARGB.colorFromFloat((float) (double) opacity.get(), 0.3f, 0.9f, 1f);

        for (Entity e : mc.level.entitiesForRendering()) {
            if (!(e instanceof ItemEntity)) continue;
            AABB box = e.getBoundingBox().inflate(0.05);
            context.poseStack().pushPose();
            context.poseStack().translate(-cam.x, -cam.y, -cam.z);
            context.submitNodeCollector().submitCustomGeometry(context.poseStack(), dev.novautils.utils.EspRender.filledNoDepth(),
                (pose, buffer) -> RenderUtils.drawBox(pose, buffer, box, color));
            context.poseStack().popPose();
        }
    }
}
