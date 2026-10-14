package dev.novautils.mixin;

import dev.novautils.gui.screens.AccountScreen;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.multiplayer.JoinMultiplayerScreen;
import net.minecraft.network.chat.Component;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** An "Accounts" button in the top-left corner of the multiplayer screen. */
@Mixin(JoinMultiplayerScreen.class)
public abstract class MultiplayerScreenMixin extends Screen {
    protected MultiplayerScreenMixin(Component title) {
        super(title);
    }

    @Inject(method = "init", at = @At("TAIL"))
    private void novautils$accounts(CallbackInfo ci) {
        Screen self = this;
        addRenderableWidget(Button.builder(Component.literal("Accounts"), b -> dev.novautils.utils.PlatformCompat.openScreen(minecraft, new AccountScreen(self)))
            .bounds(6, 6, 74, 20).build());
    }
}
