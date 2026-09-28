package dev.novautils.modules.misc;

import dev.novautils.modules.Category;
import dev.novautils.modules.HudModule;
import dev.novautils.settings.Setting;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.multiplayer.PlayerInfo;
import net.minecraft.core.BlockPos;
import net.minecraft.util.CommonColors;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.List;

/** Movable block of info lines: coordinates (with the other dimension's), facing, speed, FPS, biome, ping. */
public class HudInfo extends HudModule {
    private final Setting<Boolean> coordinates = register(Setting.builder(Boolean.class, "coordinates")
        .description("Show your XYZ position.").defaultValue(true).build());

    private final Setting<Boolean> otherDimension = register(Setting.builder(Boolean.class, "nether-coords")
        .description("Also show the matching Nether / Overworld coordinates (x8 or /8).").defaultValue(true).build());

    private final Setting<Boolean> facing = register(Setting.builder(Boolean.class, "facing")
        .description("Show the direction you are looking.").defaultValue(true).build());

    private final Setting<Boolean> speed = register(Setting.builder(Boolean.class, "speed")
        .description("Show your horizontal speed in blocks per second.").defaultValue(false).build());

    private final Setting<Boolean> fps = register(Setting.builder(Boolean.class, "fps")
        .description("Show frames per second.").defaultValue(true).build());

    private final Setting<Boolean> biome = register(Setting.builder(Boolean.class, "biome")
        .description("Show the biome you're standing in.").defaultValue(false).build());

    private final Setting<Boolean> ping = register(Setting.builder(Boolean.class, "ping")
        .description("Show your ping to the server.").defaultValue(false).build());

    private Vec3 lastPos;
    private double bps;

    public HudInfo() {
        super("HudInfo", "Coordinates, facing, speed, FPS, biome and ping. Drag it in the HUD editor.", Category.MISC);
    }

    @Override
    public void onTick() {
        if (mc.player == null) return;
        Vec3 pos = mc.player.position();
        if (lastPos != null) {
            double dx = pos.x - lastPos.x, dz = pos.z - lastPos.z;
            // Smooth a little so the number doesn't flicker every tick.
            bps = bps * 0.7 + Math.sqrt(dx * dx + dz * dz) * 20.0 * 0.3;
        }
        lastPos = pos;
    }

    private static String facingName(float yaw) {
        String[] names = {"South (+Z)", "West (-X)", "North (-Z)", "East (+X)"};
        int index = Math.floorMod(Math.round(yaw / 90f), 4);
        return names[index];
    }

    @Override
    protected int[] render(GuiGraphicsExtractor g, int x, int y, boolean right) {
        List<String> lines = new ArrayList<>();
        BlockPos pos = mc.player.blockPosition();

        if (coordinates.get()) {
            lines.add(String.format("XYZ: %d, %d, %d", pos.getX(), pos.getY(), pos.getZ()));
            if (otherDimension.get() && mc.level != null) {
                boolean nether = mc.level.dimension() == Level.NETHER;
                double factor = nether ? 8.0 : 1.0 / 8.0;
                lines.add(String.format("%s: %d, %d", nether ? "Overworld" : "Nether",
                    (int) Math.floor(mc.player.getX() * factor), (int) Math.floor(mc.player.getZ() * factor)));
            }
        }
        if (facing.get()) lines.add("Facing: " + facingName(mc.player.getYRot()));
        if (speed.get()) lines.add(String.format("Speed: %.1f b/s", bps));
        if (fps.get()) lines.add("FPS: " + mc.getFps());
        if (biome.get() && mc.level != null) {
            String biomeName = mc.level.getBiome(pos).unwrapKey()
                .map(key -> key.identifier().getPath()).orElse("unknown");
            lines.add("Biome: " + biomeName);
        }
        if (ping.get() && mc.getConnection() != null) {
            PlayerInfo info = mc.getConnection().getPlayerInfo(mc.player.getUUID());
            int latency = info != null ? info.getLatency() : -1;
            lines.add("Ping: " + (latency >= 0 ? latency + "ms" : "n/a"));
        }

        int w = 0;
        for (String l : lines) w = Math.max(w, mc.font.width(l));
        int lineH = mc.font.lineHeight + 2;
        for (int i = 0; i < lines.size(); i++) {
            line(g, lines.get(i), x, y + i * lineH, w, right, CommonColors.WHITE);
        }
        return new int[]{w, lines.size() * lineH};
    }
}
