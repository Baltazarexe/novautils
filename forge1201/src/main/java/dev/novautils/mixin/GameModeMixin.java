package dev.novautils.mixin;

import dev.novautils.utils.MiningGuard;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.MultiPlayerGameMode;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Lets module-driven mining survive vanilla's "not holding attack, cancel mining" reset. */
@Mixin(MultiPlayerGameMode.class)
public abstract class GameModeMixin {
    @Inject(method = "attack", at = @At("HEAD"), cancellable = true)
    private void novautils$criticals(net.minecraft.world.entity.player.Player player, net.minecraft.world.entity.Entity target, CallbackInfo ci) {
        if (!dev.novautils.NovaUtils.modulesReady) return;
        var m = dev.novautils.modules.ModuleManager.get();
        if (m.isActive(dev.novautils.modules.combat.Criticals.class)
            && m.get(dev.novautils.modules.combat.Criticals.class).beforeAttack(target)) {
            ci.cancel();
        }
    }

    @Inject(method = "stopDestroyBlock", at = @At("HEAD"), cancellable = true)
    private void novautils$keepMining(CallbackInfo ci) {
        if (MiningGuard.active() && !Minecraft.getInstance().options.keyAttack.isDown()) ci.cancel();
    }

    /** PacketMine takes over the click: it mines the block in the background instead. */
    @Inject(method = "startDestroyBlock", at = @At("HEAD"), cancellable = true)
    private void novautils$packetMine(net.minecraft.core.BlockPos pos, net.minecraft.core.Direction dir, CallbackInfoReturnable<Boolean> cir) {
        if (!dev.novautils.NovaUtils.modulesReady) return;
        var m = dev.novautils.modules.ModuleManager.get();
        if (m.isActive(dev.novautils.modules.player.PacketMine.class)
            && m.get(dev.novautils.modules.player.PacketMine.class).onClick(pos, dir)) {
            cir.setReturnValue(true);
        }
    }
}
