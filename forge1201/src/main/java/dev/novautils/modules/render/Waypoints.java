package dev.novautils.modules.render;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;
import dev.novautils.NovaUtils;
import dev.novautils.modules.Category;
import dev.novautils.modules.Module;
import dev.novautils.render.RenderContext;
import dev.novautils.settings.Setting;
import dev.novautils.utils.RenderUtils;
import dev.novautils.utils.Storage;
import net.minecraft.network.chat.Component;
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
 * Persistent named markers you can see through walls, with a floating name + distance label.
 * Each waypoint remembers its dimension and only shows there - except that Overworld waypoints can
 * also show in the Nether at their /8 position (and the other way round), which is how you
 * actually travel on 2b2t. The {@code /waypoint add|remove|list} command is registered by each
 * loader's glue code and calls {@link #add}, {@link #remove} and {@link #list}.
 */
public class Waypoints extends Module {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final String OVERWORLD = "minecraft:overworld", NETHER = "minecraft:the_nether";

    private final Setting<Integer> color = register(Setting.builder(Integer.class, "color")
        .description("Beam and label colour.")
        .defaultValue(0x80FFD91A).asColor().build());

    private final Setting<Boolean> beam = register(Setting.builder(Boolean.class, "beam")
        .description("Draw a tall beam so you can spot it from far away.")
        .defaultValue(true).build());

    private final Setting<Boolean> crossDimension = register(Setting.builder(Boolean.class, "nether-overworld")
        .description("Show Overworld waypoints in the Nether at x/8 (and Nether ones in the Overworld at x8).")
        .defaultValue(true).build());

    private final Setting<Double> labelScale = register(Setting.builder(Double.class, "label-scale")
        .description("Size of the name label.")
        .defaultValue(1.0).range(0.5, 3.0).build());

    private final List<Waypoint> waypoints = new ArrayList<>();

    public Waypoints() {
        super("Waypoints", "Named markers with distance, visible through walls.", Category.RENDER);
        loadWaypoints();
    }

    private String currentDimension() {
        return mc.level == null ? OVERWORLD : mc.level.dimension().location().toString();
    }

    public void add(String name, double x, double y, double z) {
        waypoints.removeIf(w -> w.name.equalsIgnoreCase(name));
        Waypoint w = new Waypoint(name, x, y, z);
        w.dimension = currentDimension();
        waypoints.add(w);
        saveWaypoints();
    }

    public boolean remove(String name) {
        boolean removed = waypoints.removeIf(w -> w.name.equalsIgnoreCase(name));
        if (removed) saveWaypoints();
        return removed;
    }

    /** One formatted "name: x, y, z (dimension)" line per waypoint, ready to print. */
    public List<String> list() {
        List<String> lines = new ArrayList<>();
        for (Waypoint w : waypoints) {
            String dim = w.dimension == null ? "" : " (" + w.dimension.replace("minecraft:", "") + ")";
            lines.add(String.format("%s: %.0f, %.0f, %.0f%s", w.name, w.x, w.y, w.z, dim));
        }
        return lines;
    }

    /** Where to draw {@code w} in the current dimension, or null if it doesn't belong here. */
    private Vec3 positionHere(Waypoint w) {
        String here = currentDimension();
        if (w.dimension == null || w.dimension.equals(here)) return new Vec3(w.x, w.y, w.z);
        if (!crossDimension.get()) return null;
        if (w.dimension.equals(OVERWORLD) && here.equals(NETHER)) return new Vec3(w.x / 8, w.y, w.z / 8);
        if (w.dimension.equals(NETHER) && here.equals(OVERWORLD)) return new Vec3(w.x * 8, w.y, w.z * 8);
        return null;
    }

    /** {x, y, z} of every waypoint that shows in this dimension (Minimap). */
    public List<double[]> positionsHere() {
        List<double[]> out = new ArrayList<>();
        for (Waypoint w : waypoints) {
            Vec3 p = positionHere(w);
            if (p != null) out.add(new double[]{p.x, p.y, p.z});
        }
        return out;
    }

    @Override
    public void onRenderWorld(RenderContext context) {
        if (waypoints.isEmpty() || mc.player == null) return;
        Vec3 cam = context.cameraPos();
        int c = color.get();

        for (Waypoint w : waypoints) {
            Vec3 pos = positionHere(w);
            if (pos == null) continue;
            boolean converted = w.dimension != null && !w.dimension.equals(currentDimension());

            if (beam.get()) {
                AABB box = new AABB(pos.x - 0.25, pos.y, pos.z - 0.25, pos.x + 0.25,
                    Math.max(pos.y + 2, mc.level == null ? pos.y + 256 : mc.level.getMaxBuildHeight()), pos.z + 0.25);
                    RenderUtils.world(context, (pose, buffer) -> RenderUtils.drawBox(pose, buffer, box, c));
            }

            double dist = mc.player.position().distanceTo(pos);
            String distText = dist >= 1000 ? String.format("%.1fk", dist / 1000) : String.valueOf(Math.round(dist));
            Component label = Component.literal(w.name).withStyle(s_ -> s_.withColor(c & 0xFFFFFF))
                .append(Component.literal(" " + distText + "m" + (converted ? " ⇄" : "")).withStyle(s_ -> s_.withColor(0xAAAAAA)));
            // clamp the label to 64 blocks away along the view line so it's always readable
            Vec3 eye = mc.player.getEyePosition();
            Vec3 labelPos = pos.add(0, 1.2, 0);
            if (dist > 64) labelPos = eye.add(labelPos.subtract(eye).normalize().scale(64));
            RenderUtils.label(context, labelPos, label, labelScale.get().floatValue(), true);
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
        /** e.g. "minecraft:overworld"; null for waypoints saved before dimensions were tracked. */
        String dimension;

        Waypoint(String name, double x, double y, double z) {
            this.name = name;
            this.x = x;
            this.y = y;
            this.z = z;
        }
    }
}
