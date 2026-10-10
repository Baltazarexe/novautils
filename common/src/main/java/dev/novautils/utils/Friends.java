package dev.novautils.utils;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import dev.novautils.NovaUtils;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;
import java.util.Set;
import java.util.TreeSet;

/**
 * Your friends list (player names, case-insensitive), saved in config/novautils_friends.json.
 * Nametags and Tracers colour friends, Notifier says when one comes into view, and AutoLog's
 * "player nearby" ignores them. Added and removed with the Friends module (middle-click a player).
 */
public final class Friends {
    private Friends() {}

    private static final Set<String> names = new TreeSet<>();
    private static boolean loaded;

    private static Path file() {
        return Storage.configDir().resolve("novautils_friends.json");
    }

    private static void load() {
        if (loaded) return;
        loaded = true;
        try {
            if (Files.exists(file())) {
                Set<String> read = new Gson().fromJson(Files.readString(file(), StandardCharsets.UTF_8),
                    new TypeToken<Set<String>>() {}.getType());
                if (read != null) for (String n : read) names.add(n.toLowerCase(Locale.ROOT));
            }
        } catch (Exception e) {
            NovaUtils.LOG.error("Failed to load friends", e);
        }
    }

    private static void save() {
        try {
            Files.createDirectories(file().getParent());
            Files.writeString(file(), new Gson().toJson(names), StandardCharsets.UTF_8);
        } catch (Exception e) {
            NovaUtils.LOG.error("Failed to save friends", e);
        }
    }

    public static boolean isFriend(String name) {
        load();
        return names.contains(name.toLowerCase(Locale.ROOT));
    }

    public static boolean isFriend(Entity e) {
        return e instanceof Player p && isFriend(p.getName().getString());
    }

    /** Adds or removes; returns true if they are a friend now. */
    public static boolean toggle(String name) {
        load();
        String key = name.toLowerCase(Locale.ROOT);
        boolean now = !names.remove(key);
        if (now) names.add(key);
        save();
        return now;
    }

    public static Set<String> all() {
        load();
        return names;
    }
}
