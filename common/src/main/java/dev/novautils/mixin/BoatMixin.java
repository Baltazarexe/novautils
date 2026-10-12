package dev.novautils.mixin;

import dev.novautils.modules.ModuleManager;
import dev.novautils.modules.movement.BoatFly;
import net.minecraft.world.entity.vehicle.boat.AbstractBoat;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** BoatFly: right after vanilla steered the boat, replace its velocity before it moves. */
@Mixin(AbstractBoat.class)
public abstract class BoatMixin {
    @Inject(method = "controlBoat", at = @At("RETURN"))
    private void novautils$boatFly(CallbackInfo ci) {
        if (!dev.novautils.NovaUtils.modulesReady || !ModuleManager.get().isActive(BoatFly.class)) return;
        ModuleManager.get().get(BoatFly.class).steer((AbstractBoat) (Object) this);
    }
}
