package dev.novautils.mixin;

import com.mojang.blaze3d.shaders.Uniform;
import dev.novautils.modules.render.Esp;
import net.minecraft.client.renderer.EffectInstance;
import net.minecraft.client.renderer.PostPass;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Esp GLOW sliders: Spread and Strength go to the outline blur shader before every draw. */
@Mixin(PostPass.class)
public abstract class PostPassMixin {
    @Shadow
    @org.spongepowered.asm.mixin.Final
    private EffectInstance effect;

    @Inject(method = "process(F)V", at = @At("HEAD"))
    private void novautils$glow(float time, CallbackInfo ci) {
        if (!dev.novautils.NovaUtils.modulesReady || !effect.getName().endsWith("entity_outline_box_blur")) return;
        Uniform spread = effect.getUniform("Spread");
        if (spread != null) spread.set(Esp.glowSpread());
        Uniform strength = effect.getUniform("Strength");
        if (strength != null) strength.set(Esp.glowStrength());
    }
}
