package dev.novautils.mixin;

import dev.novautils.modules.render.SkyFog;
import net.minecraft.client.renderer.fog.FogData;
import net.minecraft.client.renderer.fog.FogRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** SkyFog: change the fog numbers and colour right before the game uploads them. */
@Mixin(FogRenderer.class)
public abstract class FogMixin {
    @Inject(method = "updateBuffer(Lnet/minecraft/client/renderer/fog/FogData;)V", at = @At("HEAD"))
    private void novautils$fog(FogData data, CallbackInfo ci) {
        SkyFog s = SkyFog.active();
        if (s == null) return;
        float t = s.terrainScale();
        if (Float.isInfinite(t)) {
            data.renderDistanceStart = data.renderDistanceEnd = 1_000_000f;
            data.skyEnd = data.cloudEnd = 1_000_000f;
        } else if (t != 1f) {
            data.renderDistanceStart *= t;
            data.renderDistanceEnd *= t;
            data.skyEnd *= t;
            data.cloudEnd *= t;
        }
        float f = s.fluidScale();
        if (f != 1f) {
            data.environmentalStart *= f;
            data.environmentalEnd *= f;
        }
        int c = s.fogColor();
        if (c != 0) data.color.set((c >> 16 & 255) / 255f, (c >> 8 & 255) / 255f, (c & 255) / 255f, data.color.w());
    }
}
