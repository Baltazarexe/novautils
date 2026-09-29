package dev.novautils;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** Shared identity for the mod: same mod id and logger on both Fabric and NeoForge. */
public final class NovaUtils {
    public static final String MOD_ID = "novautils";
    public static final Logger LOG = LoggerFactory.getLogger(MOD_ID);

    /**
     * Set once ModuleManager has finished building. Mixin hooks check it first: several of them can
     * fire during startup (block caches, loading-screen input, config-phase packets), and touching
     * ModuleManager there would force the module list and config to load before the game exists.
     */
    public static volatile boolean modulesReady;

    private NovaUtils() {}
}
