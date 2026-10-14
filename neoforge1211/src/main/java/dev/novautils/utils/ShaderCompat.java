package dev.novautils.utils;

import java.lang.reflect.Method;

/**
 * Is a shaderpack running (Iris)? Asked through Iris' public API by reflection, so the mod neither
 * needs Iris nor breaks without it. With a shaderpack on, Iris takes over world rendering and drops
 * our see-through geometry, so boxes and lines switch to screen space (ScreenSpace) instead.
 */
public final class ShaderCompat {
    private ShaderCompat() {}

    private static boolean looked;
    private static Object api;
    private static Method inUse;

    public static boolean shadersActive() {
        if (!looked) {
            looked = true;
            try {
                Class<?> c = Class.forName("net.irisshaders.iris.api.v0.IrisApi");
                api = c.getMethod("getInstance").invoke(null);
                inUse = c.getMethod("isShaderPackInUse");
            } catch (ReflectiveOperationException | LinkageError e) {
                api = null;
            }
        }
        if (api == null) return false;
        try {
            return (Boolean) inUse.invoke(api);
        } catch (ReflectiveOperationException e) {
            return false;
        }
    }
}
