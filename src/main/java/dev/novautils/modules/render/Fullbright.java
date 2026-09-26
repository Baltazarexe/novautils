package dev.novautils.modules.render;

import dev.novautils.modules.Category;
import dev.novautils.modules.Module;

/**
 * Actual rendering happens in {@link dev.novautils.mixin.LightmapMixin}, which checks
 * {@code ModuleManager.get().isActive(Fullbright.class)} and clears the lightmap texture
 * to full brightness when active.
 */
public class Fullbright extends Module {
    public Fullbright() {
        super("Fullbright", "Makes everything fully lit, no more caves or night.", Category.RENDER);
    }
}
