package dev.novautils.mixin;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import dev.novautils.modules.render.Xray;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Xray SEE_THROUGH with Embeddium: draw every face of kept blocks, and no ambient occlusion. */
@Pseudo
@Mixin(targets = "me.jellysquid.mods.sodium.client.render.chunk.compile.pipeline.BlockRenderer")
public abstract class EmbeddiumXrayRenderMixin {
    @Inject(method = "isFaceVisible", at = @At("HEAD"), cancellable = true, require = 0, remap = false)
    private void novautils$xrayFace(CallbackInfoReturnable<Boolean> cir) {
        if (Xray.forcesBright()) cir.setReturnValue(true);
    }

    @ModifyExpressionValue(method = "renderModel", require = 0, remap = false,
        at = @At(value = "FIELD", target = "Lme/jellysquid/mods/sodium/client/render/chunk/compile/pipeline/BlockRenderer;useAmbientOcclusion:Z", remap = false))
    private boolean novautils$xrayNoAo(boolean original) {
        return original && !Xray.forcesBright();
    }
}
