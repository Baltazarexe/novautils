package dev.novautils.modules.render;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;
import dev.novautils.NovaUtils;
import dev.novautils.modules.Category;
import dev.novautils.modules.Module;
import dev.novautils.render.RenderContext;
import dev.novautils.settings.Setting;
import dev.novautils.utils.EspRender;
import dev.novautils.utils.RenderUtils;
import dev.novautils.utils.Storage;
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
 * Persistent named markers you can see through walls. The {@code /waypoint add|remove|list}
 * command is registered by each loader's glue code (Brigadier command trees are tied to a
 * loader-specific source type), which calls straight into {@link #add}, {@link #remove} and
 * {@link #list} below - this class only holds the data and the shared logic.
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
    }

    public void add(String name, double x, double y, double z) {
        waypoints.add(new Waypoint(name, x, y, z));
        saveWaypoints();
    }

    public boolean remove(String name) {
        boolean removed = waypoints.removeIf(w -> w.name.equalsIgnoreCase(name));
        if (removed) saveWaypoints();
        return removed;
    }

    /** One formatted "name: x, y, z" line per waypoint, ready to print. */
    public List<String> list() {
        List<String> lines = new ArrayList<>();
        for (Waypoint w : waypoints) {
            lines.add(String.format("%s: %.1f, %.1f, %.1f", w.name, w.x, w.y, w.z));
        }
        return lines;
    }

    @Override
    public void onRenderWorld(RenderContext context) {
        if (waypoints.isEmpty()) return;

        Vec3 cameraPos = context.cameraPos();
        int color = ARGB.colorFromFloat((float) (double) opacity.get(), 1f, 0.85f, 0.1f);

        for (Waypoint w : waypoints) {
            AABB box = new AABB(w.x - 0.4, w.y, w.z - 0.4, w.x + 0.4, w.y + 256, w.z + 0.4);

            context.poseStack().pushPose();
            context.poseStack().translate(-cameraPos.x, -cameraPos.y, -cameraPos.z);

            context.submitNodeCollector().submitCustomGeometry(context.poseStack(), EspRender.filledNoDepth(),
                (pose, buffer) -> RenderUtils.drawBox(pose, buffer, box, color));

            context.poseStack().popPose();
        }
    }

    private Path waypointsFile() {
        return Storage.configDir().resolve("novautils_waypoints.json");
    }

    private void loadWaypoints() {
        Path file = waypointsFile();
        if (!Files.exists(file)) return;

        try (Reader reader = Files.newBufferedReader(file, StandardCharsets.UTF_8)) {
            Type listType = new TypeToken<List<Waypoint>>() {}.getType();
            List<Waypoint> loaded = GSON.fromJson(reader, listType);
            if (loaded != null) waypoints.addAll(loaded);
        } catch (IOException | RuntimeException e) {
            NovaUtils.LOG.error("Failed to load waypoints", e);
        }
    }

    private void saveWaypoints() {
        try {
            Files.createDirectories(waypointsFile().getParent());
            try (Writer writer = Files.newBufferedWriter(waypointsFile(), StandardCharsets.UTF_8)) {
                GSON.toJson(waypoints, writer);
            }
        } catch (IOException e) {
            NovaUtils.LOG.error("Failed to save waypoints", e);
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
