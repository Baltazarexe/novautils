package dev.novautils.modules.misc;

import com.mojang.blaze3d.platform.InputConstants;
import dev.novautils.modules.Category;
import dev.novautils.modules.Module;
import dev.novautils.modules.ModuleManager;
import dev.novautils.settings.Setting;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.gui.GuiGraphicsExtractor;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/** On-screen list of your modules, so you can tell at a glance what is running. */
public class ActiveModules extends Module {
    public enum Corner { TOP_RIGHT, TOP_LEFT, BOTTOM_RIGHT, BOTTOM_LEFT }

    public enum Sort { LENGTH, ALPHABETICAL, CATEGORY }

    private final Setting<Corner> corner = register(Setting.builder(Corner.class, "corner")
        .description("Which corner of the screen to draw in.")
        .defaultValue(Corner.TOP_RIGHT).build());

    private final Setting<Sort> sort = register(Setting.builder(Sort.class, "sort")
        .description("LENGTH gives the usual staircase look.")
        .defaultValue(Sort.LENGTH).build());

    private final Setting<Boolean> showDisabled = register(Setting.builder(Boolean.class, "show-disabled")
        .description("Also list modules that are turned off, dimmed.")
        .defaultValue(false).build());

    private final Setting<Boolean> showKeybind = register(Setting.builder(Boolean.class, "show-keybind")
        .description("Show the bound key next to the name.")
        .defaultValue(false).build());

    private final Setting<Boolean> background = register(Setting.builder(Boolean.class, "background")
        .description("Draw a dark strip behind each line.")
        .defaultValue(true).build());

    private final Setting<Integer> activeColor = register(Setting.builder(Integer.class, "active-color")
        .description("Colour for modules that are on.")
        .defaultValue(0xFF7C5CFF).asColor().build());

    private final Setting<Integer> disabledColor = register(Setting.builder(Integer.class, "disabled-color")
        .description("Colour for modules that are off.")
        .defaultValue(0xFF6E6E82).asColor().build());

    private int lastHeight;

    public ActiveModules() {
        super("ActiveModules", "Lists your modules on screen so you can see what is on.", Category.MISC);
    }

    /** Pixels this list used last frame in that corner, so other HUDs can move out of the way. */
    public int heightIn(Corner where) {
        return isEnabled() && corner.get() == where ? lastHeight : 0;
    }

    private String label(Module module) {
        if (!showKeybind.get() || module.getKeyBind() == -1) return module.name;
        String key = InputConstants.Type.KEYBOARD.getOrCreate(module.getKeyBind()).getDisplayName().getString();
        return module.name + " [" + key + "]";
    }

    @Override
    public void onRenderHud(GuiGraphicsExtractor g, DeltaTracker deltaTracker) {
        List<Module> list = new ArrayList<>();
        for (Module module : ModuleManager.get().getModules()) {
            if (module.isEnabled() || showDisabled.get()) list.add(module);
        }
        lastHeight = list.size() * (mc.font.lineHeight + 2) + 2;
        if (list.isEmpty()) return;

        Comparator<Module> order = switch (sort.get()) {
            case LENGTH -> Comparator.comparingInt((Module m) -> mc.font.width(label(m))).reversed();
            case ALPHABETICAL -> Comparator.comparing(m -> m.name);
            case CATEGORY -> Comparator.<Module, String>comparing(m -> m.category.name()).thenComparing(m -> m.name);
        };
        // Enabled first, so turning something off drops it to the dimmed part of the list.
        list.sort(Comparator.comparing((Module m) -> !m.isEnabled()).thenComparing(order));

        Corner where = corner.get();
        boolean right = where == Corner.TOP_RIGHT || where == Corner.BOTTOM_RIGHT;
        boolean bottom = where == Corner.BOTTOM_LEFT || where == Corner.BOTTOM_RIGHT;

        int lineH = mc.font.lineHeight + 2;
        int y = bottom ? g.guiHeight() - 4 - list.size() * lineH : 2;

        for (Module module : list) {
            String text = label(module);
            int w = mc.font.width(text);
            int x = right ? g.guiWidth() - 3 - w : 3;
            int color = module.isEnabled() ? activeColor.get() : disabledColor.get();

            if (background.get()) {
                g.fill(x - 2, y - 1, x + w + 2, y + lineH - 1, 0x80101018);
                // accent edge on the side the list is anchored to
                if (right) g.fill(x + w + 2, y - 1, x + w + 3, y + lineH - 1, color);
                else g.fill(x - 3, y - 1, x - 2, y + lineH - 1, color);
            }

            g.text(mc.font, text, x, y + 1, color);
            y += lineH;
        }
    }
}
