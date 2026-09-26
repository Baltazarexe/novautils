package dev.novautils;

import com.mojang.blaze3d.platform.InputConstants;
import dev.novautils.gui.screens.ClickGuiScreen;
import dev.novautils.modules.ModuleManager;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientLifecycleEvents;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderEvents;
import net.minecraft.client.KeyMapping;
import net.minecraft.resources.Identifier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class NovaUtilsClient implements ClientModInitializer {
    public static final String MOD_ID = "novautils";
    public static final Logger LOG = LoggerFactory.getLogger(MOD_ID);
    private static final boolean DEBUG_MENU = System.getProperty("novautils.debugmenu") != null;

    @Override
    public void onInitializeClient() {
        LOG.info("NovaUtils loading...");

        // Constructing ModuleManager registers every module and loads the saved config.
        ModuleManager manager = ModuleManager.get();

        KeyMapping openMenu = KeyMappingHelper.registerKeyMapping(new KeyMapping(
            "key.novautils.openmenu", InputConstants.Type.KEYBOARD, InputConstants.KEY_RSHIFT, KeyMapping.Category.MISC));

        KeyMapping panic = KeyMappingHelper.registerKeyMapping(new KeyMapping(
            "key.novautils.panic", InputConstants.Type.KEYBOARD, InputConstants.KEY_DELETE, KeyMapping.Category.MISC));

        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            manager.tick();
            while (panic.consumeClick()) {
                int disabled = manager.disableAll();
                if (client.player != null) {
                    client.player.sendSystemMessage(net.minecraft.network.chat.Component.literal(
                        "[NovaUtils] Panic: disabled " + disabled + " modules"));
                }
            }
            if (DEBUG_MENU && client.gui.screen() instanceof net.minecraft.client.gui.screens.TitleScreen) {
                LOG.info("[debug] opening ClickGuiScreen from title screen");
                client.gui.setScreen(new ClickGuiScreen());
            }
            while (openMenu.consumeClick()) {
                LOG.info("[debug] menu key pressed, screen={}", client.gui.screen());
                if (client.gui.screen() == null) client.gui.setScreen(new ClickGuiScreen());
            }
        });
        LevelRenderEvents.BEFORE_TRANSLUCENT_TERRAIN.register(manager::renderWorld);
        HudElementRegistry.addLast(Identifier.fromNamespaceAndPath(MOD_ID, "hud"), manager::renderHud);
        // Autosave runs on a timer in ModuleManager#tick; these are the "don't lose it" moments.
        ClientLifecycleEvents.CLIENT_STOPPING.register(client -> manager.saveIfDirty());
        ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> manager.saveIfDirty());

        LOG.info("NovaUtils loaded, {} modules registered.", manager.getModules().size());
    }
}
