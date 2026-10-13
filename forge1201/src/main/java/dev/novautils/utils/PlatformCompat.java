package dev.novautils.utils;

import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.scores.PlayerTeam;

/** The vanilla APIs that changed shape since 1.20.1, in their 1.20.1 form. */
public final class PlatformCompat {
    private PlatformCompat() {}

    public static Screen currentScreen(Minecraft mc) {
        return mc.screen;
    }

    public static void openScreen(Minecraft mc, Screen screen) {
        mc.setScreen(screen);
    }

    public static void setPlayerColor(PlayerTeam team) {
        team.setColor(ChatFormatting.RED);
    }

    public static void setHostileColor(PlayerTeam team) {
        team.setColor(ChatFormatting.GOLD);
    }

    public static void setPassiveColor(PlayerTeam team) {
        team.setColor(ChatFormatting.GREEN);
    }

    public static boolean isKeyDown(Minecraft mc, int keyCode) {
        return InputConstants.isKeyDown(mc.getWindow().getWindow(), keyCode);
    }

    public static String keyDisplayName(int keyCode) {
        return InputConstants.Type.KEYSYM.getOrCreate(keyCode).getDisplayName().getString();
    }

    public static void swingMainHand(LocalPlayer player) {
        player.swing(InteractionHand.MAIN_HAND);
    }

    public static net.minecraft.client.Camera camera(Minecraft mc) {
        return mc.gameRenderer.getMainCamera();
    }

    /** Throws away every built chunk mesh so they get rebuilt (Xray on/off). */
    public static void reloadChunks(Minecraft mc) {
        mc.levelRenderer.allChanged();
    }

    /** Field of view of the last rendered frame (ForgeHooks' ComputeFov, includes Zoom). */
    public static double lastFov = 70;
}
