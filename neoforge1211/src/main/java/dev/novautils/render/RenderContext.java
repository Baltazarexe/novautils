package dev.novautils.render;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.world.phys.Vec3;

/**
 * "We are rendering the world now, draw something": Forge's RenderLevelStageEvent wrapped up so
 * modules don't depend on it. The pose stack is camera-relative (world coordinates minus
 * {@link #cameraPos()}), which RenderUtils.world takes care of.
 */
public interface RenderContext {
    PoseStack poseStack();

    Vec3 cameraPos();

    float partialTick();
}
