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
 * ViewModel: wraps the first-person "arm with item" draw in an extra transform. The class and its
 * arguments changed between versions (FirstPersonHandsAndItemsRenderer.submitArmWithItem on 26.3,
 * ItemInHandRenderer.renderArmWithItem on 26.1.2), so both are targeted by name and only the hand
 * and the PoseStack are picked out of the arguments.
 */
@Pseudo
@Mixin(targets = {"net.minecraft.client.renderer.FirstPersonHandsAndItemsRenderer", "net.minecraft.client.renderer.ItemInHandRenderer"})
public abstract class ViewModelMixin {
    @Inject(method = {"submitArmWithItem", "renderArmWithItem"}, at = @At("HEAD"), require = 0)
    private void novautils$viewModelBefore(CallbackInfo ci, @Local(argsOnly = true) PoseStack pose,
                                           @Local(argsOnly = true) InteractionHand hand) {
        ViewModel.before(pose, hand);
    }

    @Inject(method = {"submitArmWithItem", "renderArmWithItem"}, at = @At("RETURN"), require = 0)
    private void novautils$viewModelAfter(CallbackInfo ci, @Local(argsOnly = true) PoseStack pose) {
        ViewModel.after(pose);
    }
}
