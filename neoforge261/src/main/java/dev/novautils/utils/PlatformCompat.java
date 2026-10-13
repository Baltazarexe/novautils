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

    public static net.minecraft.client.Camera camera(Minecraft mc) {
        return mc.gameRenderer.getMainCamera();
    }

    /** Throws away every built chunk mesh so they get rebuilt (Xray on/off). */
    public static void reloadChunks(Minecraft mc) {
        mc.levelRenderer.allChanged();
    }

    /** Chams: submits {@code model} once more with the see-through render type, in a flat colour. */
    @SuppressWarnings({"unchecked", "rawtypes"})
    public static void submitChams(net.minecraft.client.renderer.SubmitNodeCollector collector, net.minecraft.client.model.Model model,
                                   Object state, com.mojang.blaze3d.vertex.PoseStack pose,
                                   net.minecraft.client.renderer.rendertype.RenderType type, int color) {
        collector.submitModel((net.minecraft.client.model.Model) model, state, pose, type, 0xF000F0,
            net.minecraft.client.renderer.texture.OverlayTexture.NO_OVERLAY, color, null, 0, null);
    }
}
