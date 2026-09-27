package dev.novautils.modules.misc;

import dev.novautils.modules.Category;
import dev.novautils.modules.Module;
import dev.novautils.settings.Setting;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.multiplayer.PlayerInfo;
import net.minecraft.core.BlockPos;
import net.minecraft.util.CommonColors;

/** Small stack of info lines in the top-left corner: coordinates, FPS, biome, ping. */
public class HudInfo extends Module {
    private final Setting<Boolean> coordinates = register(Setting.builder(Boolean.class, "coordinates")
        .description("Show your XYZ position.")
        .defaultValue(true)
        .build());

    private final Setting<Boolean> fps = register(Setting.builder(Boolean.class, "fps")
        .description("Show frames per second.")
        .defaultValue(true)
        .build());

    private final Setting<Boolean> biome = register(Setting.builder(Boolean.class, "biome")
        .description("Show the biome you're standing in.")
        .defaultValue(false)
        .build());

    private final Setting<Boolean> ping = register(Setting.builder(Boolean.class, "ping")
        .description("Show your ping to the server.")
        .defaultValue(false)
        .build());

    public HudInfo() {
        super("HudInfo", "Shows coordinates, FPS, biome and ping in the corner of your screen.", Category.MISC);
    }

    @Override
    public void onRenderHud(GuiGraphicsExtractor graphics, DeltaTracker deltaTracker) {
        if (mc.player == null || mc.level == null) return;

        int x = 4;
        int y = 4;
        int lineHeight = mc.font.lineHeight + 2;

        if (coordinates.get()) {
            BlockPos pos = mc.player.blockPosition();
            graphics.text(mc.font, String.format("XYZ: %d, %d, %d", pos.getX(), pos.getY(), pos.getZ()), x, y, CommonColors.WHITE);
            y += lineHeight;
        }

        if (fps.get()) {
            graphics.text(mc.font, "FPS: " + mc.getFps(), x, y, CommonColors.WHITE);
            y += lineHeight;
        }

        if (biome.get()) {
            String biomeName = mc.level.getBiome(mc.player.blockPosition())
                .unwrapKey()
                .map(key -> key.identifier().getPath())
                .orElse("unknown");
            graphics.text(mc.font, "Biome: " + biomeName, x, y, CommonColors.WHITE);
            y += lineHeight;
        }

        if (ping.get() && mc.getConnection() != null) {
            PlayerInfo info = mc.getConnection().getPlayerInfo(mc.player.getUUID());
            int latency = info != null ? info.getLatency() : -1;
            graphics.text(mc.font, "Ping: " + (latency >= 0 ? latency + "ms" : "n/a"), x, y, CommonColors.WHITE);
        }
    }
}
