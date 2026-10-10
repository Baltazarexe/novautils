package dev.novautils.modules.misc;

import dev.novautils.gui.Toasts;
import dev.novautils.modules.Category;
import dev.novautils.modules.Module;
import dev.novautils.utils.Friends;
import dev.novautils.utils.PlatformCompat;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;

/**
 * Middle-click a player to add or remove them as a friend (works at any distance - it picks the
 * player closest to your crosshair). Friends show in a different colour on Nametags and Tracers,
 * get their own Notifier alert, and don't trigger AutoLog's "player nearby".
 */
public class FriendsModule extends Module {
    private boolean wasDown;

    public FriendsModule() {
        super("Friends", "Middle-click a player to add/remove them as a friend.", Category.MISC);
        setEnabled(true);
    }

    /** The player nearest to the centre of the screen, within ~3 degrees and 128 blocks. */
    private Player aimed() {
        Vec3 eyes = mc.player.getEyePosition();
        Vec3 look = mc.player.getLookAngle();
        Player best = null;
        double bestDot = Math.cos(Math.toRadians(3));
        for (Player p : mc.level.players()) {
            if (p == mc.player) continue;
            Vec3 to = p.getBoundingBox().getCenter().subtract(eyes);
            double dist = to.length();
            if (dist > 128 || dist < 0.01) continue;
            double dot = to.scale(1 / dist).dot(look);
            if (dot > bestDot) {
                bestDot = dot;
                best = p;
            }
        }
        return best;
    }

    @Override
    public void onTick() {
        if (mc.player == null || mc.level == null) return;
        boolean down = mc.options.keyPickItem.isDown();
        if (down && !wasDown && PlatformCompat.currentScreen(mc) == null) {
            Player p = aimed();
            if (p != null) {
                String name = p.getName().getString();
                boolean friend = Friends.toggle(name);
                Toasts.push(friend ? name + " added as friend" : name + " removed from friends",
                    friend ? 0xFF55FFFF : 0xFF8A8A9A);
            }
        }
        wasDown = down;
    }
}
