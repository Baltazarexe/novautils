package dev.novautils.modules.combat;

import dev.novautils.modules.Category;
import dev.novautils.modules.Module;
import dev.novautils.settings.Setting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Items;

/**
 * Disconnects you before you die: when health drops low, when you run out of totems, or when a
 * player comes too close. Self-preservation only - it never does anything to the other player.
 * Turns itself off after firing so it doesn't instantly log you out again on reconnect.
 */
public class AutoLog extends Module {
    private final Setting<Integer> health = register(Setting.builder(Integer.class, "health")
        .description("Disconnect at or below this many health points (20 = full). 0 = off.")
        .defaultValue(6).range(0, 19).build());

    private final Setting<Boolean> noTotems = register(Setting.builder(Boolean.class, "no-totems")
        .description("Disconnect when you have no Totem of Undying left.")
        .defaultValue(false).build());

    private final Setting<Integer> playerRange = register(Setting.builder(Integer.class, "player-range")
        .description("Disconnect when another player comes within this many blocks. 0 = off.")
        .defaultValue(0).range(0, 128).build());

    public AutoLog() {
        super("AutoLog", "Disconnects before you die (low health, no totems, player nearby).", Category.COMBAT);
    }

    private boolean hasTotem() {
        var inv = mc.player.getInventory();
        for (int i = 0; i < inv.getContainerSize(); i++) {
            if (inv.getItem(i).is(Items.TOTEM_OF_UNDYING)) return true;
        }
        return false;
    }

    private void logOut(String reason) {
        setEnabled(false);
        // reconnecting straight back into whatever made us leave would defeat the point
        dev.novautils.modules.ModuleManager.get().get(dev.novautils.modules.misc.AutoReconnect.class).setEnabled(false);
        if (mc.getConnection() != null) {
            mc.getConnection().getConnection().disconnect(Component.literal("[NovaUtils AutoLog] " + reason));
        }
    }

    @Override
    public void onTick() {
        if (mc.player == null || mc.level == null || mc.getConnection() == null) return;
        if (mc.player.isDeadOrDying()) return;

        if (health.get() > 0 && mc.player.getHealth() <= health.get()) {
            logOut("Health was " + Math.round(mc.player.getHealth()));
            return;
        }
        if (noTotems.get() && !hasTotem()) {
            logOut("Out of totems");
            return;
        }
        if (playerRange.get() > 0) {
            for (Player other : mc.level.players()) {
                if (other == mc.player) continue;
                if (dev.novautils.utils.Friends.isFriend(other)) continue;
                if (mc.player.distanceTo(other) <= playerRange.get()) {
                    logOut(other.getName().getString() + " came within " + playerRange.get() + " blocks");
                    return;
                }
            }
        }
    }
}
