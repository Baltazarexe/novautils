package dev.novautils.modules.misc;

import dev.novautils.utils.PlatformCompat;
import dev.novautils.modules.Category;
import dev.novautils.modules.Module;
import net.minecraft.client.gui.screens.DeathScreen;
import net.minecraft.network.protocol.game.ServerboundClientCommandPacket;

/** Clicks "Respawn" for you the instant the death screen shows up. */
public class AutoRespawn extends Module {
    public AutoRespawn() {
        super("AutoRespawn", "Respawns automatically when you die.", Category.MISC);
    }

    @Override
    public void onTick() {
        if (!(PlatformCompat.currentScreen(mc) instanceof DeathScreen)) return;
        if (mc.getConnection() == null) return;

        mc.getConnection().send(new ServerboundClientCommandPacket(ServerboundClientCommandPacket.Action.PERFORM_RESPAWN));
        PlatformCompat.openScreen(mc, null);
    }
}
