package dev.novautils.mixin;

import dev.novautils.modules.ModuleManager;
import dev.novautils.modules.render.Nametags;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.state.EntityRenderState;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Belt and braces for NametagMixin / AvatarNametagMixin: whatever decided to show the vanilla tag
 * (another mod, a server team setting), drop it from the render state while our tag is drawn.
 */
@Mixin(EntityRenderer.class)
public abstract class NametagHideMixin {
    @Inject(method = "extractRenderState(Lnet/minecraft/world/entity/Entity;Lnet/minecraft/client/renderer/entity/state/EntityRenderState;F)V", at = @At("TAIL"))
    private void novautils$hideTag(Entity entity, EntityRenderState state, float partial, CallbackInfo ci) {
        if (!dev.novautils.NovaUtils.modulesReady) return;
        ModuleManager m = ModuleManager.get();
        if (m.isActive(Nametags.class) && m.get(Nametags.class).replaces(entity)) {
            state.nameTag = null;
            state.scoreText = null;
        }
    }
}
