package dev.novautils.utils;

import net.minecraft.client.Minecraft;

/**
 * Module keybinds can be a keyboard key or a mouse button. Keys keep their normal key code; mouse
 * buttons are stored as {@code -100 - button} so they never clash with a key or with -1 (unbound)
 * and old configs stay valid. MouseHandlerMixin reports button presses here.
 */
public final class Keybinds {
    private Keybinds() {}

    private static final int MOUSE_BASE = -100;
    private static final boolean[] mouseDown = new boolean[16];

    public static int mouse(int button) {
        return MOUSE_BASE - button;
    }

    public static boolean isMouse(int code) {
        return code <= MOUSE_BASE;
    }

    public static int mouseButton(int code) {
        return MOUSE_BASE - code;
    }

    /** MouseHandlerMixin: remember which buttons are held (for "hold" style binds like Zoom). */
    public static void mouseChanged(int button, boolean down) {
        if (button >= 0 && button < mouseDown.length) mouseDown[button] = down;
    }

    public static boolean isDown(Minecraft mc, int code) {
        if (code == -1) return false;
        if (isMouse(code)) {
            int b = mouseButton(code);
            return b >= 0 && b < mouseDown.length && mouseDown[b];
        }
        return PlatformCompat.isKeyDown(mc, code);
    }

    public static String name(int code) {
        if (code == -1) return "None";
        if (isMouse(code)) {
            return switch (mouseButton(code)) {
                case 0 -> "Left Click";
                case 1 -> "Right Click";
                case 2 -> "Middle";
                default -> "Mouse " + (mouseButton(code) + 1);
            };
        }
        return PlatformCompat.keyDisplayName(code);
    }
}
