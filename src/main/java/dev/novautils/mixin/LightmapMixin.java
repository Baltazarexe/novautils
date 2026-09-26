package dev.novautils.mixin;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.renderpearl.api.textures.GpuTexture;
import dev.novautils.modules.ModuleManager;
import dev.novautils.modules.render.Fullbright;
import net.minecraft.client.renderer.Lightmap;
import net.minecraft.client.renderer.state.LightmapRenderState;
import org.joml.Vector4f;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** When Fullbright is active, replaces the lightmap texture with solid white instead of letting it render normally. */
@Mixin(Lightmap.class)
public abstract class LightmapMixin {
    @Shadow
    @Final
    private GpuTexture texture;

    @Inject(method = "render", at = @At("HEAD"), cancellable = true)
    private void novautils$fullbright(LightmapRenderState renderState, CallbackInfo ci) {
        if (ModuleManager.get().isActive(Fullbright.class)) {
            RenderSystem.getDevice().createCommandEncoder().clearColorTexture(texture, new Vector4f(1));
            ci.cancel();
        }
    }
}
