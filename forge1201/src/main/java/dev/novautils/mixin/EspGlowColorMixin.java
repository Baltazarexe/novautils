package dev.novautils.mixin;

import dev.novautils.modules.render.Esp;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Esp GLOW: the outline takes the exact ESP colour instead of one of the 16 team colours. */
@Mixin(Entity.class)
public abstract class EspGlowColorMixin {
    @Inject(method = "getTeamColor", at = @At("HEAD"), cancellable = true)
    private void novautils$glowColor(CallbackInfoReturnable<Integer> cir) {
        if (!dev.novautils.NovaUtils.modulesReady) return;
        int c = Esp.glowColor((Entity) (Object) this);
        if (c != 0) cir.setReturnValue(c & 0xFFFFFF);
    }
}
