package dev.novautils.modules;

import dev.novautils.config.Config;
import dev.novautils.modules.combat.AutoFight;
import dev.novautils.modules.misc.HudInfo;
import dev.novautils.modules.movement.AutoSprint;
import dev.novautils.modules.render.Esp;
import dev.novautils.modules.render.Fullbright;
import dev.novautils.modules.render.StorageEsp;
import dev.novautils.modules.render.Waypoints;
import dev.novautils.modules.render.Zoom;
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderContext;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.List;

public class ModuleManager {
    private static final ModuleManager INSTANCE = new ModuleManager();

    private final List<Module> modules = new ArrayList<>();

    public static ModuleManager get() {
        return INSTANCE;
    }

    private ModuleManager() {
        register(new Fullbright());
        register(new Zoom());
        register(new Esp());
        register(new StorageEsp());
        register(new Waypoints());
        register(new HudInfo());
        register(new AutoSprint());
        register(new AutoFight());

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
        long handle = mc.getWindow().handle();

        for (Module module : modules) {
            int key = module.getKeyBind();
            if (key != -1 && mc.gui.screen() == null) {
                boolean down = GLFW.glfwGetKey(handle, key) == GLFW.GLFW_PRESS;
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

    public void saveConfig() {
        Config.get().save(modules);
    }
}
