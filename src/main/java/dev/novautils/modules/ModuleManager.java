package dev.novautils.modules;

import com.mojang.blaze3d.platform.InputConstants;
import dev.novautils.config.Config;
import dev.novautils.modules.combat.AutoArmor;
import dev.novautils.modules.combat.AutoFight;
import dev.novautils.modules.combat.AutoTotem;
import dev.novautils.modules.combat.Surround;
import dev.novautils.modules.misc.AntiAfk;
import dev.novautils.modules.misc.ArmorHud;
import dev.novautils.modules.misc.DeathPoint;
import dev.novautils.modules.misc.KeyStrokes;
import dev.novautils.modules.misc.PotionHud;
import dev.novautils.modules.misc.AutoReconnect;
import dev.novautils.modules.misc.AutoRespawn;
import dev.novautils.modules.misc.HudInfo;
import dev.novautils.modules.misc.Notifier;
import dev.novautils.modules.misc.PlayerRadar;
import dev.novautils.modules.movement.AirJump;
import dev.novautils.modules.movement.AntiVoid;
import dev.novautils.modules.movement.AutoJump;
import dev.novautils.modules.movement.AutoSprint;
import dev.novautils.modules.movement.ElytraBoost;
import dev.novautils.modules.movement.ElytraFly;
import dev.novautils.modules.movement.FastClimb;
import dev.novautils.modules.movement.Flight;
import dev.novautils.modules.movement.HighJump;
import dev.novautils.modules.movement.Jesus;
import dev.novautils.modules.movement.LongJump;
import dev.novautils.modules.movement.NoFall;
import dev.novautils.modules.movement.Parkour;
import dev.novautils.modules.movement.SafeWalk;
import dev.novautils.modules.movement.Scaffold;
import dev.novautils.modules.movement.Sneak;
import dev.novautils.modules.movement.Speed;
import dev.novautils.modules.movement.Spider;
import dev.novautils.modules.movement.Step;
import dev.novautils.modules.player.AutoClicker;
import dev.novautils.modules.player.AutoEat;
import dev.novautils.modules.player.AutoReplenish;
import dev.novautils.modules.player.AutoTool;
import dev.novautils.modules.player.ChestSwap;
import dev.novautils.modules.player.FastUse;
import dev.novautils.modules.render.Breadcrumbs;
import dev.novautils.modules.render.Esp;
import dev.novautils.modules.render.Fullbright;
import dev.novautils.modules.render.HoleEsp;
import dev.novautils.modules.render.ItemHighlight;
import dev.novautils.modules.render.LightOverlay;
import dev.novautils.modules.render.LogoutSpots;
import dev.novautils.modules.render.StorageEsp;
import dev.novautils.modules.render.TimeChanger;
import dev.novautils.modules.render.Tracers;
import dev.novautils.modules.render.Trail;
import dev.novautils.modules.render.TunnelEsp;
import dev.novautils.modules.render.VoidEsp;
import dev.novautils.modules.render.Waypoints;
import dev.novautils.modules.render.WeatherChanger;
import dev.novautils.modules.render.Xray;
import dev.novautils.modules.render.Zoom;
import dev.novautils.modules.world.AutoBreed;
import dev.novautils.modules.world.AutoNametag;
import dev.novautils.modules.world.AutoShearer;
import dev.novautils.modules.world.Nuker;
import dev.novautils.modules.world.Timer;
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderContext;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;

import java.util.ArrayList;
import java.util.List;

public class ModuleManager {
    private static final ModuleManager INSTANCE = new ModuleManager();

    private final List<Module> modules = new ArrayList<>();

    public static ModuleManager get() {
        return INSTANCE;
    }

    private ModuleManager() {
        // Render
        register(new Fullbright());
        register(new Zoom());
        register(new Esp());
        register(new StorageEsp());
        register(new Waypoints());
        register(new Xray());
        register(new Tracers());
        register(new Trail());
        register(new Breadcrumbs());
        register(new LogoutSpots());
        register(new ItemHighlight());
        register(new LightOverlay());
        register(new HoleEsp());
        register(new TunnelEsp());
        register(new VoidEsp());
        register(new TimeChanger());
        register(new WeatherChanger());

        // Movement
        register(new AutoSprint());
        register(new AutoJump());
        register(new Flight());
        register(new Speed());
        register(new Step());
        register(new HighJump());
        register(new LongJump());
        register(new AirJump());
        register(new Jesus());
        register(new Spider());
        register(new FastClimb());
        register(new ElytraBoost());
        register(new ElytraFly());
        register(new Scaffold());
        register(new Sneak());
        register(new SafeWalk());
        register(new Parkour());
        register(new NoFall());
        register(new AntiVoid());

        // Combat (defensive / mobs only - nothing that targets players)
        register(new AutoFight());
        register(new Surround());
        register(new AutoTotem());
        register(new AutoArmor());

        // Player
        register(new AutoEat());
        register(new AutoTool());
        register(new AutoReplenish());
        register(new ChestSwap());
        register(new FastUse());
        register(new AutoClicker());

        // World
        register(new Timer());
        register(new Nuker());
        register(new AutoShearer());
        register(new AutoBreed());
        register(new AutoNametag());

        // Misc
        register(new HudInfo());
        register(new ArmorHud());
        register(new PotionHud());
        register(new KeyStrokes());
        register(new DeathPoint());
        register(new PlayerRadar());
        register(new Notifier());
        register(new AntiAfk());
        register(new AutoRespawn());
        register(new AutoReconnect());

        Config.get().load(modules);
    }

    private void register(Module module) {
        modules.add(module);
    }

    public List<Module> getModules() {
        return modules;
    }

    @SuppressWarnings("unchecked")
    public <T extends Module> T get(Class<T> type) {
        for (Module module : modules) {
            if (type.isInstance(module)) return (T) module;
        }
        throw new IllegalStateException("Module not registered: " + type.getName());
    }

    public boolean isActive(Class<? extends Module> type) {
        for (Module module : modules) {
            if (type.isInstance(module)) return module.isEnabled();
        }
        return false;
    }

    /** Called once per client tick from the mod's client tick callback. */
    public void tick() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.getWindow() == null) return;

        for (Module module : modules) {
            int key = module.getKeyBind();
            if (key != -1 && mc.gui.screen() == null) {
                boolean down = InputConstants.isKeyDown(key);
                if (down && !module.isKeyWasDown()) module.toggle();
                module.setKeyWasDown(down);
            } else if (key != -1) {
                module.setKeyWasDown(false);
            }

            if (module.isEnabled()) module.onTick();
        }
    }

    /** Registered once with LevelRenderEvents; dispatches to every enabled module. */
    public void renderWorld(LevelRenderContext context) {
        for (Module module : modules) {
            if (module.isEnabled()) module.onRenderWorld(context);
        }
    }

    /** Registered once with HudElementRegistry; dispatches to every enabled module. */
    public void renderHud(GuiGraphicsExtractor graphics, DeltaTracker deltaTracker) {
        for (Module module : modules) {
            if (module.isEnabled()) module.onRenderHud(graphics, deltaTracker);
        }
    }

    /** Turns everything off at once; returns how many were on. Bound to the panic key. */
    public int disableAll() {
        int count = 0;
        for (Module module : modules) {
            if (module.isEnabled()) {
                module.setEnabled(false);
                count++;
            }
        }
        saveConfig();
        return count;
    }

    public void saveConfig() {
        Config.get().save(modules);
    }
}
