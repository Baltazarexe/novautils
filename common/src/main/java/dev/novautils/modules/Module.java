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

    /** Key code bound to this module, -1 = unbound. Toggled from the key press event (KeyboardHandlerMixin). */
    private int keyBind = -1;

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

    public void onEnable() {}
    public void onDisable() {}
    public void onTick() {}

    /** Only called for enabled modules by ModuleManager's single global render hook. */
    public void onRenderWorld(RenderContext context) {}

    /** Only called for enabled modules by ModuleManager's single global HUD hook. */
    public void onRenderHud(GuiGraphicsExtractor graphics, DeltaTracker deltaTracker) {}

    /**
     * The movement the local player is about to make this tick (self-propelled only). Return the
     * vector to use instead - this runs inside vanilla's own physics step, so unlike setting the
     * velocity after the tick nothing overwrites it.
     */
    public net.minecraft.world.phys.Vec3 onMove(net.minecraft.world.phys.Vec3 movement) {
        return movement;
    }

    /** An outgoing packet. Return true to drop it. */
    public boolean onSendPacket(net.minecraft.network.protocol.Packet<?> packet) {
        return false;
    }

    /**
     * An incoming game packet, already on the client thread. Only the packet types
     * ClientPacketListenerMixin forwards arrive here. Return true to cancel vanilla's handling.
     */
    public boolean onReceivePacket(net.minecraft.network.protocol.Packet<?> packet) {
        return false;
    }
}
