package dev.novautils.modules.misc;

import dev.novautils.modules.Category;
import dev.novautils.modules.Module;
import net.minecraft.client.multiplayer.PlayerInfo;
import net.minecraft.network.chat.Component;

import java.util.HashSet;
import java.util.Set;

/** Tells you in chat (only you see it) when players join or leave the server. */
public class Notifier extends Module {
    private final Set<String> known = new HashSet<>();
    private boolean primed;

    public Notifier() {
        super("Notifier", "Chat notice when players join or leave.", Category.MISC);
    }

    @Override
    public void onEnable() {
        known.clear();
        primed = false;
    }

    @Override
    public void onTick() {
        if (mc.player == null || mc.getConnection() == null) return;

        Set<String> now = new HashSet<>();
        for (PlayerInfo info : mc.getConnection().getOnlinePlayers()) {
            now.add(info.getProfile().name());
        }

        if (primed) {
            for (String name : now) if (!known.contains(name)) mc.player.sendSystemMessage(Component.literal("[NovaUtils] " + name + " joined"));
            for (String name : known) if (!now.contains(name)) mc.player.sendSystemMessage(Component.literal("[NovaUtils] " + name + " left"));
        }
        known.clear();
        known.addAll(now);
        primed = true;
    }
}
