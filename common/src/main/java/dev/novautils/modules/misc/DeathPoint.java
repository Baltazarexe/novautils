package dev.novautils.modules.misc;

import dev.novautils.render.RenderContext;
import dev.novautils.modules.Category;
import dev.novautils.modules.Module;
import dev.novautils.utils.EspRender;
import dev.novautils.utils.RenderUtils;
import net.minecraft.client.gui.screens.DeathScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.util.ARGB;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/** Remembers where you died and marks the spot through walls until you get there. */
public class DeathPoint extends Module {
    private Vec3 death;
    private boolean wasDead;

    public DeathPoint() {
        super("DeathPoint", "Marks where you died so you can find your stuff.", Category.MISC);
    }

    @Override
    public void onTick() {
        if (mc.player == null) return;

        boolean dead = mc.gui.screen() instanceof DeathScreen || mc.player.getHealth() <= 0;
        if (dead && !wasDead) {
            death = mc.player.position();
            mc.player.sendSystemMessage(Component.literal(String.format(
                "[NovaUtils] Died at %d, %d, %d", (int) death.x, (int) death.y, (int) death.z)));
        }
        wasDead = dead;

        if (death != null && mc.player.position().distanceToSqr(death) < 9) death = null;
    }

    @Override
    public void onRenderWorld(RenderContext context) {
        if (death == null) return;

        Vec3 cam = context.cameraPos();
        int color = ARGB.colorFromFloat(0.55f, 1f, 0.3f, 0.3f);
        AABB box = new AABB(death.x - 0.4, death.y, death.z - 0.4, death.x + 0.4, death.y + 256, death.z + 0.4);

        context.poseStack().pushPose();
        context.poseStack().translate(-cam.x, -cam.y, -cam.z);
        context.submitNodeCollector().submitCustomGeometry(context.poseStack(), EspRender.filledNoDepth(),
            (pose, buffer) -> RenderUtils.drawBox(pose, buffer, box, color));
        context.poseStack().popPose();
    }
}
