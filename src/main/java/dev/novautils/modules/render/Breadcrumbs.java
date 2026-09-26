package dev.novautils.modules.render;

import dev.novautils.modules.Category;
import dev.novautils.modules.Module;
import dev.novautils.settings.Setting;
import dev.novautils.utils.RenderUtils;
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderContext;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.util.ARGB;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.List;

/** Drops a small marker every few blocks you walk so you can find your way back. */
public class Breadcrumbs extends Module {
    private final Setting<Integer> spacing = register(Setting.builder(Integer.class, "spacing")
        .description("Blocks between markers.").defaultValue(3).range(1, 20).build());
    private final Setting<Integer> maxMarkers = register(Setting.builder(Integer.class, "max-markers")
        .description("Markers kept before the oldest disappear.").defaultValue(150).range(10, 1000).build());

    private final List<Vec3> markers = new ArrayList<>();

    public Breadcrumbs() {
        super("Breadcrumbs", "Leaves markers along your path.", Category.RENDER);
    }

    @Override
    public void onDisable() {
        markers.clear();
    }

    @Override
    public void onTick() {
        if (mc.player == null) return;
        Vec3 pos = mc.player.position();
        double s = spacing.get();
        if (markers.isEmpty() || markers.get(markers.size() - 1).distanceToSqr(pos) >= s * s) markers.add(pos);
        while (markers.size() > maxMarkers.get()) markers.remove(0);
    }

    @Override
    public void onRenderWorld(LevelRenderContext context) {
        if (markers.isEmpty()) return;
        Vec3 cam = context.levelState().cameraRenderState.pos;
        int color = ARGB.colorFromFloat(0.7f, 1f, 0.6f, 0.1f);

        for (Vec3 m : markers) {
            AABB box = new AABB(m.x - 0.12, m.y, m.z - 0.12, m.x + 0.12, m.y + 0.25, m.z + 0.12);
            context.poseStack().pushPose();
            context.poseStack().translate(-cam.x, -cam.y, -cam.z);
            context.submitNodeCollector().submitCustomGeometry(context.poseStack(), dev.novautils.utils.EspRender.filledNoDepth(),
                (pose, buffer) -> RenderUtils.drawBox(pose, buffer, box, color));
            context.poseStack().popPose();
        }
    }
}
