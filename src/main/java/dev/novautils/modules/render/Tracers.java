package dev.novautils.modules.render;

import dev.novautils.modules.Category;
import dev.novautils.modules.Module;
import dev.novautils.settings.Setting;
import dev.novautils.utils.RenderUtils;
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderContext;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.util.ARGB;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;

/** Draws a line from your crosshair to nearby entities so you always know where they are. */
public class Tracers extends Module {
    private final Setting<Boolean> players = register(Setting.builder(Boolean.class, "players")
        .description("Trace to players.").defaultValue(true).build());
    private final Setting<Boolean> hostiles = register(Setting.builder(Boolean.class, "hostile-mobs")
        .description("Trace to hostile mobs.").defaultValue(true).build());
    private final Setting<Boolean> passives = register(Setting.builder(Boolean.class, "passive-mobs")
        .description("Trace to other mobs.").defaultValue(false).build());
    private final Setting<Double> range = register(Setting.builder(Double.class, "range")
        .description("Max distance to trace.").defaultValue(96.0).range(8.0, 256.0).build());

    public Tracers() {
        super("Tracers", "Lines from your crosshair to nearby entities.", Category.RENDER);
    }

    @Override
    public void onRenderWorld(LevelRenderContext context) {
        if (mc.level == null || mc.player == null) return;

        Vec3 cam = context.levelState().cameraRenderState.pos;
        Vec3 start = cam.add(mc.player.getLookAngle().scale(0.8));
        double rangeSq = range.get() * range.get();

        for (Entity e : mc.level.entitiesForRendering()) {
            if (e == mc.player || !(e instanceof LivingEntity living) || !living.isAlive()) continue;
            if (mc.player.distanceToSqr(e) > rangeSq) continue;

            int color;
            if (e instanceof Player) {
                if (!players.get()) continue;
                color = ARGB.colorFromFloat(0.9f, 1f, 0.2f, 0.2f);
            } else if (e instanceof Monster) {
                if (!hostiles.get()) continue;
                color = ARGB.colorFromFloat(0.9f, 1f, 0.55f, 0f);
            } else {
                if (!passives.get()) continue;
                color = ARGB.colorFromFloat(0.9f, 0.2f, 1f, 0.3f);
            }

            Vec3 end = e.getBoundingBox().getCenter();
            context.poseStack().pushPose();
            context.poseStack().translate(-cam.x, -cam.y, -cam.z);
            context.submitNodeCollector().submitCustomGeometry(context.poseStack(), RenderTypes.debugFilledBox(),
                (pose, buffer) -> RenderUtils.drawRibbon(pose, buffer, start, end, 0.012, color));
            context.poseStack().popPose();
        }
    }
}
