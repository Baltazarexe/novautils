package dev.novautils.mixin;

import dev.novautils.modules.misc.BetterChat;
import net.minecraft.client.gui.components.ChatComponent;
import net.minecraft.network.chat.Component;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Constant;
import org.spongepowered.asm.mixin.injection.ModifyConstant;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

/** BetterChat's hooks: rewrite each new line, and lift the 100-message history cap. */
@Mixin(ChatComponent.class)
public abstract class ChatComponentMixin {
    @ModifyVariable(method = "addMessage(Lnet/minecraft/network/chat/Component;Lnet/minecraft/network/chat/MessageSignature;Lnet/minecraft/client/multiplayer/chat/GuiMessageSource;Lnet/minecraft/client/multiplayer/chat/GuiMessageTag;)V",
        at = @At("HEAD"), argsOnly = true, ordinal = 0)
    private Component novautils$betterChat(Component message) {
        ChatComponentAccessor self = (ChatComponentAccessor) this;
        return BetterChat.process(message, self.novautils$allMessages(), self::novautils$refreshTrimmedMessages);
    }

    @ModifyConstant(method = {"addMessageToQueue", "addMessageToDisplayQueue"}, constant = @Constant(intValue = 100), require = 0)
    private int novautils$history(int vanilla) {
        return BetterChat.historyLimit(vanilla);
    }
}
