package dev.novautils.mixin;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import dev.novautils.modules.ModuleManager;
import dev.novautils.modules.render.Fullbright;
import net.minecraft.client.renderer.LightTexture;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;

/** Fullbright (and Xray): the lightmap is built with a huge gamma and no darkness effect. */
@Mixin(LightTexture.class)
public abstract class LightmapMixin {
    @Unique
    private static boolean novautils$bright() {
        if (!dev.novautils.NovaUtils.modulesReady) return false;
        return ModuleManager.get().get(Fullbright.class).isEnabled() || dev.novautils.modules.render.Xray.forcesBright();
    }

    /** First OptionInstance.get() in updateLightTexture: the darkness effect scale. */
    @ModifyExpressionValue(method = "updateLightTexture", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/OptionInstance;get()Ljava/lang/Object;", ordinal = 0))
    private Object novautils$noDarkness(Object value) {
        return novautils$bright() ? (Object) Double.valueOf(0.0) : value;
    }

    /** Second one: gamma. */
    @ModifyExpressionValue(method = "updateLightTexture", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/OptionInstance;get()Ljava/lang/Object;", ordinal = 1))
    private Object novautils$gamma(Object value) {
        return novautils$bright() ? (Object) Double.valueOf(16.0) : value;
    }
}
