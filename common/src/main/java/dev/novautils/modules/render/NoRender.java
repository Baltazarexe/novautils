package dev.novautils.modules.render;

import dev.novautils.modules.Category;
import dev.novautils.modules.Module;
import dev.novautils.modules.ModuleManager;
import dev.novautils.settings.Setting;

/**
 * Hides screen effects that get in the way: the fire covering your screen while burning, the
 * camera shake when you take damage, and the big totem animation (in PvP areas it covers half the
 * screen at the worst moment). The hooks are in NoRenderMixin.
 */
public class NoRender extends Module {
    private final Setting<Boolean> fire = register(Setting.builder(Boolean.class, "fire-overlay")
        .description("No fire covering the screen while you burn.").defaultValue(true).build());

    private final Setting<Boolean> hurtCam = register(Setting.builder(Boolean.class, "hurt-camera")
        .description("No camera shake when you take damage.").defaultValue(true).build());

    private final Setting<Boolean> totem = register(Setting.builder(Boolean.class, "totem-animation")
        .description("No big totem animation when one pops.").defaultValue(true).build());

    public NoRender() {
        super("NoRender", "Hides fire overlay, hurt camera shake and the totem animation.", Category.RENDER);
    }

    private static NoRender active() {
        if (!dev.novautils.NovaUtils.modulesReady) return null;
        NoRender m = ModuleManager.get().get(NoRender.class);
        return m.isEnabled() ? m : null;
    }

    public static boolean hideFire() {
        NoRender m = active();
        return m != null && m.fire.get();
    }

    public static boolean hideHurtCam() {
        NoRender m = active();
        return m != null && m.hurtCam.get();
    }

    public static boolean hideTotem() {
        NoRender m = active();
        return m != null && m.totem.get();
    }
}
