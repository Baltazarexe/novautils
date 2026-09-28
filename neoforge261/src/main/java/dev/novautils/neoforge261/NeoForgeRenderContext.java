package dev.novautils.neoforge261;

import com.mojang.blaze3d.vertex.PoseStack;
import dev.novautils.render.RenderContext;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.client.event.SubmitCustomGeometryEvent;

/** Wraps NeoForge's world-geometry-submission event into our loader-agnostic {@link RenderContext}. */
public record NeoForgeRenderContext(SubmitCustomGeometryEvent event) implements RenderContext {
    @Override
    public PoseStack poseStack() {
        return event.getPoseStack();
    }

    @Override
    public SubmitNodeCollector submitNodeCollector() {
        return event.getSubmitNodeCollector();
    }

    @Override
    public Vec3 cameraPos() {
        return event.getLevelRenderState().cameraRenderState.pos;
    }
}
