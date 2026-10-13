package dev.novautils.mixin;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import dev.novautils.modules.render.Xray;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.block.ModelBlockRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Xray SEE_THROUGH, vanilla renderer: the kept blocks sit inside hidden stone where the real light
 * is 0, so give them full light and no corner shading (what Meteor does).
 */
@Mixin(LevelRenderer.class)
public abstract class XrayLightMixin {
    @Inject(method = "getLightColor(Lnet/minecraft/world/level/BlockAndTintGetter;Lnet/minecraft/world/level/block/state/BlockState;Lnet/minecraft/core/BlockPos;)I",
        at = @At("HEAD"), cancellable = true)
    private static void novautils$xrayLight(CallbackInfoReturnable<Integer> cir) {
        if (Xray.forcesBright()) cir.setReturnValue(LightTexture.FULL_BRIGHT);
    }
}
