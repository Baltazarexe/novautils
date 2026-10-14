package dev.novautils.mixin;

import dev.novautils.modules.ModuleManager;
import dev.novautils.modules.render.ShulkerPeek;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.world.inventory.Slot;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** ShulkerPeek: a hovered shulker box shows its contents instead of the plain tooltip. */
@Mixin(AbstractContainerScreen.class)
public abstract class ContainerScreenMixin {
    @Shadow
    protected Slot hoveredSlot;

    @Inject(method = "renderTooltip", at = @At("HEAD"), cancellable = true)
    private void novautils$shulkerPeek(GuiGraphics g, int mouseX, int mouseY, CallbackInfo ci) {
        if (!dev.novautils.NovaUtils.modulesReady || hoveredSlot == null || !hoveredSlot.hasItem()) return;
        if (!ModuleManager.get().isActive(ShulkerPeek.class)) return;
        var self = (AbstractContainerScreen<?>) (Object) this;
        if (!self.getMenu().getCarried().isEmpty()) return;
        if (ModuleManager.get().get(ShulkerPeek.class).draw(g, hoveredSlot.getItem(), mouseX, mouseY)) ci.cancel();
    }
}
