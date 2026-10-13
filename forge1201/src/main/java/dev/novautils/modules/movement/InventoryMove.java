package dev.novautils.modules.movement;

import com.mojang.blaze3d.platform.InputConstants;
import dev.novautils.gui.screens.ClickGuiScreen;
import dev.novautils.gui.screens.HudEditorScreen;
import dev.novautils.mixin.KeyMappingAccessor;
import dev.novautils.modules.Category;
import dev.novautils.modules.Module;
import dev.novautils.settings.Setting;
import dev.novautils.utils.PlatformCompat;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.ChatScreen;
import net.minecraft.client.gui.screens.Screen;

/**
 * Keep walking, jumping and sprinting with your inventory, a chest or any other menu open. Skipped
 * while you're typing (chat, signs, anvil names, search boxes) so letters don't move you.
 */
public class InventoryMove extends Module {
    private final Setting<Boolean> sneak = register(Setting.builder(Boolean.class, "sneak")
        .description("Also let the sneak key work in menus.").defaultValue(false).build());

    private boolean wasInMenu;

    public InventoryMove() {
        super("InventoryMove", "Walk around with menus open.", Category.MOVEMENT);
    }

    private KeyMapping[] keys() {
        var o = mc.options;
        return sneak.get()
            ? new KeyMapping[]{o.keyUp, o.keyDown, o.keyLeft, o.keyRight, o.keyJump, o.keySprint, o.keyShift}
            : new KeyMapping[]{o.keyUp, o.keyDown, o.keyLeft, o.keyRight, o.keyJump, o.keySprint};
    }

    private boolean physicallyDown(KeyMapping k) {
        InputConstants.Key key = ((KeyMappingAccessor) k).novautils$boundKey();
        if (key.getType() == InputConstants.Type.MOUSE || key.getValue() < 0) return false;
        return PlatformCompat.isKeyDown(mc, key.getValue());
    }

    private boolean typing(Screen s) {
        return s instanceof ChatScreen || s instanceof ClickGuiScreen || s instanceof HudEditorScreen
            || s.getFocused() instanceof EditBox
            || s.getClass().getSimpleName().contains("Sign") || s.getClass().getSimpleName().contains("Book");
    }

    @Override
    public void onTick() {
        if (mc.player == null) return;
        Screen s = PlatformCompat.currentScreen(mc);
        boolean inMenu = s != null && !typing(s);
        // update while a menu is open, and once more right after it closes so nothing stays stuck
        if (inMenu || wasInMenu) {
            for (KeyMapping k : keys()) k.setDown(physicallyDown(k));
        }
        wasInMenu = inMenu;
    }
}
