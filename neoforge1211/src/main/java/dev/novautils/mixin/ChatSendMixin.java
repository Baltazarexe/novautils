package dev.novautils.mixin;

import dev.novautils.commands.Commands;
import net.minecraft.client.multiplayer.ClientPacketListener;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Chat lines starting with "." are NovaUtils commands: handled here, never sent to the server. */
@Mixin(ClientPacketListener.class)
public abstract class ChatSendMixin {
    @Inject(method = "sendChat", at = @At("HEAD"), cancellable = true)
    private void novautils$commands(String message, CallbackInfo ci) {
        if (!dev.novautils.NovaUtils.modulesReady) return;
        if (Commands.handle(message)) ci.cancel();
    }
}
