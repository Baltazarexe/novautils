package dev.novautils.commands;

import com.mojang.blaze3d.platform.InputConstants;
import dev.novautils.config.Config;
import dev.novautils.modules.Module;
import dev.novautils.modules.ModuleManager;
import dev.novautils.settings.Setting;
import dev.novautils.utils.Friends;
import dev.novautils.utils.Keybinds;
import dev.novautils.utils.Storage;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.stream.Stream;

/**
 * Chat commands: anything typed starting with {@link #PREFIX} is handled here and never sent to
 * the server (ChatSendMixin). {@code .help} lists them.
 */
public final class Commands {
    public static final String PREFIX = ".";

    private Commands() {}

    private static final Minecraft mc = Minecraft.getInstance();

    private static void say(String text) {
        say(text, 0xE8EAF0);
    }

    private static void say(String text, int color) {
        if (mc.player == null) return;
        mc.player.sendSystemMessage(Component.literal("[Nova] ").withColor(0x8B9CFF)
            .append(Component.literal(text).withColor(color)));
    }

    private static void error(String text) {
        say(text, 0xFF7A7A);
    }

    /** ChatSendMixin. True = it was a command (handled, don't send it). */
    public static boolean handle(String message) {
        if (!message.startsWith(PREFIX) || message.length() == PREFIX.length()) return false;
        String[] args = message.substring(PREFIX.length()).trim().split("\\s+");
        try {
            run(args[0].toLowerCase(Locale.ROOT), args);
        } catch (RuntimeException e) {
            error("Error: " + e.getMessage());
        }
        return true;
    }

    private static Module module(String name) {
        for (Module m : ModuleManager.get().getModules()) {
            if (m.name.equalsIgnoreCase(name)) return m;
        }
        throw new IllegalArgumentException("no module called " + name);
    }

    private static String rest(String[] a, int from) {
        return String.join(" ", java.util.Arrays.copyOfRange(a, from, a.length));
    }

    private static void need(String[] a, int count, String usage) {
        if (a.length < count) throw new IllegalArgumentException("usage: " + PREFIX + usage);
    }

    private static void run(String cmd, String[] a) {
        switch (cmd) {
            case "help", "h" -> help();
            case "t", "toggle" -> {
                need(a, 2, "t <module>");
                Module m = module(a[1]);
                m.toggle();
                say(m.name + (m.isEnabled() ? " on" : " off"), m.isEnabled() ? 0x66DD77 : 0x9196A4);
            }
            case "bind" -> {
                need(a, 3, "bind <module> <key|mouse4|none>");
                Module m = module(a[1]);
                int code = parseKey(a[2]);
                m.setKeyBind(code);
                say(m.name + " bound to " + Keybinds.name(code));
            }
            case "set" -> {
                need(a, 4, "set <module> <setting> <value>");
                setting(module(a[1]), a[2], rest(a, 3));
            }
            case "reset" -> {
                need(a, 2, "reset <module>");
                Module m = module(a[1]);
                for (Setting<?> s : m.getSettings()) s.reset();
                say(m.name + " settings reset");
            }
            case "wp", "waypoint" -> waypoint(a);
            case "friend", "f" -> friend(a);
            case "coords" -> {
                if (mc.player == null) return;
                String c = mc.player.getBlockX() + " " + mc.player.getBlockY() + " " + mc.player.getBlockZ();
                mc.keyboardHandler.setClipboard(c);
                say("Copied " + c);
            }
            case "profile", "p" -> profile(a);
            case "config" -> config(a);
            case "find" -> {
                need(a, 2, "find <item>");
                var tracker = ModuleManager.get().get(dev.novautils.modules.misc.ChestTracker.class);
                if (!tracker.isEnabled()) throw new IllegalArgumentException("turn ChestTracker on first");
                List<String> lines = tracker.find(rest(a, 1));
                if (lines.isEmpty()) say("Not in any container you opened (" + tracker.tracked() + " remembered).");
                else for (String l : lines.subList(0, Math.min(10, lines.size()))) say(l, 0xFFD84A);
            }
            case "echest" -> {
                var items = ModuleManager.get().get(dev.novautils.modules.misc.ChestTracker.class).enderChest();
                if (items == null) say("Open your ender chest once first.");
                else if (items.isEmpty()) say("Your ender chest is empty.");
                else for (var s : items) say(s.getCount() + "x " + s.getHoverName().getString());
            }
            case "seed" -> {
                var cb = ModuleManager.get().get(dev.novautils.modules.render.ChunkBorders.class);
                if (a.length < 2) {
                    Long s = cb.seed();
                    say(s == null ? "No seed yet: " + PREFIX + "seed <number>, or " + PREFIX + "seed 2b2t" : "Seed: " + s);
                    return;
                }
                String value = a[1].equalsIgnoreCase("2b2t") ? String.valueOf(dev.novautils.utils.StructureFinder.SEED_2B2T) : rest(a, 1);
                cb.setSeed(value);
                say("Seed set to " + cb.seed() + " (" + PREFIX + "locate, slime chunks)");
            }
            case "locate", "loc" -> locate(a);
            case "sort" -> ModuleManager.get().get(dev.novautils.modules.player.InventorySort.class).setEnabled(true);
            case "drop" -> drop(a);
            case "panic" -> say("Turned off " + ModuleManager.get().disableAll() + " modules");
            default -> error("Unknown command. " + PREFIX + "help lists them.");
        }
        ModuleManager.get().markDirty();
    }

    private static void help() {
        say("Commands:");
        String[][] list = {
            {"t <module>", "toggle a module"},
            {"bind <module> <key>", "bind a key, mouse4/mouse5, or none"},
            {"set <module> <setting> <value>", "change a setting"},
            {"reset <module>", "reset a module's settings"},
            {"wp add <name> [x y z] | del <name> | list | clear", "waypoints"},
            {"friend add|del|list [name]", "friends"},
            {"coords", "copy your coordinates"},
            {"profile save|load|del|list [name]", "whole-config profiles"},
            {"config export|import", "copy / paste your config as text"},
            {"find <item>", "where you left it (ChestTracker)"},
            {"echest", "what's in your ender chest"},
            {"sort", "sort the open chest or your inventory"},
            {"drop add|del|list [item]", "AutoDrop's extra items"},
            {"seed [seed|2b2t]", "world seed for .locate and slime chunks"},
            {"locate <structure> [count]", "nearest structures from the seed (" + PREFIX + "locate list)"},
            {"panic", "turn every module off"},
        };
        for (String[] c : list) {
            mc.player.sendSystemMessage(Component.literal("  " + PREFIX + c[0]).withColor(0xC49BFF)
                .append(Component.literal(" - " + c[1]).withColor(0x9196A4)));
        }
    }

    public static int parseKey(String name) {
        String n = name.toLowerCase(Locale.ROOT);
        if (n.equals("none") || n.equals("unbind")) return -1;
        if (n.startsWith("mouse")) {
            int b = Integer.parseInt(n.substring(5));
            return Keybinds.mouse(b - 1);
        }
        n = switch (n) {
            case "rshift" -> "right.shift";
            case "lshift" -> "left.shift";
            case "rctrl" -> "right.control";
            case "lctrl" -> "left.control";
            case "ralt" -> "right.alt";
            case "lalt" -> "left.alt";
            default -> n;
        };
        try {
            return InputConstants.getKey("key.keyboard." + n).getValue();
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("unknown key " + name);
        }
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private static void setting(Module m, String name, String value) {
        for (Setting<?> s : m.getSettings()) {
            if (!s.name.equalsIgnoreCase(name)) continue;
            Object v;
            if (s.type == Boolean.class) v = Boolean.parseBoolean(value) || value.equalsIgnoreCase("on");
            else if (s.type == Integer.class) v = s.color ? (int) Long.parseLong(value.replace("#", ""), 16) : Integer.parseInt(value);
            else if (s.type == Double.class) v = Double.parseDouble(value);
            else if (s.type == String.class) v = value;
            else if (Enum.class.isAssignableFrom(s.type)) v = Enum.valueOf((Class) s.type, value.toUpperCase(Locale.ROOT));
            else throw new IllegalArgumentException("can't set " + name);
            ((Setting<Object>) s).set(v);
            say(m.name + " " + s.name + " = " + s.get());
            return;
        }
        throw new IllegalArgumentException(m.name + " has no setting " + name);
    }

    private static void waypoint(String[] a) {
        need(a, 2, "wp add|del|list [name]");
        var wps = ModuleManager.get().get(dev.novautils.modules.render.Waypoints.class);
        switch (a[1].toLowerCase(Locale.ROOT)) {
            case "add" -> {
                need(a, 3, "wp add <name> [x y z]");
                // ".wp add base 100 64 -200" marks coordinates, ".wp add base" marks where you stand
                if (a.length >= 6) {
                    String name = String.join(" ", java.util.Arrays.copyOfRange(a, 2, a.length - 3));
                    double x = Double.parseDouble(a[a.length - 3]), y = Double.parseDouble(a[a.length - 2]), z = Double.parseDouble(a[a.length - 1]);
                    wps.add(name, x, y, z);
                    wps.setEnabled(true);
                    say("Waypoint " + name + " added at " + (int) x + " " + (int) y + " " + (int) z);
                } else {
                    wps.add(rest(a, 2), mc.player.getX(), mc.player.getY(), mc.player.getZ());
                    wps.setEnabled(true);
                    say("Waypoint " + rest(a, 2) + " added here");
                }
            }
            case "del", "remove" -> {
                need(a, 3, "wp del <name>");
                say(wps.remove(rest(a, 2)) ? "Removed" : "No waypoint called " + rest(a, 2));
            }
            case "clear" -> say("Removed " + wps.clear() + " waypoints");
            case "list" -> {
                var lines = wps.list();
                if (lines.isEmpty()) say("No waypoints");
                for (String l : lines) say(l);
            }
            default -> error("usage: " + PREFIX + "wp add <name> [x y z] | del <name> | list | clear");
        }
    }

    private static void friend(String[] a) {
        need(a, 2, "friend add|del|list [name]");
        switch (a[1].toLowerCase(Locale.ROOT)) {
            case "add" -> {
                need(a, 3, "friend add <name>");
                if (!Friends.isFriend(a[2])) Friends.toggle(a[2]);
                say(a[2] + " is a friend");
            }
            case "del", "remove" -> {
                need(a, 3, "friend del <name>");
                if (Friends.isFriend(a[2])) Friends.toggle(a[2]);
                say(a[2] + " removed");
            }
            case "list" -> say(Friends.all().isEmpty() ? "No friends yet" : String.join(", ", Friends.all()));
            default -> error("usage: " + PREFIX + "friend add|del|list [name]");
        }
    }

    private static Path profiles() {
        return Storage.configDir().resolve("novautils_profiles");
    }

    private static String safe(String name) {
        return name.replaceAll("[^A-Za-z0-9_-]", "_");
    }

    private static void profile(String[] a) {
        need(a, 2, "profile save|load|del|list [name]");
        var modules = ModuleManager.get().getModules();
        switch (a[1].toLowerCase(Locale.ROOT)) {
            case "save" -> {
                need(a, 3, "profile save <name>");
                Config.get().saveTo(modules, profiles().resolve(safe(a[2]) + ".json"));
                say("Profile " + a[2] + " saved");
            }
            case "load" -> {
                need(a, 3, "profile load <name>");
                if (!Config.get().loadFrom(modules, profiles().resolve(safe(a[2]) + ".json"))) {
                    throw new IllegalArgumentException("no profile called " + a[2]);
                }
                say("Profile " + a[2] + " loaded");
            }
            case "del", "delete" -> {
                need(a, 3, "profile del <name>");
                try {
                    say(Files.deleteIfExists(profiles().resolve(safe(a[2]) + ".json")) ? "Deleted" : "No profile called " + a[2]);
                } catch (IOException e) {
                    throw new IllegalArgumentException(e.getMessage());
                }
            }
            case "list" -> {
                List<String> names = new ArrayList<>();
                if (Files.isDirectory(profiles())) {
                    try (Stream<Path> s = Files.list(profiles())) {
                        s.forEach(p -> names.add(p.getFileName().toString().replace(".json", "")));
                    } catch (IOException ignored) {
                    }
                }
                say(names.isEmpty() ? "No profiles yet (" + PREFIX + "profile save <name>)" : "Profiles: " + String.join(", ", names));
            }
            default -> error("usage: " + PREFIX + "profile save|load|del|list [name]");
        }
    }

    private static void config(String[] a) {
        need(a, 2, "config export|import");
        var modules = ModuleManager.get().getModules();
        switch (a[1].toLowerCase(Locale.ROOT)) {
            case "export" -> {
                mc.keyboardHandler.setClipboard(Config.get().exportString(modules));
                say("Config copied to the clipboard");
            }
            case "import" -> say(Config.get().importString(modules, mc.keyboardHandler.getClipboard())
                ? "Config imported from the clipboard" : "The clipboard doesn't hold a NovaUtils config", 0xFFD84A);
            default -> error("usage: " + PREFIX + "config export|import");
        }
    }

    private static void locate(String[] a) {
        need(a, 2, "locate <structure> [count]  (" + PREFIX + "locate list)");
        if (a[1].equalsIgnoreCase("list")) {
            List<String> names = new ArrayList<>();
            for (var k : dev.novautils.utils.StructureFinder.KINDS) names.add(k.name());
            say("Structures: " + String.join(", ", names));
            return;
        }
        var kind = dev.novautils.utils.StructureFinder.find(a[1]);
        if (kind == null) throw new IllegalArgumentException("unknown structure " + a[1] + " (" + PREFIX + "locate list)");
        Long seed = ModuleManager.get().get(dev.novautils.modules.render.ChunkBorders.class).seed();
        if (seed == null) throw new IllegalArgumentException("set the world seed first: " + PREFIX + "seed <number>");
        int count = a.length > 2 ? Math.max(1, Math.min(10, Integer.parseInt(a[2]))) : 3;

        // work in the structure's own dimension (nether coordinates are 1/8 of the overworld's)
        String here = mc.level.dimension().identifier().getPath();
        double x = mc.player.getX(), z = mc.player.getZ();
        var dim = kind.dim();
        boolean inNether = here.equals("the_nether");
        if (dim == dev.novautils.utils.StructureFinder.Dim.NETHER && !inNether) { x /= 8; z /= 8; }
        if (dim == dev.novautils.utils.StructureFinder.Dim.OVERWORLD && inNether) { x *= 8; z *= 8; }
        var results = dev.novautils.utils.StructureFinder.nearest(seed, kind, (int) x, (int) z, count, 160);
        if (results.isEmpty()) {
            say("None within range.");
            return;
        }
        String dimName = switch (dim) { case NETHER -> "nether"; case END -> "the end"; default -> "overworld"; };
        say("Nearest " + kind.name() + " (" + dimName + " coords):");
        for (var r : results) {
            say("  " + r.x() + ", " + r.z() + "  (" + Math.round(r.distance()) + "m)" + (r.note().isEmpty() ? "" : "  " + r.note()), 0xFFD84A);
        }
        say("Candidate spots from the seed - the game also needs the right biome/terrain, so one may be empty.", 0x9196A4);
        boolean sameDim = switch (dim) { case NETHER -> inNether; case END -> here.equals("the_end"); default -> here.equals("overworld"); };
        if (sameDim) {
            var wps = ModuleManager.get().get(dev.novautils.modules.render.Waypoints.class);
            wps.remove(kind.name());
            wps.add(kind.name(), results.get(0).x(), mc.player.getY(), results.get(0).z());
            wps.setEnabled(true);
            say("Waypoint \"" + kind.name() + "\" set on the nearest one.", 0x9196A4);
        }
    }

    private static void drop(String[] a) {
        need(a, 2, "drop add|del|list [item]");
        var ad = ModuleManager.get().get(dev.novautils.modules.player.AutoDrop.class);
        switch (a[1].toLowerCase(Locale.ROOT)) {
            case "add" -> {
                need(a, 3, "drop add <item>");
                say(ad.addCustom(a[2]) ? a[2] + " will be dropped" : a[2] + " is already on the list");
            }
            case "del", "remove" -> {
                need(a, 3, "drop del <item>");
                say(ad.removeCustom(a[2]) ? a[2] + " removed" : a[2] + " wasn't on the list");
            }
            case "list" -> say(ad.customList().isEmpty() ? "No extra items" : String.join(", ", ad.customList()));
            default -> error("usage: " + PREFIX + "drop add|del|list [item]");
        }
    }
}
