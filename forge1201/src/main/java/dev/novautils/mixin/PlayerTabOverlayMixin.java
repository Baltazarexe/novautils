package dev.novautils.mixin;

import dev.novautils.modules.misc.BetterTab;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.PlayerTabOverlay;
import net.minecraft.client.multiplayer.PlayerInfo;
import net.minecraft.network.chat.Component;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Constant;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyConstant;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** BetterTab: more players in the list, highlighted names, the ping as a number. */
@Mixin(PlayerTabOverlay.class)
public abstract class PlayerTabOverlayMixin {
    @ModifyConstant(method = "getPlayerInfos", constant = @Constant(longValue = 80L))
    private long novautils$maxPlayers(long vanilla) {
        return BetterTab.maxPlayers(vanilla);
    }

    @Inject(method = "getNameForDisplay", at = @At("RETURN"), cancellable = true)
    private void novautils$name(PlayerInfo info, CallbackInfoReturnable<Component> cir) {
        Component name = BetterTab.name(info, cir.getReturnValue());
        if (name != null) cir.setReturnValue(name);
    }

    @Inject(method = "renderPingIcon", at = @At("HEAD"), cancellable = true)
    private void novautils$ping(GuiGraphics g, int width, int x, int y, PlayerInfo info, CallbackInfo ci) {
        if (BetterTab.drawPing(g, width, x, y, info)) ci.cancel();
    }
}
