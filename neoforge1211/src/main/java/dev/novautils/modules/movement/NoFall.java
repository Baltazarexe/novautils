package dev.novautils.modules.movement;

import dev.novautils.mixin.MovePlayerPacketAccessor;
import dev.novautils.modules.Category;
import dev.novautils.modules.Module;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ServerboundMovePlayerPacket;

/**
 * No fall damage. Every movement packet sent while you're falling says "on ground", so the server
 * keeps resetting its fall counter and never has a big fall to charge you for. Editing the packets
 * vanilla already sends (instead of sending extra ones) keeps the packet rate normal.
 * Skipped while gliding with an elytra and in creative.
 */
public class NoFall extends Module {
    public NoFall() {
        super("NoFall", "Cancels fall damage.", Category.MOVEMENT);
    }

    @Override
    public boolean onSendPacket(Packet<?> packet) {
        if (!(packet instanceof ServerboundMovePlayerPacket)) return false;
        var p = mc.player;
        if (p == null || p.isFallFlying() || p.getAbilities().instabuild || p.getAbilities().flying) return false;
        if (p.fallDistance > 2.0 || p.getDeltaMovement().y < -0.5) {
            ((MovePlayerPacketAccessor) packet).novautils$setOnGround(true);
        }
        return false;
    }
}
