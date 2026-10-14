package dev.novautils.mixin;

import dev.novautils.modules.render.Xray;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArg;

/** Xray SEE_THROUGH with Sodium: no ambient-occlusion shadows on the blocks that stay visible. */
@Pseudo
@Mixin(targets = "net.caffeinemc.mods.sodium.client.render.chunk.compile.pipeline.BlockRenderer")
public abstract class SodiumXrayAoMixin {
    @ModifyArg(method = "renderModel", require = 0, at = @At(value = "INVOKE",
        target = "Lnet/caffeinemc/mods/sodium/client/render/chunk/compile/pipeline/BlockRenderer;prepareAoInfo(Z)V"))
    private boolean novautils$xrayNoAo(boolean useAo) {
        return useAo && !Xray.forcesBright();
    }
}
