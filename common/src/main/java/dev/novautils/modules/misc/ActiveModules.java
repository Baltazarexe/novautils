package dev.novautils.modules.misc;

import dev.novautils.gui.Theme;
import dev.novautils.modules.Category;
import dev.novautils.modules.HudModule;
import dev.novautils.modules.Module;
import dev.novautils.modules.ModuleManager;
import dev.novautils.settings.Setting;
import dev.novautils.utils.PlatformCompat;
import net.minecraft.client.gui.GuiGraphicsExtractor;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * On-screen list of your modules, so you can tell at a glance what is running. Drag it anywhere in
 * the HUD editor; lines align to whichever side of the screen it sits on.
 */
public class ActiveModules extends HudModule {
    public enum Sort { LENGTH, ALPHABETICAL, CATEGORY }

    private final Setting<Sort> sort = register(Setting.builder(Sort.class, "sort")
        .description("LENGTH gives the usual staircase look.")
        .defaultValue(Sort.LENGTH).build());

    private final Setting<Boolean> showDisabled = register(Setting.builder(Boolean.class, "show-disabled")
        .description("Also list modules that are turned off, dimmed.")
        .defaultValue(false).build());

    private final Setting<Boolean> hideHud = register(Setting.builder(Boolean.class, "hide-hud-modules")
        .description("Leave HUD elements (this list, HudInfo, ...) out of the list.")
        .defaultValue(true).build());

    private final Setting<Boolean> showKeybind = register(Setting.builder(Boolean.class, "show-keybind")
        .description("Show the bound key next to the name.")
        .defaultValue(false).build());

    private final Setting<Boolean> background = register(Setting.builder(Boolean.class, "background")
        .description("Draw a dark strip behind each line.")
        .defaultValue(true).build());

    private final Setting<Boolean> gradient = register(Setting.builder(Boolean.class, "gradient")
        .description("Fade from the active colour to the second colour down the list.")
        .defaultValue(true).build());

    private final Setting<Integer> activeColor = register(Setting.builder(Integer.class, "active-color")
        .description("Colour for modules that are on (top of the list when gradient is on).")
        .defaultValue(0xFF22D3EE).asColor().build());

    private final Setting<Integer> secondColor = register(Setting.builder(Integer.class, "second-color")
        .description("Bottom colour of the gradient.")
        .defaultValue(0xFF8B5CF6).asColor()
        .visible(() -> gradient.get()).build());

    private final Setting<Integer> disabledColor = register(Setting.builder(Integer.class, "disabled-color")
        .description("Colour for modules that are off.")
        .defaultValue(0xFF6E6E82).asColor()
        .visible(() -> showDisabled.get()).build());

    public ActiveModules() {
        super("ActiveModules", "Lists your modules on screen so you can see what is on.", Category.MISC);
    }

    @Override
    protected int defaultX(int screenW, int w) {
        return screenW - w - 3;
    }

    @Override
    protected int defaultY(int screenH, int h) {
        return 2;
    }

    private String label(Module module) {
        if (!showKeybind.get() || module.getKeyBind() == -1) return module.name;
        return module.name + " [" + PlatformCompat.keyDisplayName(module.getKeyBind()) + "]";
    }

    @Override
    protected int[] render(GuiGraphicsExtractor g, int x, int y, boolean right) {
        List<Module> list = new ArrayList<>();
        for (Module module : ModuleManager.get().getModules()) {
            if (hideHud.get() && module instanceof HudModule) continue;
            if (module.isEnabled() || showDisabled.get()) list.add(module);
        }
        if (list.isEmpty()) {
            if (!editing) return new int[]{0, 0};
            String sample = "Esp";
            g.text(mc.font, sample, x, y + 1, 0x8022D3EE);
            return new int[]{mc.font.width(sample), mc.font.lineHeight + 2};
        }

        Comparator<Module> order = switch (sort.get()) {
            case LENGTH -> Comparator.comparingInt((Module m) -> mc.font.width(label(m))).reversed();
            case ALPHABETICAL -> Comparator.comparing(m -> m.name);
            case CATEGORY -> Comparator.<Module, String>comparing(m -> m.category.name()).thenComparing(m -> m.name);
        };
        // Enabled first, so turning something off drops it to the dimmed part of the list.
        list.sort(Comparator.comparing((Module m) -> !m.isEnabled()).thenComparing(order));

        int blockW = 0;
        for (Module m : list) blockW = Math.max(blockW, mc.font.width(label(m)));

        int lineH = mc.font.lineHeight + 2;
        int enabledCount = 0;
        for (Module m : list) if (m.isEnabled()) enabledCount++;

        for (int i = 0; i < list.size(); i++) {
            Module module = list.get(i);
            String text = label(module);
            int w = mc.font.width(text);
            int lx = right ? x + blockW - w : x;
            int ly = y + i * lineH;

            int color;
            if (!module.isEnabled()) {
                color = disabledColor.get();
            } else if (gradient.get() && enabledCount > 1) {
                color = Theme.mix(activeColor.get(), secondColor.get(), i / (float) (enabledCount - 1));
            } else {
                color = activeColor.get();
            }

            if (background.get()) {
                g.fill(lx - 2, ly - 1, lx + w + 2, ly + lineH - 1, 0x80101018);
                // accent edge on the side the list is anchored to
                if (right) g.fill(lx + w + 2, ly - 1, lx + w + 3, ly + lineH - 1, color);
                else g.fill(lx - 3, ly - 1, lx - 2, ly + lineH - 1, color);
            }
            g.text(mc.font, text, lx, ly + 1, color);
        }
        return new int[]{blockW, list.size() * lineH};
    }
}
