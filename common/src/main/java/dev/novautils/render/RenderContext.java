package dev.novautils.render;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.world.phys.Vec3;

/**
 * Loader-agnostic view of "we are in the middle of rendering the world, submit some geometry".
 * Fabric and NeoForge each fire their own event for this at roughly the same point in the frame
 * (translucent terrain has been drawn); the per-loader entrypoint wraps its event into one of these
 * so every module only has to know this interface, not either loader's API.
 */
public interface RenderContext {
    PoseStack poseStack();

    SubmitNodeCollector submitNodeCollector();

    Vec3 cameraPos();
}
