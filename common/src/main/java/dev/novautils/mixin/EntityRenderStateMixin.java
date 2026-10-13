package dev.novautils.mixin;

import dev.novautils.utils.ChamsHolder;
import net.minecraft.client.renderer.entity.state.EntityRenderState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;

/** Carries the chams colour from extractRenderState to submit (ChamsMixin). */
@Mixin(EntityRenderState.class)
public abstract class EntityRenderStateMixin implements ChamsHolder {
    @Unique
    private int novautils$chamsColor;

    @Override
    public int novautils$chams() {
        return novautils$chamsColor;
    }

    @Override
    public void novautils$setChams(int color) {
        novautils$chamsColor = color;
    }
}
