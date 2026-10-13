package dev.novautils.mixin;

import com.mojang.blaze3d.vertex.PoseStack;
import dev.novautils.modules.render.Esp;
import dev.novautils.utils.ChamsRenderType;
import net.minecraft.client.model.EntityModel;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Esp CHAMS: after vanilla draws a mob's model, draw it once more through walls in the chams colour. */
@Mixin(LivingEntityRenderer.class)
public abstract class ChamsMixin {
    @Shadow
    protected EntityModel<?> model;

    @SuppressWarnings({"unchecked", "rawtypes"})
    private ResourceLocation texture(LivingEntity entity) {
        return ((net.minecraft.client.renderer.entity.EntityRenderer) (Object) this).getTextureLocation(entity);
    }

    @Inject(method = "render(Lnet/minecraft/world/entity/LivingEntity;FFLcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/MultiBufferSource;I)V",
        at = @At(value = "INVOKE", shift = At.Shift.AFTER,
            target = "Lnet/minecraft/client/model/EntityModel;renderToBuffer(Lcom/mojang/blaze3d/vertex/PoseStack;Lcom/mojang/blaze3d/vertex/VertexConsumer;IIFFFF)V"))
    private void novautils$chams(LivingEntity entity, float yaw, float partial, PoseStack pose, MultiBufferSource buffers, int light, CallbackInfo ci) {
        if (!dev.novautils.NovaUtils.modulesReady) return;
        int c = Esp.chamsColor(entity);
        if (c == 0) return;
        ResourceLocation texture = Esp.chamsTextured() ? texture(entity) : Esp.WHITE;
        model.renderToBuffer(pose, buffers.getBuffer(ChamsRenderType.chams(texture)), 0xF000F0, OverlayTexture.NO_OVERLAY,
            (c >> 16 & 0xFF) / 255f, (c >> 8 & 0xFF) / 255f, (c & 0xFF) / 255f, (c >>> 24) / 255f);
    }
}
