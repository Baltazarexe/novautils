package dev.novautils.modules.misc;

import dev.novautils.utils.PlatformCompat;
import dev.novautils.modules.Category;
import dev.novautils.modules.Module;
import dev.novautils.settings.Setting;
import net.minecraft.client.gui.screens.ConnectScreen;
import net.minecraft.client.gui.screens.DisconnectedScreen;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.client.multiplayer.ServerData;
import net.minecraft.client.multiplayer.resolver.ServerAddress;

/** Reconnects to the last server a few seconds after you get disconnected. */
public class AutoReconnect extends Module {
    private final Setting<Integer> delaySeconds = register(Setting.builder(Integer.class, "delay-seconds")
        .description("Seconds to wait before reconnecting.")
        .defaultValue(5).range(1, 120).build());

    private ServerData lastServer;
    private int timer = -1;

    public AutoReconnect() {
        super("AutoReconnect", "Reconnects after a disconnect.", Category.MISC);
    }

    @Override
    public void onTick() {
        ServerData current = mc.getCurrentServer();
        if (current != null && mc.player != null) lastServer = current;

        if (PlatformCompat.currentScreen(mc) instanceof DisconnectedScreen && lastServer != null) {
            if (timer < 0) timer = delaySeconds.get() * 20;
            if (timer-- == 0) {
                timer = -1;
                ConnectScreen.startConnecting(new TitleScreen(), mc, ServerAddress.parseString(lastServer.ip), lastServer, false);
            }
        } else {
            timer = -1;
        }
    }
}
