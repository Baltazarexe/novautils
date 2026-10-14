package dev.novautils.modules.player;

import dev.novautils.modules.Category;
import dev.novautils.modules.Module;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ServerboundPlayerCommandPacket;

/**
 * Sprinting costs a lot of hunger, but the server only charges it while it thinks you sprint. This
 * keeps running fast on your side and never tells the server you started sprinting, so you get
 * hungry about as slowly as when walking. (Some servers' anti-cheats correct sprint speed; if you
 * rubber-band, turn it off.)
 */
public class AntiHunger extends Module {
    public AntiHunger() {
        super("AntiHunger", "Sprint without the server charging sprint hunger.", Category.PLAYER);
    }

    @Override
    public void onEnable() {
        if (mc.player != null && mc.getConnection() != null && mc.player.isSprinting()) {
            mc.getConnection().send(new ServerboundPlayerCommandPacket(mc.player, ServerboundPlayerCommandPacket.Action.STOP_SPRINTING));
        }
    }

    @Override
    public boolean onSendPacket(Packet<?> packet) {
        return packet instanceof ServerboundPlayerCommandPacket p && p.getAction() == ServerboundPlayerCommandPacket.Action.START_SPRINTING;
    }
}
