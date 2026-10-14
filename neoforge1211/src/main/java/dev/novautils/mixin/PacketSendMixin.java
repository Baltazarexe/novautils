package dev.novautils.mixin;

import dev.novautils.modules.ModuleManager;
import net.minecraft.network.Connection;
import net.minecraft.network.PacketSendListener;
import net.minecraft.network.protocol.Packet;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Every packet the client sends passes through here; modules may edit or drop it. */
@Mixin(Connection.class)
public abstract class PacketSendMixin {
    @Inject(method = "send(Lnet/minecraft/network/protocol/Packet;Lnet/minecraft/network/PacketSendListener;)V", at = @At("HEAD"), cancellable = true)
    private void novautils$send(Packet<?> packet, PacketSendListener listener, CallbackInfo ci) {
        if (!dev.novautils.NovaUtils.modulesReady) return;
        if (ModuleManager.get().onSendPacket(packet)) ci.cancel();
    }
}
