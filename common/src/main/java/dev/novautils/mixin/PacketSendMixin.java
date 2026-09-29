package dev.novautils.mixin;

import dev.novautils.modules.ModuleManager;
import net.minecraft.client.multiplayer.ClientCommonPacketListenerImpl;
import net.minecraft.network.protocol.Packet;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Every packet the client sends in game passes through here; modules may edit or drop it. */
@Mixin(ClientCommonPacketListenerImpl.class)
public abstract class PacketSendMixin {
    @Inject(method = "send(Lnet/minecraft/network/protocol/Packet;)V", at = @At("HEAD"), cancellable = true)
    private void novautils$send(Packet<?> packet, CallbackInfo ci) {
        if (!dev.novautils.NovaUtils.modulesReady) return;
        if (ModuleManager.get().onSendPacket(packet)) ci.cancel();
    }
}
