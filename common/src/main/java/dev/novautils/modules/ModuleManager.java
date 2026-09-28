package dev.novautils.modules;

import dev.novautils.utils.PlatformCompat;
import dev.novautils.render.RenderContext;
import dev.novautils.config.Config;
import dev.novautils.modules.combat.AutoArmor;
import dev.novautils.modules.combat.AutoFight;
import dev.novautils.modules.combat.AutoLog;
import dev.novautils.modules.combat.AutoTotem;
import dev.novautils.modules.combat.Surround;
import dev.novautils.modules.misc.ActiveModules;
import dev.novautils.modules.misc.AntiAfk;
import dev.novautils.modules.misc.ArmorHud;
import dev.novautils.modules.misc.DeathPoint;
import dev.novautils.modules.misc.KeyStrokes;
import dev.novautils.modules.misc.Notifications;
import dev.novautils.modules.misc.TotemCounter;
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
import dev.novautils.modules.movement.AutoWalk;
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
import dev.novautils.modules.player.FastBreak;
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
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;

import java.util.ArrayList;
import java.util.List;

public class ModuleManager {
    private static final ModuleManager INSTANCE = new ModuleManager();

    private static final int AUTOSAVE_INTERVAL = 200; // 10 seconds

    private final List<Module> modules = new ArrayList<>();
    private boolean dirty;
    private int autosaveTimer = AUTOSAVE_INTERVAL;

    public static ModuleManager get() {
        return INSTANCE;
    }

    /** Null until the singleton finished constructing (i.e. while modules are still registering). */
    public static ModuleManager getIfReady() {
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
        register(new AutoWalk());
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
        register(new AutoLog());

        // Player
        register(new AutoEat());
        register(new AutoTool());
        register(new AutoReplenish());
        register(new ChestSwap());
        register(new FastUse());
        register(new FastBreak());
        register(new AutoClicker());

        // World
        register(new Timer());
        register(new Nuker());
        register(new AutoShearer());
        register(new AutoBreed());
        register(new AutoNametag());

        // Misc
        register(new ActiveModules());
        register(new HudInfo());
        register(new ArmorHud());
        register(new PotionHud());
        register(new KeyStrokes());
        register(new TotemCounter());
        register(new Notifications());
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

        if (autosaveTimer-- <= 0) {
            autosaveTimer = AUTOSAVE_INTERVAL;
            saveIfDirty();
        }

        for (Module module : modules) {
            int key = module.getKeyBind();
            if (key != -1 && PlatformCompat.currentScreen(mc) == null) {
                boolean down = PlatformCompat.isKeyDown(mc, key);
                if (down && !module.isKeyWasDown()) {
                    module.toggle();
                    markDirty();
                    dev.novautils.gui.Toasts.push(module.name + (module.isEnabled() ? " ON" : " OFF"),
                        module.isEnabled() ? dev.novautils.gui.Theme.ACCENT : 0xFF6E6E82);
                }
                module.setKeyWasDown(down);
            } else if (key != -1) {
                module.setKeyWasDown(false);
            }

            if (module.isEnabled()) module.onTick();
        }
    }

    /** Registered once with LevelRenderEvents; dispatches to every enabled module. */
    public void renderWorld(RenderContext context) {
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

    /**
     * Flags the config as changed. Writing happens on the autosave tick instead of right away, so
     * dragging a slider doesn't rewrite the file on every frame.
     */
    public void markDirty() {
        dirty = true;
    }

    /** Writes the config if anything changed since the last write. */
    public void saveIfDirty() {
        if (dirty) saveConfig();
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
        dirty = false;
    }
}
