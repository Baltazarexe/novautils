package dev.novautils.mixin;

import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.KeyMapping;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(KeyMapping.class)
public interface KeyMappingAccessor {
    /** The key this mapping is bound to right now (the user's choice, not the default). */
    @Accessor("key")
    InputConstants.Key novautils$boundKey();
}
