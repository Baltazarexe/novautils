package dev.novautils.mixin;

import com.mojang.blaze3d.vertex.PoseStack;
import dev.novautils.modules.render.Esp;
import dev.novautils.utils.ChamsHolder;
import dev.novautils.utils.EspRender;
import dev.novautils.utils.PlatformCompat;
import net.minecraft.client.model.EntityModel;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import net.minecraft.client.renderer.entity.state.LivingEntityRenderState;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Esp CHAMS: right before vanilla submits a mob's model, submit the same model once more with a
 * render type that ignores walls, tinted with the chams colour. The submitModel call differs
 * between 26.3 and 26.1.2, hence two targets (one of them matches) and PlatformCompat's call.
 */
@Mixin(LivingEntityRenderer.class)
public abstract class ChamsMixin {
    private static final String SUBMIT_263 = "Lnet/minecraft/client/renderer/SubmitNodeCollector;submitModel(Lnet/minecraft/client/model/Model;Ljava/lang/Object;Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/rendertype/RenderType;IIILnet/minecraft/client/renderer/texture/UvMapping;I)V";
    private static final String SUBMIT_261 = "Lnet/minecraft/client/renderer/SubmitNodeCollector;submitModel(Lnet/minecraft/client/model/Model;Ljava/lang/Object;Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/rendertype/RenderType;IIILnet/minecraft/client/renderer/texture/TextureAtlasSprite;ILnet/minecraft/client/renderer/feature/ModelFeatureRenderer$CrumblingOverlay;)V";

    @Shadow
    protected EntityModel<?> model;

    @Shadow
    public abstract Identifier getTextureLocation(LivingEntityRenderState state);

    @Inject(method = "extractRenderState(Lnet/minecraft/world/entity/LivingEntity;Lnet/minecraft/client/renderer/entity/state/LivingEntityRenderState;F)V", at = @At("TAIL"))
    private void novautils$chamsState(LivingEntity entity, LivingEntityRenderState state, float partial, CallbackInfo ci) {
        if (!dev.novautils.NovaUtils.modulesReady) return;
        ((ChamsHolder) state).novautils$setChams(Esp.chamsColor(entity));
    }

    @Inject(method = "submit(Lnet/minecraft/client/renderer/entity/state/LivingEntityRenderState;Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/SubmitNodeCollector;Lnet/minecraft/client/renderer/state/level/CameraRenderState;)V",
        at = {@At(value = "INVOKE", target = SUBMIT_263), @At(value = "INVOKE", target = SUBMIT_261)})
    private void novautils$chams(LivingEntityRenderState state, PoseStack pose, SubmitNodeCollector collector, CameraRenderState camera, CallbackInfo ci) {
        int color = ((ChamsHolder) state).novautils$chams();
        if (color == 0) return;
        Identifier texture = Esp.chamsTextured() ? getTextureLocation(state) : Esp.WHITE;
        RenderType type = EspRender.chams(texture);
        if (type != null) PlatformCompat.submitChams(collector, model, state, pose, type, color);
    }
}
