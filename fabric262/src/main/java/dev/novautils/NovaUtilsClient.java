package dev.novautils;

import com.mojang.blaze3d.platform.InputConstants;
import dev.novautils.fabric.FabricRenderContext;
import dev.novautils.fabric.FabricWaypointCommands;
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
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;

public class NovaUtilsClient implements ClientModInitializer {
    private static final boolean DEBUG_MENU = System.getProperty("novautils.debugmenu") != null;

    @Override
    public void onInitializeClient() {
        NovaUtils.LOG.info("NovaUtils loading (Fabric, MC 26.2)...");

        // Constructing ModuleManager registers every module and loads the saved config.
        ModuleManager manager = ModuleManager.get();

        KeyMapping openMenu = KeyMappingHelper.registerKeyMapping(new KeyMapping(
            "key.novautils.openmenu", InputConstants.Type.KEYSYM, InputConstants.KEY_RSHIFT, KeyMapping.Category.MISC));

        KeyMapping panic = KeyMappingHelper.registerKeyMapping(new KeyMapping(
            "key.novautils.panic", InputConstants.Type.KEYSYM, InputConstants.KEY_DELETE, KeyMapping.Category.MISC));

        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            manager.tick();
            while (panic.consumeClick()) {
                int disabled = manager.disableAll();
                if (client.player != null) {
                    client.player.sendSystemMessage(Component.literal("[NovaUtils] Panic: disabled " + disabled + " modules"));
                }
            }
            if (DEBUG_MENU && client.gui.screen() instanceof TitleScreen) {
                client.gui.setScreen(new ClickGuiScreen());
            }
            while (openMenu.consumeClick()) {
                if (client.gui.screen() == null) client.gui.setScreen(dev.novautils.gui.GuiState.get().newMenu());
            }
        });

        LevelRenderEvents.COLLECT_SUBMITS.register(context -> manager.renderWorld(new FabricRenderContext(context)));
        HudElementRegistry.addLast(Identifier.fromNamespaceAndPath(NovaUtils.MOD_ID, "hud"), manager::renderHud);
        FabricWaypointCommands.register();

        // Autosave runs on a timer in ModuleManager#tick; these are the "don't lose it" moments.
        ClientLifecycleEvents.CLIENT_STOPPING.register(client -> manager.saveIfDirty());
        ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> manager.saveIfDirty());

        NovaUtils.LOG.info("NovaUtils loaded, {} modules registered.", manager.getModules().size());
    }
}
