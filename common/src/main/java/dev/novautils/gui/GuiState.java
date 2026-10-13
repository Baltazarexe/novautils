package dev.novautils.gui;

import dev.novautils.NovaUtils;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import dev.novautils.modules.Category;
import dev.novautils.utils.Storage;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.EnumMap;
import java.util.Map;

/** Remembers where each GUI panel was left, so the menu opens how you arranged it. */
public class GuiState {
    private static final GuiState INSTANCE = new GuiState();
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    private final Map<Category, int[]> positions = new EnumMap<>(Category.class);
    /** Menu style: the classic Meteor-like panels instead of the single Nova window. */
    private boolean classic;
    private boolean loaded;

    public boolean isClassic() {
        if (!loaded) load();
        return classic;
    }

    public void setClassic(boolean value) {
        if (!loaded) load();
        classic = value;
        save();
    }

    /** The menu screen in the chosen style (the open-menu key). */
    public net.minecraft.client.gui.screens.Screen newMenu() {
        return isClassic() ? new dev.novautils.gui.screens.ClassicGuiScreen() : new dev.novautils.gui.screens.ClickGuiScreen();
    }

    public static GuiState get() {
        return INSTANCE;
    }

    private Path file() {
        return Storage.configDir().resolve("novautils_gui.json");
    }

    public int[] position(Category category) {
        return positions.get(category);
    }

    public void store(Category category, int x, int y, boolean open) {
        positions.put(category, new int[]{x, y, open ? 1 : 0});
    }

    public void load() {
        loaded = true;
        Path path = file();
        if (!Files.exists(path)) return;

        try (Reader reader = Files.newBufferedReader(path, StandardCharsets.UTF_8)) {
            JsonObject root = GSON.fromJson(reader, JsonObject.class);
            if (root == null) return;
            if (root.has("_classic")) classic = root.get("_classic").getAsBoolean();
            for (Category category : Category.values()) {
                if (!root.has(category.name())) continue;
                JsonObject obj = root.getAsJsonObject(category.name());
                positions.put(category, new int[]{
                    obj.get("x").getAsInt(), obj.get("y").getAsInt(), obj.get("open").getAsBoolean() ? 1 : 0});
            }
        } catch (IOException | RuntimeException e) {
            NovaUtils.LOG.error("Failed to load GUI state", e);
        }
    }

    public void save() {
        JsonObject root = new JsonObject();
        root.addProperty("_classic", classic);
        positions.forEach((category, pos) -> {
            JsonObject obj = new JsonObject();
            obj.addProperty("x", pos[0]);
            obj.addProperty("y", pos[1]);
            obj.addProperty("open", pos[2] != 0);
            root.add(category.name(), obj);
        });

        try {
            Files.createDirectories(file().getParent());
            try (Writer writer = Files.newBufferedWriter(file(), StandardCharsets.UTF_8)) {
                GSON.toJson(root, writer);
            }
        } catch (IOException e) {
            NovaUtils.LOG.error("Failed to save GUI state", e);
        }
    }
}
