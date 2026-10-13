package dev.novautils.mixin;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import dev.novautils.modules.ModuleManager;
import dev.novautils.modules.movement.NoSlow;
import dev.novautils.utils.Rotations;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.entity.MoverType;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(LocalPlayer.class)
public abstract class LocalPlayerMixin {
    /** Movement modules rewrite the vector inside vanilla physics (Meteor PlayerMoveEvent). */
    @WrapMethod(method = "move")
    private void novautils$move(MoverType type, Vec3 movement, Operation<Void> original) {
        if (type == MoverType.SELF && dev.novautils.NovaUtils.modulesReady) movement = ModuleManager.get().onPlayerMove(movement);
        original.call(type, movement);
    }

    /** Freecam: your body ignores the movement keys (they move the camera instead). */
    @Inject(method = "aiStep", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/player/Input;tick(ZF)V", shift = At.Shift.AFTER))
    private void novautils$freecamInput(CallbackInfo ci) {
        if (dev.novautils.modules.render.Freecam.active() == null) return;
        var in = ((LocalPlayer) (Object) this).input;
        in.leftImpulse = 0;
        in.forwardImpulse = 0;
        in.up = in.down = in.left = in.right = in.jumping = in.shiftKeyDown = false;
    }

    @Inject(method = "sendPosition", at = @At("HEAD"))
    private void novautils$rotationsPre(CallbackInfo ci) {
        Rotations.preSend((LocalPlayer) (Object) this);
    }

    @Inject(method = "sendPosition", at = @At("RETURN"))
    private void novautils$rotationsPost(CallbackInfo ci) {
        Rotations.postSend((LocalPlayer) (Object) this);
    }

    /** NoSlow: full speed while eating, drinking, blocking or drawing a bow. */
    @ModifyExpressionValue(method = "aiStep", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/player/LocalPlayer;isUsingItem()Z"))
    private boolean novautils$noSlow(boolean using) {
        if (!using || !dev.novautils.NovaUtils.modulesReady) return using;
        return !ModuleManager.get().isActive(NoSlow.class);
    }
}
