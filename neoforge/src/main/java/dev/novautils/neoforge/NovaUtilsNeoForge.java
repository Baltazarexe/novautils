package dev.novautils.neoforge;

import com.mojang.blaze3d.platform.InputConstants;
import dev.novautils.NovaUtils;
import dev.novautils.gui.screens.ClickGuiScreen;
import dev.novautils.modules.ModuleManager;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.event.lifecycle.FMLClientSetupEvent;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.RegisterClientCommandsEvent;
import net.neoforged.neoforge.client.event.RegisterGuiLayersEvent;
import net.neoforged.neoforge.client.event.RegisterKeyMappingsEvent;
import net.neoforged.neoforge.client.event.SubmitCustomGeometryEvent;
import net.neoforged.neoforge.common.NeoForge;

/**
 * NeoForge entrypoint. This is the loader-specific glue only - everything else (modules, the menu,
 * settings, rendering logic) lives in the shared source under common/ and is identical to the
 * Fabric build's NovaUtilsClient. Client-only ({@code dist = Dist.CLIENT}) since this mod has no
 * server-side content.
 *
 * <p>Important NeoForge-specific gotcha: the {@code @Mod} constructor runs during mod construction,
 * <em>before</em> {@code Minecraft.getInstance()} exists. {@link ModuleManager#get()} is first
 * touched in {@link #onClientSetup}, which is the documented "safe to touch the client" point -
 * touching it any earlier (as the constructor originally did) crashes with a NullPointerException
 * reading {@code Minecraft.getInstance().gameDirectory} while loading the config.
 */
@Mod(value = NovaUtils.MOD_ID, dist = Dist.CLIENT)
public class NovaUtilsNeoForge {
    private static final boolean DEBUG_MENU = System.getProperty("novautils.debugmenu") != null;

    private KeyMapping openMenu;
    private KeyMapping panic;

    public NovaUtilsNeoForge(IEventBus modEventBus) {
        NovaUtils.LOG.info("NovaUtils loading (NeoForge)...");

        modEventBus.addListener(this::registerKeyMappings);
        modEventBus.addListener(this::registerGuiLayers);
        modEventBus.addListener(this::onClientSetup);

        NeoForge.EVENT_BUS.addListener(this::onClientTick);
        NeoForge.EVENT_BUS.addListener(this::onSubmitGeometry);
        NeoForge.EVENT_BUS.addListener(this::onRegisterCommands);
        NeoForge.EVENT_BUS.addListener(this::onLoggingOut);
    }

    private void registerKeyMappings(RegisterKeyMappingsEvent event) {
        openMenu = new KeyMapping("key.novautils.openmenu", InputConstants.Type.KEYBOARD, InputConstants.KEY_RSHIFT, KeyMapping.Category.MISC);
        panic = new KeyMapping("key.novautils.panic", InputConstants.Type.KEYBOARD, InputConstants.KEY_DELETE, KeyMapping.Category.MISC);
        event.register(openMenu);
        event.register(panic);
    }

    private void registerGuiLayers(RegisterGuiLayersEvent event) {
        // Deferred lambda, not a ModuleManager.get()::renderHud method reference: this event fires
        // before FMLClientSetupEvent, i.e. before Minecraft.getInstance() is valid, and a method
        // reference would evaluate ModuleManager.get() (and so construct every module) right now.
        event.registerAboveAll(Identifier.fromNamespaceAndPath(NovaUtils.MOD_ID, "hud"),
            (graphics, tracker) -> ModuleManager.get().renderHud(graphics, tracker));
    }

    private void onClientSetup(FMLClientSetupEvent event) {
        // First real touch of ModuleManager: constructs every module and loads the saved config.
        ModuleManager manager = ModuleManager.get();
        NovaUtils.LOG.info("NovaUtils loaded, {} modules registered.", manager.getModules().size());
    }

    private void onClientTick(ClientTickEvent.Post event) {
        ModuleManager manager = ModuleManager.get();
        manager.tick();

        Minecraft client = Minecraft.getInstance();

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
            if (client.gui.screen() == null) client.gui.setScreen(new ClickGuiScreen());
        }
    }

    private void onSubmitGeometry(SubmitCustomGeometryEvent event) {
        ModuleManager.get().renderWorld(new NeoForgeRenderContext(event));
    }

    private void onRegisterCommands(RegisterClientCommandsEvent event) {
        NeoForgeWaypointCommands.register(event);
    }

    private void onLoggingOut(ClientPlayerNetworkEvent.LoggingOut event) {
        // Also covers quitting the game: NeoForge fires this before the client shuts down too.
        ModuleManager.get().saveIfDirty();
    }
}
