package dev.novautils.modules.misc;

import dev.novautils.modules.Category;
import dev.novautils.modules.Module;
import dev.novautils.modules.ModuleManager;
import dev.novautils.settings.Setting;
import net.minecraft.client.GuiMessage;
import net.minecraft.network.chat.Component;

import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.List;

/**
 * Chat that's easier to live with on a busy server: a time stamp on every line, the same message
 * repeated back to back collapsed into one line with a counter ("(x5)") instead of flooding, and
 * a much longer history to scroll back through. Hooks are in ChatComponentMixin.
 */
public class BetterChat extends Module {
    private static final DateTimeFormatter TIME = DateTimeFormatter.ofPattern("HH:mm");

    private final Setting<Boolean> timestamps = register(Setting.builder(Boolean.class, "timestamps")
        .description("Show the time before each message.").defaultValue(true).build());

    private final Setting<Boolean> antiSpam = register(Setting.builder(Boolean.class, "anti-spam")
        .description("Collapse repeated messages into one line with a counter.").defaultValue(true).build());

    private final Setting<Integer> history = register(Setting.builder(Integer.class, "history")
        .description("How many messages chat remembers (vanilla: 100).").defaultValue(1000).range(100, 5000).build());

    private String lastText;
    private int repeats;
    private static List<GuiMessage> seenHistory;

    /** The chat history list last seen by the hook (for tests). */
    public static List<GuiMessage> history() {
        return seenHistory;
    }

    public BetterChat() {
        super("BetterChat", "Timestamps, anti-spam stacking and longer chat history.", Category.MISC);
    }

    private static BetterChat active() {
        if (!dev.novautils.NovaUtils.modulesReady) return null;
        BetterChat b = ModuleManager.get().get(BetterChat.class);
        return b.isEnabled() ? b : null;
    }

    /** ChatComponentMixin: rewrite an incoming line; may remove the previous copy of it. */
    public static Component process(Component message, List<GuiMessage> all, Runnable refresh) {
        BetterChat b = active();
        if (b == null) return message;
        String text = message.getString();
        seenHistory = all;

        Component out = message;
        if (b.antiSpam.get()) {
            if (text.equals(b.lastText) && !all.isEmpty()) {
                b.repeats++;
                all.remove(0);
                refresh.run();
                out = message.copy().append(Component.literal(" (x" + b.repeats + ")").withStyle(s_ -> s_.withColor(0x8A8A9A)));
            } else {
                b.lastText = text;
                b.repeats = 1;
            }
        }
        if (b.timestamps.get()) {
            out = Component.literal("[" + LocalTime.now().format(TIME) + "] ").withStyle(s_ -> s_.withColor(0x6E6E82)).append(out);
        }
        return out;
    }

    /** ChatComponentMixin: replaces vanilla's 100-message limit. */
    public static int historyLimit(int vanilla) {
        BetterChat b = active();
        return b == null ? vanilla : b.history.get();
    }
}
