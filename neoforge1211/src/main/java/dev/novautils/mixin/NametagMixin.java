package dev.novautils.mixin;

import dev.novautils.modules.ModuleManager;
import dev.novautils.modules.render.Nametags;
import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Hides the vanilla name tag on mobs and players while Nametags draws its own. */
@Mixin(LivingEntityRenderer.class)
public abstract class NametagMixin {
    @Inject(method = "shouldShowName(Lnet/minecraft/world/entity/LivingEntity;)Z", at = @At("HEAD"), cancellable = true)
    private void novautils$hide(LivingEntity entity, CallbackInfoReturnable<Boolean> cir) {
        if (!dev.novautils.NovaUtils.modulesReady) return;
        ModuleManager m = ModuleManager.get();
        if (m.isActive(Nametags.class) && m.get(Nametags.class).replaces(entity)) cir.setReturnValue(false);
    }
}
