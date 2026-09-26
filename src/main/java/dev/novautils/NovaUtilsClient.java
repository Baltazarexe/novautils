package dev.novautils;

import net.fabricmc.api.ClientModInitializer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class NovaUtilsClient implements ClientModInitializer {
    public static final String MOD_ID = "novautils";
    public static final Logger LOG = LoggerFactory.getLogger(MOD_ID);

    @Override
    public void onInitializeClient() {
        LOG.info("NovaUtils loading...");
    }
}
