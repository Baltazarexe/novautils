package dev.novautils.modules.render;

import dev.novautils.modules.Category;
import dev.novautils.modules.Module;
import dev.novautils.settings.Setting;

/** Temporarily lowers the FOV option while active, restoring it on disable. Public API only, no mixin. */
public class Zoom extends Module {
    private final Setting<Integer> zoomFov = register(Setting.builder(Integer.class, "zoom-fov")
        .description("Field of view to use while zoomed in.")
        .defaultValue(20)
        .range(5, 90)
        .build());

    private Integer previousFov;

    public Zoom() {
        super("Zoom", "Reduces your field of view to zoom in.", Category.RENDER);
    }

    @Override
    public void onEnable() {
        previousFov = mc.options.fov().get();
        mc.options.fov().set(zoomFov.get());
    }

    @Override
    public void onDisable() {
        if (previousFov != null) {
            mc.options.fov().set(previousFov);
            previousFov = null;
        }
    }

    @Override
    public void onTick() {
        if (!mc.options.fov().get().equals(zoomFov.get())) {
            mc.options.fov().set(zoomFov.get());
        }
    }
}
