package dev.novautils;

import com.mojang.blaze3d.platform.InputConstants;
import com.mojang.blaze3d.vertex.PoseStack;
import dev.novautils.gui.screens.ClickGuiScreen;
import dev.novautils.modules.ModuleManager;
import dev.novautils.modules.render.Zoom;
import dev.novautils.render.RenderContext;
import dev.novautils.utils.PlatformCompat;
import dev.novautils.utils.RenderUtils;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.ClientPlayerNetworkEvent;
import net.minecraftforge.client.event.RegisterGuiOverlaysEvent;
import net.minecraftforge.client.event.RegisterKeyMappingsEvent;
import net.minecraftforge.client.event.RenderLevelStageEvent;
import net.minecraftforge.client.event.ViewportEvent;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.fml.IExtensionPoint;
import net.minecraftforge.fml.ModLoadingContext;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.event.lifecycle.FMLClientSetupEvent;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import net.minecraftforge.fml.loading.FMLEnvironment;

/**
 * Forge 1.20.1 entrypoint: the loader glue (key mappings, HUD overlay, client tick, world render
 * stage, FOV hook) around the same modules as the 26.x builds. Client-only: on a dedicated server
 * it does nothing, and it tells Forge it doesn't need to be on the server.
 *
 * <p>Like NeoForge, the {@code @Mod} constructor runs before Minecraft.getInstance() exists, so
 * ModuleManager is first touched in {@link #onClientSetup}.
 */
@Mod(NovaUtils.MOD_ID)
public class NovaUtilsForge {
    private static final boolean DEBUG_MENU = System.getProperty("novautils.debugmenu") != null;

    private KeyMapping openMenu;
    private KeyMapping panic;

    public NovaUtilsForge() {
        // nothing on the server side, and no need for the server to have it
        ModLoadingContext.get().registerExtensionPoint(IExtensionPoint.DisplayTest.class,
            () -> new IExtensionPoint.DisplayTest(() -> IExtensionPoint.DisplayTest.IGNORESERVERONLY, (remote, server) -> true));
        if (FMLEnvironment.dist != Dist.CLIENT) return;

        NovaUtils.LOG.info("NovaUtils loading (Forge 1.20.1)...");
        IEventBus modBus = FMLJavaModLoadingContext.get().getModEventBus();
        modBus.addListener(this::registerKeyMappings);
        modBus.addListener(this::registerOverlays);
        modBus.addListener(this::onClientSetup);

        MinecraftForge.EVENT_BUS.addListener(this::onClientTick);
        MinecraftForge.EVENT_BUS.addListener(this::onRenderLevel);
        MinecraftForge.EVENT_BUS.addListener(this::onComputeFov);
        MinecraftForge.EVENT_BUS.addListener(this::onLoggingOut);
    }

    private void registerKeyMappings(RegisterKeyMappingsEvent event) {
        openMenu = new KeyMapping("key.novautils.openmenu", InputConstants.Type.KEYSYM, InputConstants.KEY_RSHIFT, "key.categories.misc");
        panic = new KeyMapping("key.novautils.panic", InputConstants.Type.KEYSYM, InputConstants.KEY_DELETE, "key.categories.misc");
        event.register(openMenu);
        event.register(panic);
    }

    private void registerOverlays(RegisterGuiOverlaysEvent event) {
        // a lambda, not ModuleManager.get()::renderHud: that would build every module right now,
        // before the game exists
        event.registerAboveAll("hud", (gui, graphics, partialTick, width, height) -> {
            if (NovaUtils.modulesReady) ModuleManager.get().renderHud(graphics, partialTick);
        });
    }

    private void onClientSetup(FMLClientSetupEvent event) {
        event.enqueueWork(() -> {
            ModuleManager manager = ModuleManager.get();
            NovaUtils.LOG.info("NovaUtils loaded, {} modules registered.", manager.getModules().size());
        });
    }

    private void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END || !NovaUtils.modulesReady) return;
        ModuleManager manager = ModuleManager.get();
        manager.tick();
        SelfTest.tick();

        Minecraft client = Minecraft.getInstance();
        while (panic.consumeClick()) {
            int disabled = manager.disableAll();
            if (client.player != null) {
                client.player.sendSystemMessage(Component.literal("[NovaUtils] Panic: disabled " + disabled + " modules"));
            }
        }
        if (DEBUG_MENU && client.screen instanceof TitleScreen) client.setScreen(new ClickGuiScreen());
        while (openMenu.consumeClick()) {
            if (client.screen == null) client.setScreen(dev.novautils.gui.GuiState.get().newMenu());
        }
    }

    private void onRenderLevel(RenderLevelStageEvent event) {
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_TRANSLUCENT_BLOCKS || !NovaUtils.modulesReady) return;
        PoseStack pose = event.getPoseStack();
        Vec3 cam = event.getCamera().getPosition();
        float partial = event.getPartialTick();
        ModuleManager.get().renderWorld(new RenderContext() {
            @Override
            public PoseStack poseStack() {
                return pose;
            }

            @Override
            public Vec3 cameraPos() {
                return cam;
            }

            @Override
            public float partialTick() {
                return partial;
            }
        });
        RenderUtils.flush();
    }

    private void onComputeFov(ViewportEvent.ComputeFov event) {
        if (NovaUtils.modulesReady) {
            float factor = ModuleManager.get().get(Zoom.class).fovFactor((float) event.getPartialTick());
            if (factor != 1f) event.setFOV(event.getFOV() * factor);
        }
        PlatformCompat.lastFov = event.getFOV();
    }

    private void onLoggingOut(ClientPlayerNetworkEvent.LoggingOut event) {
        if (NovaUtils.modulesReady) ModuleManager.get().saveIfDirty();
    }
}
