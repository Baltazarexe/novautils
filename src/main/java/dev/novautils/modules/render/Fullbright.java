package dev.novautils.modules.render;

import dev.novautils.modules.Category;
import dev.novautils.modules.Module;
import dev.novautils.settings.Setting;

/**
 * Lights the world up. The work happens in {@link dev.novautils.mixin.LightmapMixin}, which raises
 * the values vanilla uploads for the lightmap.
 */
public class Fullbright extends Module {
    public enum Mode {
        /** Max out the lightmap itself: flat, very bright. */
        LIGHTMAP,
        /** Reuse the night vision path: keeps a bit more of the original shading. */
        NIGHT_VISION
    }

    private final Setting<Mode> mode = register(Setting.builder(Mode.class, "mode")
        .description("LIGHTMAP is brighter, NIGHT_VISION keeps more shading.")
        .defaultValue(Mode.NIGHT_VISION).build());

    public Fullbright() {
        super("Fullbright", "Makes everything fully lit, no more caves or night.", Category.RENDER);
    }

    public boolean useNightVision() {
        return mode.get() == Mode.NIGHT_VISION;
    }
}
