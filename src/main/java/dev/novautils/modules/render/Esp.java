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
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/** Draws a translucent box through walls over nearby living entities. */
public class Esp extends Module {
    private final Setting<Boolean> players = register(Setting.builder(Boolean.class, "players")
        .description("Highlight other players.")
        .defaultValue(true)
        .build());

    private final Setting<Boolean> hostiles = register(Setting.builder(Boolean.class, "hostile-mobs")
        .description("Highlight hostile mobs.")
        .defaultValue(true)
        .build());

    private final Setting<Boolean> passives = register(Setting.builder(Boolean.class, "passive-mobs")
        .description("Highlight passive and neutral mobs.")
        .defaultValue(false)
        .build());

    private final Setting<Double> opacity = register(Setting.builder(Double.class, "opacity")
        .description("Opacity of the highlight box.")
        .defaultValue(0.35)
        .range(0.05, 1.0)
        .build());

    public Esp() {
        super("Esp", "Highlights entities through walls.", Category.RENDER);
    }

    @Override
    public void onRenderWorld(LevelRenderContext context) {
        if (mc.level == null || mc.player == null) return;

        Vec3 cameraPos = context.levelState().cameraRenderState.pos;
        for (Entity entity : mc.level.entitiesForRendering()) {
            if (entity == mc.player) continue;
            if (!(entity instanceof LivingEntity living) || !living.isAlive()) continue;

            float alpha = (float) (double) opacity.get();
            int color;

            if (entity instanceof Player) {
                if (!players.get()) continue;
                color = ARGB.colorFromFloat(alpha, 1f, 0.2f, 0.2f);
            } else if (entity instanceof Monster) {
                if (!hostiles.get()) continue;
                color = ARGB.colorFromFloat(alpha, 1f, 0.55f, 0f);
            } else {
                if (!passives.get()) continue;
                color = ARGB.colorFromFloat(alpha, 0.2f, 1f, 0.3f);
            }

            AABB box = living.getBoundingBox();

            context.poseStack().pushPose();
            context.poseStack().translate(-cameraPos.x, -cameraPos.y, -cameraPos.z);

            int finalColor = color;
            context.submitNodeCollector().submitCustomGeometry(context.poseStack(), RenderTypes.debugFilledBox(),
                (pose, buffer) -> RenderUtils.drawFilledBox(pose, buffer, box, finalColor));

            context.poseStack().popPose();
        }
    }
}
