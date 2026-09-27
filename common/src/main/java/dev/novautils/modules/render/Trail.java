package dev.novautils.modules.render;

import dev.novautils.render.RenderContext;
import dev.novautils.modules.Category;
import dev.novautils.modules.Module;
import dev.novautils.settings.Setting;
import dev.novautils.utils.RenderUtils;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.util.ARGB;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.List;

/** Draws a line along the path you just walked. */
public class Trail extends Module {
    private final Setting<Integer> length = register(Setting.builder(Integer.class, "length")
        .description("How many points of your path to keep.").defaultValue(200).range(20, 1000).build());

    private final List<Vec3> points = new ArrayList<>();

    public Trail() {
        super("Trail", "Draws the path you walked.", Category.RENDER);
    }

    @Override
    public void onDisable() {
        points.clear();
    }

    @Override
    public void onTick() {
        if (mc.player == null) return;
        Vec3 pos = mc.player.position().add(0, 0.1, 0);
        if (points.isEmpty() || points.get(points.size() - 1).distanceToSqr(pos) > 0.09) points.add(pos);
        while (points.size() > length.get()) points.remove(0);
    }

    @Override
    public void onRenderWorld(RenderContext context) {
        if (points.size() < 2) return;
        Vec3 cam = context.cameraPos();
        int color = ARGB.colorFromFloat(0.8f, 0.3f, 0.8f, 1f);

        context.poseStack().pushPose();
        context.poseStack().translate(-cam.x, -cam.y, -cam.z);
        context.submitNodeCollector().submitCustomGeometry(context.poseStack(), dev.novautils.utils.EspRender.filledNoDepth(), (pose, buffer) -> {
            for (int i = 1; i < points.size(); i++) {
                RenderUtils.drawRibbon(pose, buffer, points.get(i - 1), points.get(i), 0.03, color);
            }
        });
        context.poseStack().popPose();
    }
}
