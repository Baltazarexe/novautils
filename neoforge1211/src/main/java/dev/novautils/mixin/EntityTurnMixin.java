package dev.novautils.mixin;

import dev.novautils.modules.render.Freecam;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Freecam: mouse movement turns the free camera, not your body. */
@Mixin(Entity.class)
public abstract class EntityTurnMixin {
    @Inject(method = "turn", at = @At("HEAD"), cancellable = true)
    private void novautils$freecamTurn(double dx, double dy, CallbackInfo ci) {
        if ((Object) this != Minecraft.getInstance().player) return;
        Freecam fc = Freecam.active();
        if (fc == null) return;
        fc.turn(dx, dy);
        ci.cancel();
    }
}
