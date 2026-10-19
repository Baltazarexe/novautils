package dev.novautils.mixin;

import dev.novautils.modules.render.Esp;
import net.minecraft.client.renderer.PostPass;
import net.minecraft.client.renderer.UniformValue;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Esp GLOW sliders: the outline blur's uniform block ("GlowConfig", from our own entity_outline
 * post effect) gets its Spread and Strength from the module settings when the pass is built.
 */
@Mixin(PostPass.class)
public abstract class PostPassMixin {
    @ModifyVariable(method = "<init>", at = @At("HEAD"), argsOnly = true)
    private static Map<String, List<UniformValue>> novautils$glow(Map<String, List<UniformValue>> uniforms) {
        List<UniformValue> glow = uniforms.get("GlowConfig");
        if (glow == null || glow.isEmpty() || !dev.novautils.NovaUtils.modulesReady) return uniforms;
        Map<String, List<UniformValue>> copy = new HashMap<>(uniforms);
        copy.put("GlowConfig", List.of(glow.get(0), new UniformValue.FloatUniform(Esp.glowSpread()), new UniformValue.FloatUniform(Esp.glowStrength())));
        return copy;
    }
}
