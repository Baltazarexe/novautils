package dev.novautils.mixin;

import dev.novautils.modules.ModuleManager;
import dev.novautils.modules.movement.Velocity;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.network.protocol.game.ClientboundEntityEventPacket;
import net.minecraft.network.protocol.game.ClientboundExplodePacket;
import net.minecraft.network.protocol.game.ClientboundSetEntityMotionPacket;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Forwards a few incoming packets to modules. Each hook sits right after vanilla's "hop onto the
 * client thread" call, so modules run on the game thread like everything else.
 */
@Mixin(ClientPacketListener.class)
public abstract class ClientPacketListenerMixin {
    private static final String ENSURE = "Lnet/minecraft/network/protocol/PacketUtils;ensureRunningOnSameThread(Lnet/minecraft/network/protocol/Packet;Lnet/minecraft/network/PacketListener;Lnet/minecraft/network/PacketProcessor;)V";

    @Inject(method = "handleSetEntityMotion", at = @At(value = "INVOKE", target = ENSURE, shift = At.Shift.AFTER), cancellable = true)
    private void novautils$motion(ClientboundSetEntityMotionPacket packet, CallbackInfo ci) {
        if (!dev.novautils.NovaUtils.modulesReady) return;
        if (ModuleManager.get().onReceivePacket(packet)) ci.cancel();
    }

    @Inject(method = "handleEntityEvent", at = @At(value = "INVOKE", target = ENSURE, shift = At.Shift.AFTER), cancellable = true)
    private void novautils$entityEvent(ClientboundEntityEventPacket packet, CallbackInfo ci) {
        if (!dev.novautils.NovaUtils.modulesReady) return;
        if (ModuleManager.get().onReceivePacket(packet)) ci.cancel();
    }

    @Inject(method = "handleExplosion", at = @At(value = "INVOKE", target = ENSURE, shift = At.Shift.AFTER))
    private void novautils$explosionPre(ClientboundExplodePacket packet, CallbackInfo ci) {
        if (!dev.novautils.NovaUtils.modulesReady) return;
        Velocity.beforeExplosion();
    }

    @Inject(method = "handleExplosion", at = @At("RETURN"))
    private void novautils$explosionPost(ClientboundExplodePacket packet, CallbackInfo ci) {
        if (!dev.novautils.NovaUtils.modulesReady) return;
        Velocity.afterExplosion();
    }
}
