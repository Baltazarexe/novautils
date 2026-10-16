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
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.event.lifecycle.FMLClientSetupEvent;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.RegisterGuiLayersEvent;
import net.neoforged.neoforge.client.event.RegisterKeyMappingsEvent;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;
import net.neoforged.neoforge.client.event.ViewportEvent;
import net.neoforged.neoforge.common.NeoForge;

/**
 * NeoForge 1.21.1 entrypoint: the loader glue (key mappings, HUD layer, client tick, world render
 * stage, FOV hook) around the same modules as the other builds. Client-only.
 *
 * <p>The {@code @Mod} constructor runs before Minecraft.getInstance() exists, so ModuleManager is
 * first touched in {@link #onClientSetup}.
 */
@Mod(value = NovaUtils.MOD_ID, dist = Dist.CLIENT)
public class NovaUtilsNeoForge {
    private static final boolean DEBUG_MENU = System.getProperty("novautils.debugmenu") != null;

    private KeyMapping openMenu;
    private KeyMapping panic;

    public NovaUtilsNeoForge(IEventBus modBus) {
        NovaUtils.LOG.info("NovaUtils loading (NeoForge 1.21.1)...");
        modBus.addListener(this::registerKeyMappings);
        modBus.addListener(this::registerLayers);
        modBus.addListener(this::onClientSetup);

        NeoForge.EVENT_BUS.addListener(this::onClientTick);
        NeoForge.EVENT_BUS.addListener(this::onRenderLevel);
        NeoForge.EVENT_BUS.addListener(this::onComputeFov);
        NeoForge.EVENT_BUS.addListener(this::onRenderFog);
        NeoForge.EVENT_BUS.addListener(this::onFogColor);
        NeoForge.EVENT_BUS.addListener(this::onLoggingOut);
    }

    private void registerKeyMappings(RegisterKeyMappingsEvent event) {
        openMenu = new KeyMapping("key.novautils.openmenu", InputConstants.Type.KEYSYM, InputConstants.KEY_RSHIFT, "key.categories.misc");
        panic = new KeyMapping("key.novautils.panic", InputConstants.Type.KEYSYM, InputConstants.KEY_DELETE, "key.categories.misc");
        event.register(openMenu);
        event.register(panic);
    }

    private void registerLayers(RegisterGuiLayersEvent event) {
        // a lambda, not ModuleManager.get()::renderHud: that would build every module right now,
        // before the game exists
        event.registerAboveAll(ResourceLocation.fromNamespaceAndPath(NovaUtils.MOD_ID, "hud"), (graphics, tracker) -> {
            if (NovaUtils.modulesReady) ModuleManager.get().renderHud(graphics, tracker.getGameTimeDeltaPartialTick(false));
        });
    }

    private void onClientSetup(FMLClientSetupEvent event) {
        event.enqueueWork(() -> {
            ModuleManager manager = ModuleManager.get();
            NovaUtils.LOG.info("NovaUtils loaded, {} modules registered.", manager.getModules().size());
        });
    }

    private void onClientTick(ClientTickEvent.Post event) {
        if (!NovaUtils.modulesReady) return;
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
        if (pose == null) pose = new PoseStack();
        PoseStack stack = pose;
        Vec3 cam = event.getCamera().getPosition();
        float partial = event.getPartialTick().getGameTimeDeltaPartialTick(false);
        ModuleManager.get().renderWorld(new RenderContext() {
            @Override
            public PoseStack poseStack() {
                return stack;
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

    /** SkyFog: push back / remove the fog, clear water and lava. */
    private void onRenderFog(ViewportEvent.RenderFog event) {
        var s = dev.novautils.modules.render.SkyFog.active();
        if (s == null) return;
        var type = event.getType();
        float scale = type == net.minecraft.world.level.material.FogType.WATER || type == net.minecraft.world.level.material.FogType.LAVA
            ? s.fluidScale() : type == net.minecraft.world.level.material.FogType.NONE ? s.terrainScale() : 1f;
        if (scale == 1f) return;
        if (Float.isInfinite(scale)) {
            event.setNearPlaneDistance(1_000_000f);
            event.setFarPlaneDistance(1_000_001f);
        } else {
            event.setNearPlaneDistance(event.getNearPlaneDistance() * scale);
            event.setFarPlaneDistance(event.getFarPlaneDistance() * scale);
        }
        event.setCanceled(true);
    }

    private void onFogColor(ViewportEvent.ComputeFogColor event) {
        var s = dev.novautils.modules.render.SkyFog.active();
        if (s == null || s.fogColor() == 0) return;
        int c = s.fogColor();
        event.setRed((c >> 16 & 255) / 255f);
        event.setGreen((c >> 8 & 255) / 255f);
        event.setBlue((c & 255) / 255f);
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
