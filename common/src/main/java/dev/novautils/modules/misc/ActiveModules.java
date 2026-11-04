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
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * On-screen list of your modules, so you can tell at a glance what is running. Drag it anywhere in
 * the HUD editor; lines align to whichever side of the screen it sits on. Lines slide in when a
 * module turns on, slide out when it turns off, and the rest glide into their new places.
 */
public class ActiveModules extends HudModule {
    public enum Sort { LENGTH, ALPHABETICAL, CATEGORY }
    public enum ColorMode { THEME, COLORS, RAINBOW, BY_CATEGORY }

    private final Setting<ColorMode> colorMode = register(Setting.builder(ColorMode.class, "color-mode")
        .description("THEME: the Theme module's colours. COLORS: your two colours. RAINBOW: cycles. BY_CATEGORY: one colour per category.")
        .defaultValue(ColorMode.THEME).build());

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

    private final Setting<Boolean> animate = register(Setting.builder(Boolean.class, "animate")
        .description("Slide lines in and out as modules turn on and off.")
        .defaultValue(true).build());

    private final Setting<Boolean> background = register(Setting.builder(Boolean.class, "background")
        .description("Draw a dark strip behind each line.")
        .defaultValue(true).build());

    private final Setting<Boolean> gradient = register(Setting.builder(Boolean.class, "gradient")
        .description("Fade from the first colour to the second down the list.")
        .defaultValue(true).visible(() -> colorMode.get() == ColorMode.COLORS || colorMode.get() == ColorMode.THEME).build());

    private final Setting<Integer> activeColor = register(Setting.builder(Integer.class, "active-color")
        .description("Colour for modules that are on (top of the list when gradient is on).")
        .defaultValue(0xFF22D3EE).asColor().visible(() -> colorMode.get() == ColorMode.COLORS).build());

    private final Setting<Integer> secondColor = register(Setting.builder(Integer.class, "second-color")
        .description("Bottom colour of the gradient.")
        .defaultValue(0xFF8B5CF6).asColor()
        .visible(() -> colorMode.get() == ColorMode.COLORS && gradient.get()).build());

    private final Setting<Integer> disabledColor = register(Setting.builder(Integer.class, "disabled-color")
        .description("Colour for modules that are off.")
        .defaultValue(0xFF6E6E82).asColor()
        .visible(() -> showDisabled.get()).build());

    /** Per module: how far its line is shown (0..1) and its animated y offset in the block. */
    private final Map<Module, Float> shown = new HashMap<>();
    private final Map<Module, Float> rowY = new HashMap<>();
    private long lastFrame = System.nanoTime();

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

    private static int categoryColor(Module m) {
        return switch (m.category) {
            case RENDER -> 0xFF8B9CFF;
            case MOVEMENT -> 0xFF66E07A;
            case COMBAT -> 0xFFFF7A7A;
            case PLAYER -> 0xFFFFC94A;
            case WORLD -> 0xFF55E6FF;
            case MISC -> 0xFFC49BFF;
        };
    }

    private String label(Module module) {
        if (!showKeybind.get() || module.getKeyBind() == -1) return module.name;
        return module.name + " [" + PlatformCompat.keyDisplayName(module.getKeyBind()) + "]";
    }

    @Override
    protected int[] render(GuiGraphicsExtractor g, int x, int y, boolean right) {
        long now = System.nanoTime();
        float k = animate.get() ? Math.min(1f, Math.min(0.1f, (now - lastFrame) / 1e9f) * 12f) : 1f;
        lastFrame = now;

        // which lines are wanted, and how far each one has slid in or out
        List<Module> list = new ArrayList<>();
        for (Module module : ModuleManager.get().getModules()) {
            boolean want = !(hideHud.get() && module instanceof HudModule) && (module.isEnabled() || showDisabled.get());
            float p = shown.getOrDefault(module, 0f);
            p += ((want ? 1f : 0f) - p) * k;
            if (want && p > 0.995f) p = 1f;
            if (!want && p < 0.02f) p = 0f;
            if (p > 0f) {
                shown.put(module, p);
                list.add(module);
            } else {
                shown.remove(module);
                rowY.remove(module);
            }
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
        // Enabled first, so turning something off drops it to the dimmed part of the list. A line
        // that is sliding out keeps its place until it's gone.
        list.sort(Comparator.comparing((Module m) -> !(m.isEnabled() || !showDisabled.get())).thenComparing(order));

        int blockW = 0;
        for (Module m : list) blockW = Math.max(blockW, mc.font.width(label(m)));

        int lineH = mc.font.lineHeight + 2;
        int onCount = 0;
        for (Module m : list) if (m.isEnabled() || !showDisabled.get()) onCount++;

        float offset = 0f;
        for (int i = 0; i < list.size(); i++) {
            Module module = list.get(i);
            float p = shown.get(module);
            float ry = rowY.getOrDefault(module, offset);
            ry += (offset - ry) * k;
            rowY.put(module, ry);
            offset += p * lineH;

            String text = label(module);
            int w = mc.font.width(text);
            int slide = Math.round((1f - p) * (w + 8)) * (right ? 1 : -1);
            int lx = (right ? x + blockW - w : x) + slide;
            int ly = y + Math.round(ry);

            int color;
            boolean on = module.isEnabled() || !showDisabled.get();
            float t = onCount > 1 ? Math.min(1f, i / (float) (onCount - 1)) : 0f;
            if (!on) {
                color = disabledColor.get();
            } else if (colorMode.get() == ColorMode.RAINBOW) {
                float hue = ((System.currentTimeMillis() % 5000) / 5000f) + i * 0.04f;
                color = 0xFF000000 | (java.awt.Color.HSBtoRGB(hue, 0.55f, 1f) & 0xFFFFFF);
            } else if (colorMode.get() == ColorMode.BY_CATEGORY) {
                color = categoryColor(module);
            } else if (colorMode.get() == ColorMode.THEME) {
                color = gradient.get() ? Theme.mix(Theme.accent(), Theme.accent2(), t) : Theme.accent();
            } else if (gradient.get()) {
                color = Theme.mix(activeColor.get(), secondColor.get(), t);
            } else {
                color = activeColor.get();
            }
            color = Theme.alpha(color, p);

            if (background.get()) {
                g.fill(lx - 2, ly - 1, lx + w + 2, ly + lineH - 1, Theme.alpha(0x80101018, p));
                // accent edge on the side the list is anchored to
                if (right) g.fill(lx + w + 2, ly - 1, lx + w + 3, ly + lineH - 1, color);
                else g.fill(lx - 3, ly - 1, lx - 2, ly + lineH - 1, color);
            }
            if (p > 0.08f) g.text(mc.font, text, lx, ly + 1, color);
        }
        return new int[]{blockW, Math.max(lineH, Math.round(offset))};
    }
}
