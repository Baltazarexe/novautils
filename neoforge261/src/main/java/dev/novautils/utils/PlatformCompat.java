package dev.novautils.utils;

import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.scores.PlayerTeam;

/**
 * MC 26.1.2 flavour of this file (see common's copy for why it exists at all): screen management
 * is still directly on {@code Minecraft} here (it moved to {@code Gui} in 26.2+), and team colours
 * are the older {@code ChatFormatting} enum rather than {@code TeamColor}. This file is excluded
 * from common's contribution to this module's build - see neoforge261/build.gradle.
 */
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
        return InputConstants.isKeyDown(mc.getWindow(), keyCode);
    }

    public static String keyDisplayName(int keyCode) {
        return InputConstants.Type.KEYSYM.getOrCreate(keyCode).getDisplayName().getString();
    }

    public static void swingMainHand(LocalPlayer player) {
        player.swing(InteractionHand.MAIN_HAND);
    }
}
