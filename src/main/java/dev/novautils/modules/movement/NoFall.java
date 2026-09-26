package dev.novautils.modules.movement;

import dev.novautils.modules.Category;
import dev.novautils.modules.Module;
import net.minecraft.network.protocol.game.ServerboundMovePlayerPacket;

/**
 * While you are falling far enough to get hurt, keeps resetting your client fall distance and tells
 * the server you are on the ground (a status-only movement packet), so it never counts the fall.
 * Works on servers that trust the reported on-ground flag (2b2t-style servers usually do); a server
 * that recomputes movement fully on its own can still hurt you.
 */
public class NoFall extends Module {
    public NoFall() {
        super("NoFall", "Cancels fall damage by spoofing the on-ground flag.", Category.MOVEMENT);
    }

    @Override
    public void onTick() {
        if (mc.player == null || mc.getConnection() == null) return;
        if (mc.player.fallDistance <= 2.5 || mc.player.isFallFlying() || mc.player.getAbilities().flying) return;

        mc.player.resetFallDistance();
        mc.getConnection().send(new ServerboundMovePlayerPacket.StatusOnly(true, mc.player.horizontalCollision));
    }
}
