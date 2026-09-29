package dev.novautils.mixin;

import dev.novautils.modules.ModuleManager;
import dev.novautils.modules.render.Nametags;
import net.minecraft.client.renderer.entity.player.AvatarRenderer;
import net.minecraft.world.entity.Avatar;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Players' renderer overrides shouldShowName, so it needs its own hook (see NametagMixin). */
@Mixin(AvatarRenderer.class)
public abstract class AvatarNametagMixin {
    @Inject(method = "shouldShowName(Lnet/minecraft/world/entity/Avatar;D)Z", at = @At("HEAD"), cancellable = true)
    private void novautils$hide(Avatar entity, double distSq, CallbackInfoReturnable<Boolean> cir) {
        if (!dev.novautils.NovaUtils.modulesReady) return;
        ModuleManager m = ModuleManager.get();
        if (m.isActive(Nametags.class) && m.get(Nametags.class).replaces(entity)) cir.setReturnValue(false);
    }
}
