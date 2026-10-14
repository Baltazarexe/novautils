package dev.novautils.utils;

import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.scores.PlayerTeam;
import net.minecraft.world.scores.TeamColor;

import java.util.Optional;

/**
 * The vanilla APIs that changed shape between versions, in their MC 26.2 form: screens on Gui and
 * team colours as TeamColor (like 26.3), but GLFW-style keys, the one-argument swing and the
 * 26.1.2-style submitModel (like 26.1.2).
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
        return InputConstants.isKeyDown(mc.getWindow(), keyCode);
    }

    public static String keyDisplayName(int keyCode) {
        return InputConstants.Type.KEYSYM.getOrCreate(keyCode).getDisplayName().getString();
    }

    public static void swingMainHand(LocalPlayer player) {
        player.swing(InteractionHand.MAIN_HAND);
    }

    public static net.minecraft.client.Camera camera(Minecraft mc) {
        return mc.gameRenderer.mainCamera();
    }

    /** Throws away every built chunk mesh so they get rebuilt (Xray on/off). */
    public static void reloadChunks(Minecraft mc) {
        mc.levelExtractor.allChanged();
    }

    /** Chams: submits {@code model} once more with the see-through render type, in a flat colour. */
    @SuppressWarnings({"unchecked", "rawtypes"})
    public static void submitChams(net.minecraft.client.renderer.SubmitNodeCollector collector, net.minecraft.client.model.Model model,
                                   Object state, com.mojang.blaze3d.vertex.PoseStack pose,
                                   net.minecraft.client.renderer.rendertype.RenderType type, int color) {
        collector.submitModel((net.minecraft.client.model.Model) model, state, pose, type, 0xF000F0,
            net.minecraft.client.renderer.texture.OverlayTexture.NO_OVERLAY, color, null, 0, null);
    }

    /** Typed letters reach screens on their own here (GLFW); only 26.3 needs to ask. */
    public static void startTextInput(Minecraft mc, Object owner) {
    }

    public static void stopTextInput(Minecraft mc, Object owner) {
    }
}
