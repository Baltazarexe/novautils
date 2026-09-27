package dev.novautils.modules;

import dev.novautils.render.RenderContext;
import dev.novautils.settings.Setting;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;

import java.util.ArrayList;
import java.util.List;

/**
 * Base class for every toggleable feature. Subclasses add {@link Setting}s in their
 * constructor via {@link #register(Setting)} and override {@link #onEnable()},
 * {@link #onDisable()} and {@link #onTick()} as needed.
 */
public abstract class Module {
    protected static final Minecraft mc = Minecraft.getInstance();

    public final String name;
    public final String description;
    public final Category category;

    private final List<Setting<?>> settings = new ArrayList<>();
    private boolean enabled = false;

    /** GLFW key code bound to this module, -1 = unbound. Polled each tick by ModuleManager. */
    private int keyBind = -1;
    private boolean keyWasDown = false;

    protected Module(String name, String description, Category category) {
        this.name = name;
        this.description = description;
        this.category = category;
    }

    protected <T> Setting<T> register(Setting<T> setting) {
        settings.add(setting);
        return setting;
    }

    public List<Setting<?>> getSettings() {
        return settings;
    }

    public boolean isEnabled() {
        return enabled;
    }

    public void toggle() {
        setEnabled(!enabled);
    }

    public void setEnabled(boolean value) {
        if (value == enabled) return;
        enabled = value;
        if (enabled) onEnable();
        else onDisable();

        // Null while the manager is still building its module list (config load), which is exactly
        // when we don't want to mark anything dirty.
        ModuleManager manager = ModuleManager.getIfReady();
        if (manager != null) manager.markDirty();
    }

    public int getKeyBind() {
        return keyBind;
    }

    public void setKeyBind(int keyCode) {
        this.keyBind = keyCode;
    }

    public boolean isKeyWasDown() {
        return keyWasDown;
    }

    public void setKeyWasDown(boolean value) {
        this.keyWasDown = value;
    }

    /** Called every client tick regardless of enabled state (used for keybind polling by ModuleManager). */
    public void onEnable() {}
    public void onDisable() {}
    public void onTick() {}

    /** Only called for enabled modules by ModuleManager's single global render hook. */
    public void onRenderWorld(RenderContext context) {}

    /** Only called for enabled modules by ModuleManager's single global HUD hook. */
    public void onRenderHud(GuiGraphicsExtractor graphics, DeltaTracker deltaTracker) {}
}
