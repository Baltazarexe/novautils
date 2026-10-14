package dev.novautils.mixin;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import dev.novautils.modules.render.Xray;
import net.minecraft.client.renderer.block.ModelBlockRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/** Xray SEE_THROUGH, vanilla renderer: flat lighting (no ambient occlusion) while see-through. */
@Mixin(ModelBlockRenderer.class)
public abstract class XrayAoMixin {
    @ModifyExpressionValue(method = "tesselateBlock(Lnet/minecraft/world/level/BlockAndTintGetter;Lnet/minecraft/client/resources/model/BakedModel;Lnet/minecraft/world/level/block/state/BlockState;Lnet/minecraft/core/BlockPos;Lcom/mojang/blaze3d/vertex/PoseStack;Lcom/mojang/blaze3d/vertex/VertexConsumer;ZLnet/minecraft/util/RandomSource;JILnet/neoforged/neoforge/client/model/data/ModelData;Lnet/minecraft/client/renderer/RenderType;)V",
        at = @At(value = "INVOKE", target = "Lnet/minecraft/client/Minecraft;useAmbientOcclusion()Z"))
    private boolean novautils$xrayNoAo(boolean original) {
        return original && !Xray.forcesBright();
    }
}
