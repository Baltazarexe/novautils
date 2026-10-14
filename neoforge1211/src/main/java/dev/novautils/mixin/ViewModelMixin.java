package dev.novautils.mixin;

import com.mojang.blaze3d.vertex.PoseStack;
import dev.novautils.modules.render.ViewModel;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.ItemInHandRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * ViewModel hooks on the first-person arm-with-item draw. The position offset wraps the whole
 * method (arm and item move together); scale and rotation wrap only the item draw call, after
 * vanilla has put the item in the hand, so it grows and turns in place.
 */
@Mixin(ItemInHandRenderer.class)
public abstract class ViewModelMixin {
    private static final String ITEM = "Lnet/minecraft/client/renderer/ItemInHandRenderer;renderItem(Lnet/minecraft/world/entity/LivingEntity;Lnet/minecraft/world/item/ItemStack;Lnet/minecraft/world/item/ItemDisplayContext;ZLcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/MultiBufferSource;I)V";

    @Inject(method = "renderArmWithItem", at = @At("HEAD"))
    private void novautils$before(AbstractClientPlayer player, float partial, float pitch, InteractionHand hand, float swing,
                                  ItemStack stack, float equip, PoseStack pose, MultiBufferSource buffers, int light, CallbackInfo ci) {
        ViewModel.before(pose, hand);
    }

    @Inject(method = "renderArmWithItem", at = @At("RETURN"))
    private void novautils$after(AbstractClientPlayer player, float partial, float pitch, InteractionHand hand, float swing,
                                 ItemStack stack, float equip, PoseStack pose, MultiBufferSource buffers, int light, CallbackInfo ci) {
        ViewModel.after(pose);
    }

    @Inject(method = "renderArmWithItem", at = @At(value = "INVOKE", target = ITEM))
    private void novautils$itemBefore(AbstractClientPlayer player, float partial, float pitch, InteractionHand hand, float swing,
                                      ItemStack stack, float equip, PoseStack pose, MultiBufferSource buffers, int light, CallbackInfo ci) {
        ViewModel.beforeItem(pose, hand);
    }

    @Inject(method = "renderArmWithItem", at = @At(value = "INVOKE", target = ITEM, shift = At.Shift.AFTER))
    private void novautils$itemAfter(AbstractClientPlayer player, float partial, float pitch, InteractionHand hand, float swing,
                                     ItemStack stack, float equip, PoseStack pose, MultiBufferSource buffers, int light, CallbackInfo ci) {
        ViewModel.afterItem(pose);
    }
}
