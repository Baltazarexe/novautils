package dev.novautils.modules.misc;

import dev.novautils.gui.Theme;
import dev.novautils.modules.Category;
import dev.novautils.modules.Module;
import dev.novautils.settings.Setting;

/**
 * The colour of the menus and the HUD. Pick a preset, your own colour (the strip at the top of the
 * Classic menu sets it too) or let it cycle through the rainbow. It only holds settings, so it is
 * read whether or not the module is switched on.
 */
public class ThemeSettings extends Module {
    public enum Preset {
        ORANGE(0xFFEC812C), NOVA(0xFF8B9CFF, 0xFFC49BFF), RED(0xFFEF4444), PINK(0xFFEC4899), PURPLE(0xFFA855F7),
        BLUE(0xFF3B82F6), CYAN(0xFF22D3EE), GREEN(0xFF22C55E), YELLOW(0xFFFACC15), WHITE(0xFFE8E8E8),
        RAINBOW(0), CUSTOM(0);

        final int color, color2;

        Preset(int color) {
            this(color, 0);
        }

        Preset(int color, int color2) {
            this.color = color;
            this.color2 = color2;
        }
    }

    private static ThemeSettings instance;

    private final Setting<Preset> preset = register(Setting.builder(Preset.class, "preset")
        .description("Colour of the menus and the HUD. NOVA is the original periwinkle, RAINBOW cycles, CUSTOM uses the colour below.")
        .defaultValue(Preset.ORANGE).build());

    private final Setting<Integer> custom = register(Setting.builder(Integer.class, "custom-color")
        .description("Your own colour (also set by the strip at the top of the Classic menu).").defaultValue(0xFFEC812C).asColor()
        .visible(() -> preset.get() == Preset.CUSTOM).build());

    private final Setting<Double> rainbowSpeed = register(Setting.builder(Double.class, "rainbow-speed")
        .description("How fast the rainbow cycles.").defaultValue(0.12).range(0.02, 0.6)
        .visible(() -> preset.get() == Preset.RAINBOW).build());

    public ThemeSettings() {
        super("Theme", "Colour of the menus and the HUD: presets, your own colour or rainbow.", Category.MISC);
        instance = this;
        setEnabled(true);
    }

    /** Main accent (ARGB). */
    public static int accent() {
        ThemeSettings t = instance;
        if (t == null) return Preset.ORANGE.color;
        return switch (t.preset.get()) {
            case RAINBOW -> hue(0f, t);
            case CUSTOM -> 0xFF000000 | t.custom.get();
            default -> t.preset.get().color;
        };
    }

    /** Second end of the gradients: a neighbouring hue of the accent. */
    public static int accent2() {
        ThemeSettings t = instance;
        if (t == null) return shifted(Preset.ORANGE.color);
        Preset p = t.preset.get();
        if (p == Preset.RAINBOW) return hue(0.12f, t);
        if (p.color2 != 0) return p.color2;
        return shifted(accent());
    }

    /** Switches to CUSTOM with this colour (the Classic menu's colour strip). */
    public static void setCustom(int rgb) {
        ThemeSettings t = instance;
        if (t == null) return;
        t.custom.set(0xFF000000 | rgb);
        t.preset.set(Preset.CUSTOM);
        dev.novautils.modules.ModuleManager.get().markDirty();
    }

    public static void reset() {
        ThemeSettings t = instance;
        if (t == null) return;
        t.preset.set(Preset.ORANGE);
        dev.novautils.modules.ModuleManager.get().markDirty();
    }

    private static int hue(float offset, ThemeSettings t) {
        float h = (Theme.time() * t.rainbowSpeed.get().floatValue() + offset) % 1f;
        return 0xFF000000 | (java.awt.Color.HSBtoRGB(h, 0.65f, 1f) & 0xFFFFFF);
    }

    private static int shifted(int c) {
        float[] hsb = java.awt.Color.RGBtoHSB(c >> 16 & 255, c >> 8 & 255, c & 255, null);
        return 0xFF000000 | (java.awt.Color.HSBtoRGB((hsb[0] + 0.08f) % 1f, hsb[1] * 0.75f, Math.max(0.95f, hsb[2])) & 0xFFFFFF);
    }
}
