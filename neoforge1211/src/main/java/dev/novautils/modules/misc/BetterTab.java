package dev.novautils.modules.misc;

import dev.novautils.gui.Theme;
import dev.novautils.modules.Category;
import dev.novautils.modules.Module;
import dev.novautils.modules.ModuleManager;
import dev.novautils.settings.Setting;
import dev.novautils.utils.Friends;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.multiplayer.PlayerInfo;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.Style;
import net.minecraft.network.chat.TextColor;

/**
 * A better player list (Tab): more than vanilla's 80 players, the ping as a coloured number instead
 * of the bars, and you and your friends easy to spot. PlayerTabOverlayMixin calls in here.
 */
public class BetterTab extends Module {
    private final Setting<Integer> size = register(Setting.builder(Integer.class, "max-players")
        .description("How many players the list shows (vanilla shows 80).").defaultValue(200).range(10, 1000).build());

    private final Setting<Boolean> numericPing = register(Setting.builder(Boolean.class, "ping-number")
        .description("Show the ping in ms, coloured green to red, instead of the signal bars.").defaultValue(true).build());

    private final Setting<Boolean> highlightSelf = register(Setting.builder(Boolean.class, "highlight-self")
        .description("Your own name in the theme colour.").defaultValue(true).build());

    private final Setting<Boolean> highlightFriends = register(Setting.builder(Boolean.class, "highlight-friends")
        .description("Friends in their own colour.").defaultValue(true).build());

    private final Setting<Integer> friendColor = register(Setting.builder(Integer.class, "friend-color")
        .description("Colour for friends.").defaultValue(0xFF55E6FF).asColor().visible(highlightFriends::get).build());

    public BetterTab() {
        super("BetterTab", "Better player list: more players, ping in ms, you and your friends highlighted.", Category.MISC);
    }

    private static BetterTab active() {
        if (!dev.novautils.NovaUtils.modulesReady) return null;
        BetterTab t = ModuleManager.get().get(BetterTab.class);
        return t != null && t.isEnabled() ? t : null;
    }

    /** The list's player limit. */
    public static long maxPlayers(long vanilla) {
        BetterTab t = active();
        return t == null ? vanilla : t.size.get();
    }

    /** The name to show for a player, or null to leave vanilla's. */
    public static Component name(PlayerInfo info, Component vanilla) {
        BetterTab t = active();
        if (t == null || vanilla == null || mc.player == null) return null;
        String name = info.getProfile().getName();
        int color;
        if (t.highlightSelf.get() && name.equals(mc.player.getGameProfile().getName())) color = Theme.accent();
        else if (t.highlightFriends.get() && Friends.isFriend(name)) color = t.friendColor.get();
        else return null;
        return vanilla.copy().withStyle(Style.EMPTY.withColor(TextColor.fromRgb(color & 0xFFFFFF)));
    }

    public static int pingColor(int ms) {
        if (ms < 0) return 0xFF9196A4;
        if (ms < 80) return 0xFF55E07A;
        if (ms < 150) return 0xFFB8E05A;
        if (ms < 300) return 0xFFFFC94A;
        if (ms < 600) return 0xFFFF9F43;
        return 0xFFFF5A5A;
    }

    /** Draws the ping as a number in the ping column; true = done, skip vanilla's bars. */
    public static boolean drawPing(GuiGraphics g, int width, int x, int y, PlayerInfo info) {
        BetterTab t = active();
        if (t == null || !t.numericPing.get()) return false;
        int ms = info.getLatency();
        String text = String.valueOf(ms);
        int scaledW = mc.font.width(text);
        // vanilla keeps 11 px for the bars; the number is right-aligned in the same corner
        g.pose().pushPose();
        g.pose().translate(x + width - 1 - scaledW * 0.75f, y + 2, 0);
        g.pose().scale(0.75f, 0.75f, 1);
        g.drawString(mc.font, text, 0, 0, pingColor(ms), true);
        g.pose().popPose();
        return true;
    }
}
