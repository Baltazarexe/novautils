package dev.novautils.mixin;

import dev.novautils.modules.ModuleManager;
import dev.novautils.modules.movement.SafeWalk;
import dev.novautils.modules.movement.Scaffold;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(Player.class)
public abstract class PlayerMixin {

    /**
     * SafeWalk: vanilla already stops a sneaking player at block edges; saying "yes, sneaking" to
     * that one check gives the same edge clip at full walking speed, without actually crouching.
     */
    @Inject(method = "isStayingOnGroundSurface", at = @At("HEAD"), cancellable = true)
    private void novautils$safeWalk(CallbackInfoReturnable<Boolean> cir) {
        if (!dev.novautils.NovaUtils.modulesReady) return;
        if ((Object) this != Minecraft.getInstance().player) return;
        ModuleManager m = ModuleManager.get();
        if (m.isActive(SafeWalk.class) || (m.isActive(Scaffold.class) && m.get(Scaffold.class).safeWalk())) {
            cir.setReturnValue(true);
        }
    }
}
