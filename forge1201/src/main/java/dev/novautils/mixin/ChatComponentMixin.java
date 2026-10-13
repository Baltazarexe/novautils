package dev.novautils.mixin;

import dev.novautils.modules.misc.BetterChat;
import net.minecraft.client.gui.components.ChatComponent;
import net.minecraft.network.chat.Component;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Constant;
import org.spongepowered.asm.mixin.injection.ModifyConstant;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

/** BetterChat: rewrite each new line, and lift the 100-message history cap. */
@Mixin(ChatComponent.class)
public abstract class ChatComponentMixin {
    @ModifyVariable(method = "addMessage(Lnet/minecraft/network/chat/Component;Lnet/minecraft/network/chat/MessageSignature;Lnet/minecraft/client/GuiMessageTag;)V",
        at = @At("HEAD"), argsOnly = true, ordinal = 0)
    private Component novautils$betterChat(Component message) {
        if (!dev.novautils.NovaUtils.modulesReady) return message;
        ChatComponentAccessor self = (ChatComponentAccessor) this;
        return BetterChat.process(message, self.novautils$allMessages(), self::novautils$refreshTrimmedMessages);
    }

    @ModifyConstant(method = "addMessage(Lnet/minecraft/network/chat/Component;Lnet/minecraft/network/chat/MessageSignature;ILnet/minecraft/client/GuiMessageTag;Z)V",
        constant = @Constant(intValue = 100), require = 0)
    private int novautils$history(int vanilla) {
        if (!dev.novautils.NovaUtils.modulesReady) return vanilla;
        return BetterChat.historyLimit(vanilla);
    }
}
