package dev.novautils.modules.render;

import dev.novautils.modules.Category;
import dev.novautils.modules.Module;
import dev.novautils.settings.Setting;

/**
 * The flash a creature gets when it's hit: any colour and strength, or none at all. Rewrites the
 * game's small overlay texture (the "red" half of it), so it covers every mob and player model.
 */
public class HitColor extends Module {
    private static final int VANILLA = 0xB2FF0000;

    private final Setting<Integer> color = register(Setting.builder(Integer.class, "color")
        .description("Hit flash colour; the alpha is how strong it is (0 = no flash).").defaultValue(0xB255E6FF).asColor().build());

    private int applied = VANILLA;

    public HitColor() {
        super("HitColor", "Change (or remove) the red flash when something takes damage.", Category.RENDER);
    }

    @Override
    public void onTick() {
        if (color.get() != applied) apply(color.get());
    }

    @Override
    public void onDisable() {
        apply(VANILLA);
    }

    private void apply(int argb) {
        try {
            var tex = ((dev.novautils.mixin.OverlayTextureAccessor) mc.gameRenderer.overlayTexture()).novautils$texture();
            var img = tex.getPixels();
            if (img == null) return;
            for (int y = 0; y < 8; y++) for (int x = 0; x < 16; x++) img.setPixel(x, y, argb);
            tex.upload();
            applied = argb;
        } catch (Throwable t) {
            dev.novautils.NovaUtils.LOG.warn("HitColor could not change the overlay texture", t);
            applied = argb;
        }
    }
}
