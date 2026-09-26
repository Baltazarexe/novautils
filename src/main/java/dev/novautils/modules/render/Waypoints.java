package dev.novautils.modules.render;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;
import com.mojang.brigadier.arguments.StringArgumentType;
import dev.novautils.NovaUtilsClient;
import dev.novautils.modules.Category;
import dev.novautils.modules.Module;
import dev.novautils.settings.Setting;
import dev.novautils.utils.RenderUtils;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;
import net.fabricmc.fabric.api.client.command.v2.ClientCommands;
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderContext;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.util.ARGB;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.lang.reflect.Type;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * Persistent named markers you can see through walls. Add one at your current position with
 * {@code /waypoint add <name>}, remove with {@code /waypoint remove <name>}, list with {@code /waypoint list}.
 */
public class Waypoints extends Module {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    private final Setting<Double> opacity = register(Setting.builder(Double.class, "opacity")
        .description("Opacity of waypoint markers.")
        .defaultValue(0.5)
        .range(0.05, 1.0)
        .build());

    private final List<Waypoint> waypoints = new ArrayList<>();

    public Waypoints() {
        super("Waypoints", "Persistent markers you can see through walls.", Category.RENDER);
        loadWaypoints();
        registerCommands();
    }

    private void registerCommands() {
        ClientCommandRegistrationCallback.EVENT.register((dispatcher, buildContext) -> dispatcher.register(
            ClientCommands.literal("waypoint")
                .then(ClientCommands.literal("add")
                    .then(ClientCommands.argument("name", StringArgumentType.greedyString())
                        .executes(ctx -> {
                            String name = StringArgumentType.getString(ctx, "name");
                            var player = ctx.getSource().getPlayer();
                            if (player == null) return 0;

                            waypoints.add(new Waypoint(name, player.getX(), player.getY(), player.getZ()));
                            saveWaypoints();
                            ctx.getSource().sendFeedback(Component.literal("Waypoint '" + name + "' added."));
                            return 1;
                        })))
                .then(ClientCommands.literal("remove")
                    .then(ClientCommands.argument("name", StringArgumentType.greedyString())
                        .executes(ctx -> {
                            String name = StringArgumentType.getString(ctx, "name");
                            boolean removed = waypoints.removeIf(w -> w.name.equalsIgnoreCase(name));
                            saveWaypoints();
                            ctx.getSource().sendFeedback(Component.literal(removed
                                ? "Waypoint '" + name + "' removed."
                                : "No waypoint named '" + name + "'."));
                            return 1;
                        })))
                .then(ClientCommands.literal("list")
                    .executes(ctx -> {
                        if (waypoints.isEmpty()) {
                            ctx.getSource().sendFeedback(Component.literal("No waypoints set."));
                        } else {
                            for (Waypoint w : waypoints) {
                                ctx.getSource().sendFeedback(Component.literal(
                                    String.format("%s: %.1f, %.1f, %.1f", w.name, w.x, w.y, w.z)));
                            }
                        }
                        return 1;
                    }))
        ));
    }

    @Override
    public void onRenderWorld(LevelRenderContext context) {
        if (waypoints.isEmpty()) return;

        Vec3 cameraPos = context.levelState().cameraRenderState.pos;
        int color = ARGB.colorFromFloat((float) (double) opacity.get(), 1f, 0.85f, 0.1f);

        for (Waypoint w : waypoints) {
            AABB box = new AABB(w.x - 0.4, w.y, w.z - 0.4, w.x + 0.4, w.y + 256, w.z + 0.4);

            context.poseStack().pushPose();
            context.poseStack().translate(-cameraPos.x, -cameraPos.y, -cameraPos.z);

            context.submitNodeCollector().submitCustomGeometry(context.poseStack(), dev.novautils.utils.EspRender.filledNoDepth(),
                (pose, buffer) -> RenderUtils.drawBox(pose, buffer, box, color));

            context.poseStack().popPose();
        }
    }

    private Path waypointsFile() {
        return FabricLoader.getInstance().getConfigDir().resolve("novautils_waypoints.json");
    }

    private void loadWaypoints() {
        Path file = waypointsFile();
        if (!Files.exists(file)) return;

        try (Reader reader = Files.newBufferedReader(file, StandardCharsets.UTF_8)) {
            Type listType = new TypeToken<List<Waypoint>>() {}.getType();
            List<Waypoint> loaded = GSON.fromJson(reader, listType);
            if (loaded != null) waypoints.addAll(loaded);
        } catch (IOException | RuntimeException e) {
            NovaUtilsClient.LOG.error("Failed to load waypoints", e);
        }
    }

    private void saveWaypoints() {
        try {
            Files.createDirectories(waypointsFile().getParent());
            try (Writer writer = Files.newBufferedWriter(waypointsFile(), StandardCharsets.UTF_8)) {
                GSON.toJson(waypoints, writer);
            }
        } catch (IOException e) {
            NovaUtilsClient.LOG.error("Failed to save waypoints", e);
        }
    }

    private static class Waypoint {
        String name;
        double x, y, z;

        Waypoint(String name, double x, double y, double z) {
            this.name = name;
            this.x = x;
            this.y = y;
            this.z = z;
        }
    }
}
