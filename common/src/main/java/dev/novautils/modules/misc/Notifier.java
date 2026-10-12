package dev.novautils.modules.misc;

import dev.novautils.gui.Toasts;
import dev.novautils.modules.Category;
import dev.novautils.modules.Module;
import dev.novautils.settings.Setting;
import net.minecraft.client.multiplayer.PlayerInfo;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientboundEntityEventPacket;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Heads-up alerts (only you see them):
 * <ul>
 *   <li>visual range - a player comes into (or leaves) your render distance, with a ping sound.
 *       The one that matters most on an anarchy server.</li>
 *   <li>totem pops - someone near you (or you) burns a Totem of Undying, with a running count.</li>
 *   <li>join / leave - server-wide, off by default because on big servers it's constant.</li>
 * </ul>
 */
public class Notifier extends Module {
    private final Setting<Boolean> visualRange = register(Setting.builder(Boolean.class, "visual-range")
        .description("Alert when a player enters or leaves your render distance.")
        .defaultValue(true).build());

    private final Setting<Boolean> sound = register(Setting.builder(Boolean.class, "sound")
        .description("Play a ping when a player comes into view.")
        .defaultValue(true).visible(() -> visualRange.get()).build());

    private final Setting<Boolean> totemPops = register(Setting.builder(Boolean.class, "totem-pops")
        .description("Alert when someone pops a totem.")
        .defaultValue(true).build());

    private final Setting<Boolean> joinLeave = register(Setting.builder(Boolean.class, "join-leave")
        .description("Alert when players join or leave the server.")
        .defaultValue(false).build());

    private final Setting<Boolean> chat = register(Setting.builder(Boolean.class, "chat")
        .description("Also write alerts in chat (otherwise only an on-screen toast).")
        .defaultValue(true).build());

    private final Setting<Boolean> inventoryFull = register(Setting.builder(Boolean.class, "inventory-full")
        .description("Alert when your inventory fills up.")
        .defaultValue(true).build());

    private final Setting<Boolean> lowFood = register(Setting.builder(Boolean.class, "low-food")
        .description("Alert when your hunger drops to 3 drumsticks.")
        .defaultValue(true).build());

    private boolean wasFull, wasHungry;

    private final Set<String> known = new HashSet<>();
    private final Set<UUID> inView = new HashSet<>();
    private final Map<UUID, Integer> pops = new HashMap<>();
    private boolean primed;

    public Notifier() {
        super("Notifier", "Alerts: players in view, totem pops, joins, full inventory, low food.", Category.MISC);
    }

    @Override
    public void onEnable() {
        known.clear();
        inView.clear();
        pops.clear();
        primed = false;
    }

    private void alert(String text, int color) {
        Toasts.push(text, color);
        if (chat.get() && mc.player != null) {
            mc.player.sendSystemMessage(Component.literal("[NovaUtils] ").withColor(0x22D3EE)
                .append(Component.literal(text).withColor(color & 0xFFFFFF)));
        }
    }

    @Override
    public boolean onReceivePacket(Packet<?> packet) {
        if (!totemPops.get() || mc.level == null) return false;
        if (packet instanceof ClientboundEntityEventPacket ev && ev.getEventId() == 35) {
            Entity e = ev.getEntity(mc.level);
            if (e instanceof Player p) {
                int n = pops.merge(p.getUUID(), 1, Integer::sum);
                String who = p == mc.player ? "You" : p.getName().getString();
                alert(who + " popped a totem" + (n > 1 ? " (" + n + ")" : ""), 0xFFFFAA00);
            }
        }
        return false;
    }

    @Override
    public void onTick() {
        if (mc.player == null || mc.level == null || mc.getConnection() == null) return;

        if (visualRange.get()) {
            Set<UUID> now = new HashSet<>();
            for (Player p : mc.level.players()) {
                if (p == mc.player) continue;
                now.add(p.getUUID());
                if (primed && !inView.contains(p.getUUID())) {
                    boolean friend = dev.novautils.utils.Friends.isFriend(p);
                    alert((friend ? "Friend " : "") + p.getName().getString() + " entered visual range", friend ? 0xFF55FFFF : 0xFFFF5555);
                    if (sound.get()) mc.getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.NOTE_BLOCK_PLING, 1.6f));
                }
            }
            for (UUID id : inView) {
                if (!now.contains(id) && primed) {
                    PlayerInfo info = mc.getConnection().getPlayerInfo(id);
                    if (info != null) alert(info.getProfile().name() + " left visual range", 0xFF8A8A9A);
                }
            }
            inView.clear();
            inView.addAll(now);
        }

        for (Player p : mc.level.players()) if (p.isDeadOrDying()) pops.remove(p.getUUID());

        if (joinLeave.get()) {
            Set<String> now = new HashSet<>();
            for (PlayerInfo info : mc.getConnection().getOnlinePlayers()) now.add(info.getProfile().name());
            if (primed) {
                for (String name : now) if (!known.contains(name)) alert(name + " joined", 0xFF55FF55);
                for (String name : known) if (!now.contains(name)) alert(name + " left", 0xFF8A8A9A);
            }
            known.clear();
            known.addAll(now);
        }
        if (inventoryFull.get()) {
            boolean full = mc.player.getInventory().getFreeSlot() == -1;
            if (full && !wasFull && primed) alert("Inventory full", 0xFFFFAA00);
            wasFull = full;
        }
        if (lowFood.get()) {
            boolean hungry = mc.player.getFoodData().getFoodLevel() <= 6;
            if (hungry && !wasHungry && primed) alert("Low food - eat something", 0xFFFFAA00);
            wasHungry = hungry;
        }
        primed = true;
    }
}
