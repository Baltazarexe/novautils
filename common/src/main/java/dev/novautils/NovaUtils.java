package dev.novautils;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** Shared identity for the mod: same mod id and logger on both Fabric and NeoForge. */
public final class NovaUtils {
    public static final String MOD_ID = "novautils";
    public static final Logger LOG = LoggerFactory.getLogger(MOD_ID);

    private NovaUtils() {}
}
