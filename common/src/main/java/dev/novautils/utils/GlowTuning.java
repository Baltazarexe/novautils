package dev.novautils.utils;

import net.minecraft.client.Minecraft;

import java.lang.reflect.Field;
import java.util.Map;
import java.util.Optional;

/**
 * Esp GLOW sliders. The values reach the outline shader when the post chain is built (PostPassMixin),
 * so when a slider moves, the cached chains are dropped and rebuilt on their next use. 26.1.2 and
 * 26.2 build the chain fresh every time, so there is nothing to drop there.
 */
public final class GlowTuning {
    private static float spread = 1f, strength = 1f;
    private static boolean failed;

    private GlowTuning() {}

    public static void update(float newSpread, float newStrength) {
        if (newSpread == spread && newStrength == strength) return;
        spread = newSpread;
        strength = newStrength;
        dropCachedChains();
    }

    @SuppressWarnings("unchecked")
    private static void dropCachedChains() {
        if (failed) return;
        try {
            Object manager = Minecraft.getInstance().getShaderManager();
            Field cacheField;
            try {
                cacheField = manager.getClass().getDeclaredField("postChains");
            } catch (NoSuchFieldException e) {
                return; // this version builds the chain on every use
            }
            cacheField.setAccessible(true);
            Object cache = cacheField.get(manager);
            if (cache == null) return;
            Field mapField = cache.getClass().getDeclaredField("postChains");
            mapField.setAccessible(true);
            Map<Object, Optional<AutoCloseable>> chains = (Map<Object, Optional<AutoCloseable>>) mapField.get(cache);
            for (Optional<AutoCloseable> chain : chains.values()) {
                if (chain.isPresent()) chain.get().close();
            }
            chains.clear();
        } catch (Throwable t) {
            failed = true;
            dev.novautils.NovaUtils.LOG.warn("Glow sliders: could not refresh the outline chain", t);
        }
    }
}
