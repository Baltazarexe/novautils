package dev.novautils.utils;

import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.component.SwingAnimation;
import net.minecraft.world.scores.PlayerTeam;
import net.minecraft.world.scores.TeamColor;

import java.util.Optional;

/**
 * The handful of vanilla APIs that changed shape between the MC versions this mod targets. This
 * is the MC 26.2+ flavour (screen management moved onto {@code Gui}, team colours became
 * {@code TeamColor}); the neoforge261 module (MC 26.1.2) ships its own copy of this exact file
 * with the older shapes ({@code Minecraft.screen}/{@code setScreen}, {@code ChatFormatting}) and
 * excludes this one from its build - see neoforge261/build.gradle.
 */
public final class PlatformCompat {
    private PlatformCompat() {}

    public static Screen currentScreen(Minecraft mc) {
        return mc.gui.screen();
    }

    public static void openScreen(Minecraft mc, Screen screen) {
        mc.gui.setScreen(screen);
    }

    public static void setPlayerColor(PlayerTeam team) {
        team.setColor(Optional.of(TeamColor.RED));
    }

    public static void setHostileColor(PlayerTeam team) {
        team.setColor(Optional.of(TeamColor.GOLD));
    }

    public static void setPassiveColor(PlayerTeam team) {
        team.setColor(Optional.of(TeamColor.GREEN));
    }

    public static boolean isKeyDown(Minecraft mc, int keyCode) {
        return InputConstants.isKeyDown(keyCode);
    }

    public static String keyDisplayName(int keyCode) {
        return InputConstants.Type.KEYBOARD.getOrCreate(keyCode).getDisplayName().getString();
    }

    public static void swingMainHand(LocalPlayer player) {
        player.swing(InteractionHand.MAIN_HAND, SwingAnimation.DEFAULT, true);
    }

    public static net.minecraft.client.Camera camera(Minecraft mc) {
        return mc.gameRenderer.mainCamera();
    }
}
