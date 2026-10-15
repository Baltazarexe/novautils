package dev.novautils.mixin;

import dev.novautils.modules.ModuleManager;
import dev.novautils.modules.render.Nametags;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Belt and braces for NametagMixin: the loader's RenderNameTagEvent (or another mod answering it)
 * can force the vanilla tag on, so also cancel the drawing itself while our tag is shown.
 */
@Mixin(EntityRenderer.class)
public abstract class NametagHideMixin {
    @Inject(method = "renderNameTag", at = @At("HEAD"), cancellable = true)
    private void novautils$hideTag(Entity entity, net.minecraft.network.chat.Component text, com.mojang.blaze3d.vertex.PoseStack pose, net.minecraft.client.renderer.MultiBufferSource buffers, int light, float partial, CallbackInfo ci) {
        if (!dev.novautils.NovaUtils.modulesReady) return;
        ModuleManager m = ModuleManager.get();
        if (m.isActive(Nametags.class) && m.get(Nametags.class).replaces(entity)) ci.cancel();
    }
}
