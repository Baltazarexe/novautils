package dev.novautils.mixin;

import net.minecraft.network.protocol.game.ServerboundMovePlayerPacket;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Mutable;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(ServerboundMovePlayerPacket.class)
public interface MovePlayerPacketAccessor {
    @Mutable
    @Accessor("onGround")
    void novautils$setOnGround(boolean value);
}
