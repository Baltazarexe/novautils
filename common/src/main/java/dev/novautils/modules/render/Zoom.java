package dev.novautils.modules.render;

import dev.novautils.modules.Category;
import dev.novautils.modules.Module;
import dev.novautils.settings.Setting;
import dev.novautils.utils.PlatformCompat;

/**
 * Zoom that doesn't touch your FOV setting: CameraMixin divides the final field of view, so
 * nothing is ever saved zoomed-in. Smooth in/out, scroll to zoom further, optional cinematic
 * camera, and "hold" mode for the classic press-and-hold zoom key.
 */
public class Zoom extends Module {
    private final Setting<Double> zoom = register(Setting.builder(Double.class, "zoom")
        .description("How many times closer.")
        .defaultValue(4.0).range(1.5, 30.0).build());

    private final Setting<Boolean> hold = register(Setting.builder(Boolean.class, "hold")
        .description("Only zoom while the keybind is held down.")
        .defaultValue(true).build());

    private final Setting<Boolean> scroll = register(Setting.builder(Boolean.class, "scroll")
        .description("Scroll wheel zooms in and out while zoomed.")
        .defaultValue(true).build());

    private final Setting<Boolean> smooth = register(Setting.builder(Boolean.class, "smooth")
        .description("Animate in and out.")
        .defaultValue(true).build());

    private final Setting<Boolean> cinematic = register(Setting.builder(Boolean.class, "cinematic")
        .description("Smooth, slower mouse while zoomed.")
        .defaultValue(false).build());

    private double level;
    private float current = 1f;
    private long lastFrame;
    private boolean savedSmoothCamera;

    public Zoom() {
        super("Zoom", "Zoom in (hold the keybind), scroll to zoom further.", Category.RENDER);
    }

    @Override
    public void onEnable() {
        level = zoom.get();
        savedSmoothCamera = mc.options.smoothCamera;
        if (cinematic.get()) mc.options.smoothCamera = true;
    }

    @Override
    public void onDisable() {
        mc.options.smoothCamera = savedSmoothCamera;
    }

    @Override
    public void onTick() {
        if (hold.get() && getKeyBind() != -1 && PlatformCompat.currentScreen(mc) == null
            && !dev.novautils.utils.Keybinds.isDown(mc, getKeyBind())) {
            setEnabled(false);
        }
    }

    /** MouseHandlerMixin; true = the scroll was used for zooming. */
    public boolean onScroll(double amount) {
        if (!scroll.get() || amount == 0) return false;
        level = Math.max(1.0, Math.min(50.0, level * (amount > 0 ? 1.15 : 1 / 1.15)));
        return true;
    }

    /** CameraMixin: multiply the FOV by this. Called every frame, also while off (to zoom out). */
    public float fovFactor(float partialTick) {
        float target = isEnabled() ? (float) (1.0 / level) : 1f;
        long now = System.nanoTime();
        float dt = lastFrame == 0 ? 0f : Math.min(0.1f, (now - lastFrame) / 1_000_000_000f);
        lastFrame = now;
        if (!smooth.get()) current = target;
        else current += (target - current) * Math.min(1f, dt * 14f);
        if (Math.abs(current - target) < 0.001f) current = target;
        return current;
    }
}
