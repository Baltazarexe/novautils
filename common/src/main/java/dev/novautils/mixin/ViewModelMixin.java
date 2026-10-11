package dev.novautils.mixin;

import com.llamalad7.mixinextras.sugar.Local;
import com.mojang.blaze3d.vertex.PoseStack;
import dev.novautils.modules.render.ViewModel;
import net.minecraft.world.InteractionHand;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * ViewModel hooks on the first-person "arm with item" draw. The position offset wraps the whole
 * method (arm and item move together); scale and rotation wrap only the item draw call, after
 * vanilla has put the item in the hand, so it grows/turns in place instead of swinging around the
 * camera. Class and calls differ per version (FirstPersonHandsAndItemsRenderer.submitArmWithItem +
 * ItemStackRenderState.submit on 26.3, ItemInHandRenderer.renderArmWithItem + renderItem on
 * 26.1.2), so every hook is optional and only the hand and PoseStack are taken from the arguments.
 */
@Pseudo
@Mixin(targets = {"net.minecraft.client.renderer.FirstPersonHandsAndItemsRenderer", "net.minecraft.client.renderer.ItemInHandRenderer"})
public abstract class ViewModelMixin {
    private static final String ITEM_263 = "Lnet/minecraft/client/renderer/item/ItemStackRenderState;submit(Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/SubmitNodeCollector;III)V";
    private static final String ITEM_261 = "Lnet/minecraft/client/renderer/ItemInHandRenderer;renderItem(Lnet/minecraft/world/entity/LivingEntity;Lnet/minecraft/world/item/ItemStack;Lnet/minecraft/world/item/ItemDisplayContext;Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/SubmitNodeCollector;I)V";

    @Inject(method = {"submitArmWithItem", "renderArmWithItem"}, at = @At("HEAD"), require = 0)
    private void novautils$viewModelBefore(CallbackInfo ci, @Local(argsOnly = true) PoseStack pose,
                                           @Local(argsOnly = true) InteractionHand hand) {
        ViewModel.before(pose, hand);
    }

    @Inject(method = {"submitArmWithItem", "renderArmWithItem"}, at = @At("RETURN"), require = 0)
    private void novautils$viewModelAfter(CallbackInfo ci, @Local(argsOnly = true) PoseStack pose) {
        ViewModel.after(pose);
    }

    @Inject(method = {"submitArmWithItem", "renderArmWithItem"}, require = 0,
        at = {@At(value = "INVOKE", target = ITEM_263), @At(value = "INVOKE", target = ITEM_261)})
    private void novautils$itemBefore(CallbackInfo ci, @Local(argsOnly = true) PoseStack pose,
                                      @Local(argsOnly = true) InteractionHand hand) {
        ViewModel.beforeItem(pose, hand);
    }

    @Inject(method = {"submitArmWithItem", "renderArmWithItem"}, require = 0,
        at = {@At(value = "INVOKE", target = ITEM_263, shift = At.Shift.AFTER),
              @At(value = "INVOKE", target = ITEM_261, shift = At.Shift.AFTER)})
    private void novautils$itemAfter(CallbackInfo ci, @Local(argsOnly = true) PoseStack pose) {
        ViewModel.afterItem(pose);
    }
}
