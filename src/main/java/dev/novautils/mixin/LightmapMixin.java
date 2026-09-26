package dev.novautils.mixin;

import dev.novautils.modules.ModuleManager;
import dev.novautils.modules.render.Fullbright;
import net.minecraft.client.renderer.Lightmap;
import net.minecraft.client.renderer.LightmapRenderStateExtractor;
import net.minecraft.client.renderer.state.LightmapRenderState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Fullbright. Rather than cancelling the lightmap render and clearing its texture (which leaves the
 * uniform buffer stale and paints coloured garbage over the world), this raises the values vanilla
 * is about to upload and lets it render normally.
 */
@Mixin(Lightmap.class)
public abstract class LightmapMixin {
    @Unique
    private static boolean novautils$wasActive;

    @Inject(method = "render", at = @At("HEAD"))
    private void novautils$fullbright(LightmapRenderState state, CallbackInfo ci) {
        Fullbright fullbright = ModuleManager.get().get(Fullbright.class);
        boolean active = fullbright.isEnabled();

        // Force one more upload on the frame it switches, otherwise the old lightmap sticks around.
        if (active != novautils$wasActive) {
            state.needsUpdate = true;
            novautils$wasActive = active;
        }
        if (!active) return;

        state.needsUpdate = true;
        state.darknessEffectScale = 0f;
        state.bossOverlayWorldDarkening = 0f;

        if (fullbright.useNightVision()) {
            // Vanilla's own bright path, so the tints stay consistent.
            state.nightVisionEffectIntensity = 1f;
            state.nightVisionColor = LightmapRenderStateExtractor.WHITE;
            return;
        }

        // Raising the factors alone tints the world (block light is warm, sky light is blue-ish),
        // so neutralise the tints as well. Factors here are not 0-1: vanilla feeds ~1.4 for block
        // light and scales sky light, so stay in that range instead of clamping to 1.
        state.blockLightTint = LightmapRenderStateExtractor.WHITE;
        state.skyLightColor = LightmapRenderStateExtractor.WHITE;
        state.ambientColor = LightmapRenderStateExtractor.WHITE;
        state.blockFactor = 1.4f;
        state.skyFactor = 1.4f;
        state.brightness = 1f;
    }
}
