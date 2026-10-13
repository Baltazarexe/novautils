package dev.novautils.fabric;

import com.mojang.blaze3d.vertex.PoseStack;
import dev.novautils.render.RenderContext;
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderContext;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.world.phys.Vec3;

/** Wraps Fabric's world-render event into our loader-agnostic {@link RenderContext}. */
public record FabricRenderContext(LevelRenderContext delegate) implements RenderContext {
    @Override
    public PoseStack poseStack() {
        return delegate.poseStack();
    }

    @Override
    public SubmitNodeCollector submitNodeCollector() {
        return delegate.submitNodeCollector();
    }

    @Override
    public Vec3 cameraPos() {
        return delegate.levelState().cameraRenderState.pos;
    }
}
