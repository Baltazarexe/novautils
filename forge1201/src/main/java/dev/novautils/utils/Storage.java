package dev.novautils.utils;

import net.minecraft.client.Minecraft;

import java.nio.file.Path;

/** Where config files live. Both loaders put the game directory in the same place, so this needs no loader API at all. */
public final class Storage {
    private Storage() {}

    public static Path configDir() {
        return Minecraft.getInstance().gameDirectory.toPath().resolve("config");
    }
}
