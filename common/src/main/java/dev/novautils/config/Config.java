package dev.novautils.config;

import dev.novautils.NovaUtils;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;
import dev.novautils.modules.Module;
import dev.novautils.settings.Setting;
import dev.novautils.utils.Storage;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

/** Very small JSON config: one object per module holding its enabled state, keybind and settings. */
public class Config {
    private static final Config INSTANCE = new Config();
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    private static final String VERSION_KEY = "_configVersion";
    /** Bumped when saved keybind numbers stop meaning the same key. */
    private static final int KEYBIND_FORMAT = 2;

    public static Config get() {
        return INSTANCE;
    }

    private Path path() {
        return Storage.configDir().resolve("novautils.json");
    }

    public void load(List<Module> modules) {
        Path file = path();
        if (!Files.exists(file)) return;

        try (Reader reader = Files.newBufferedReader(file, StandardCharsets.UTF_8)) {
            JsonObject root = GSON.fromJson(reader, JsonObject.class);
            if (root == null) return;

            // MC 26.3 swapped GLFW key codes for SDL scancodes, so keybinds written by an older
            // build now point at completely different keys. Drop them instead of toggling modules
            // at random while the player walks around.
            boolean keybindsUsable = root.has(VERSION_KEY) && root.get(VERSION_KEY).getAsInt() >= KEYBIND_FORMAT;
            if (!keybindsUsable) {
                NovaUtils.LOG.warn("Config predates the SDL key change; clearing saved keybinds.");
            }

            for (Module module : modules) {
                if (!root.has(module.name)) continue;
                JsonObject obj = root.getAsJsonObject(module.name);

                if (obj.has("enabled") && obj.get("enabled").getAsBoolean()) {
                    module.setEnabled(true);
                }
                if (keybindsUsable && obj.has("keyBind")) {
                    module.setKeyBind(obj.get("keyBind").getAsInt());
                }

                if (obj.has("settings")) {
                    JsonObject settingsObj = obj.getAsJsonObject("settings");
                    for (Setting<?> setting : module.getSettings()) {
                        if (!settingsObj.has(setting.name)) continue;
                        applyValue(setting, settingsObj.get(setting.name));
                    }
                }
            }
        } catch (IOException | RuntimeException e) {
            NovaUtils.LOG.error("Failed to load config", e);
        }
    }

    @SuppressWarnings("unchecked")
    private void applyValue(Setting<?> setting, JsonElement element) {
        try {
            if (setting.type == Boolean.class) {
                ((Setting<Boolean>) setting).set(element.getAsBoolean());
            } else if (setting.type == Integer.class) {
                ((Setting<Integer>) setting).set(element.getAsInt());
            } else if (setting.type == Double.class) {
                ((Setting<Double>) setting).set(element.getAsDouble());
            } else if (setting.type == String.class) {
                ((Setting<String>) setting).set(element.getAsString());
            } else if (Enum.class.isAssignableFrom(setting.type)) {
                @SuppressWarnings("rawtypes")
                Class rawType = setting.type;
                @SuppressWarnings("unchecked")
                Object value = Enum.valueOf(rawType, element.getAsString());
                ((Setting<Object>) setting).set(value);
            }
        } catch (RuntimeException ignored) {
            // Bad/outdated config value for this setting; keep the default.
        }
    }

    public void save(List<Module> modules) {
        JsonObject root = new JsonObject();
        root.addProperty(VERSION_KEY, KEYBIND_FORMAT);

        for (Module module : modules) {
            JsonObject obj = new JsonObject();
            obj.addProperty("enabled", module.isEnabled());
            obj.addProperty("keyBind", module.getKeyBind());

            JsonObject settingsObj = new JsonObject();
            for (Setting<?> setting : module.getSettings()) {
                Object value = setting.get();
                if (value instanceof Enum<?> e) {
                    settingsObj.add(setting.name, new JsonPrimitive(e.name()));
                } else if (value instanceof Boolean b) {
                    settingsObj.add(setting.name, new JsonPrimitive(b));
                } else if (value instanceof Number n) {
                    settingsObj.add(setting.name, new JsonPrimitive(n));
                } else if (value instanceof String s) {
                    settingsObj.add(setting.name, new JsonPrimitive(s));
                }
            }
            obj.add("settings", settingsObj);

            root.add(module.name, obj);
        }

        try {
            Files.createDirectories(path().getParent());
            try (Writer writer = Files.newBufferedWriter(path(), StandardCharsets.UTF_8)) {
                GSON.toJson(root, writer);
            }
        } catch (IOException e) {
            NovaUtils.LOG.error("Failed to save config", e);
        }
    }
}
