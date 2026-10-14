package dev.novautils.utils;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;
import dev.novautils.NovaUtils;
import net.minecraft.client.Minecraft;
import net.minecraft.client.User;
import net.minecraft.core.UUIDUtil;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Account manager: offline (cracked) nicks you can switch to without restarting, for servers in
 * offline mode. The account the launcher started the game with is always kept, so you can switch
 * back to it. Only nick names are stored (config/novautils_accounts.json) - no passwords, no tokens.
 */
public final class Accounts {
    private Accounts() {}

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static List<String> offline;
    private static User launcher;

    private static Path file() {
        return Storage.configDir().resolve("novautils_accounts.json");
    }

    /** The account the game was started with (remembered the first time this is asked). */
    public static User launcherUser() {
        if (launcher == null) launcher = Minecraft.getInstance().getUser();
        return launcher;
    }

    public static List<String> offline() {
        if (offline == null) {
            offline = new ArrayList<>();
            if (Files.exists(file())) {
                try (Reader r = Files.newBufferedReader(file(), StandardCharsets.UTF_8)) {
                    List<String> read = GSON.fromJson(r, new TypeToken<List<String>>() {}.getType());
                    if (read != null) offline.addAll(read);
                } catch (IOException | RuntimeException e) {
                    NovaUtils.LOG.error("Failed to load accounts", e);
                }
            }
        }
        return offline;
    }

    private static void save() {
        try {
            Files.createDirectories(file().getParent());
            try (Writer w = Files.newBufferedWriter(file(), StandardCharsets.UTF_8)) {
                GSON.toJson(offline(), w);
            }
        } catch (IOException e) {
            NovaUtils.LOG.error("Failed to save accounts", e);
        }
    }

    /** Valid Minecraft nick: 3-16 letters, digits or underscore. */
    public static boolean validName(String name) {
        return name != null && name.matches("[A-Za-z0-9_]{3,16}");
    }

    public static boolean addOffline(String name) {
        if (!validName(name)) return false;
        for (String n : offline()) if (n.equalsIgnoreCase(name)) return false;
        offline().add(name);
        save();
        return true;
    }

    public static void removeOffline(String name) {
        offline().removeIf(n -> n.equalsIgnoreCase(name));
        save();
    }

    private static void setUser(User user) {
        launcherUser();
        ((dev.novautils.mixin.MinecraftUserAccessor) Minecraft.getInstance()).novautils$setUser(user);
    }

    /** Plays as an offline nick from now on (only offline-mode servers will accept it). */
    public static void useOffline(String name) {
        setUser(new User(name, UUIDUtil.createOfflinePlayerUUID(name), "0", Optional.empty(), Optional.empty(), User.Type.LEGACY));
    }

    /** Back to the account the launcher logged in. */
    public static void useLauncher() {
        setUser(launcherUser());
    }

    public static String current() {
        return Minecraft.getInstance().getUser().getName();
    }

    public static boolean usingLauncher() {
        return Minecraft.getInstance().getUser() == launcherUser();
    }
}
