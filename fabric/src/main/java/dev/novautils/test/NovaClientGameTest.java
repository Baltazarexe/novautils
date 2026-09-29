package dev.novautils.test;

import com.mojang.blaze3d.platform.InputConstants;
import dev.novautils.modules.movement.AutoSprint;
import dev.novautils.modules.render.Fullbright;

import dev.novautils.config.Config;
import dev.novautils.gui.Theme;
import dev.novautils.gui.Toasts;
import dev.novautils.gui.screens.ClickGuiScreen;
import dev.novautils.gui.screens.HudEditorScreen;
import dev.novautils.modules.Module;
import dev.novautils.modules.ModuleManager;
import dev.novautils.modules.misc.ActiveModules;
import dev.novautils.modules.misc.ArmorHud;
import dev.novautils.modules.misc.HudInfo;
import dev.novautils.modules.misc.KeyStrokes;
import dev.novautils.modules.misc.Notifications;
import dev.novautils.modules.misc.Notifier;
import dev.novautils.modules.misc.PotionHud;
import dev.novautils.modules.misc.TotemCounter;
import dev.novautils.modules.render.Esp;
import dev.novautils.modules.render.StorageEsp;
import dev.novautils.modules.render.Xray;
import dev.novautils.settings.Setting;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.fabricmc.loader.api.FabricLoader;

import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Dev-only in-game test: builds a small scene (ores and a zombie behind a stone wall, some storage)
 * and takes framebuffer screenshots of each feature, plus two assertions that read config from disk.
 * Run with {@code ./gradlew :fabric:runClientGameTest}; screenshots land in
 * fabric/build/run/clientGameTest/screenshots.
 */
@SuppressWarnings("unchecked")
public class NovaClientGameTest implements FabricClientGameTest {

    @SuppressWarnings("rawtypes")
    private static Setting setting(Class<? extends Module> type, String name) {
        for (Setting<?> s : ModuleManager.get().get(type).getSettings()) {
            if (s.name.equals(name)) return s;
        }
        throw new AssertionError("no setting " + name + " on " + type.getSimpleName());
    }

    private static void on(Class<? extends Module> type) {
        ModuleManager.get().get(type).setEnabled(true);
    }

    private static void allOff() {
        for (Module m : ModuleManager.get().getModules()) m.setEnabled(false);
    }

    private static void check(boolean ok, String what) {
        if (!ok) throw new AssertionError("menu test: " + what);
        System.out.println("[novautils-test] ok: " + what);
    }

    /** Moves the real cursor to a GUI-space point (converting to window pixels). */
    private static void cursor(ClientGameTestContext ctx, int guiX, int guiY) {
        double[] p = ctx.computeOnClient(c -> {
            var w = c.getWindow();
            double sx = w.getScreenWidth() / (double) w.getGuiScaledWidth();
            double sy = w.getScreenHeight() / (double) w.getGuiScaledHeight();
            return new double[]{(guiX + 0.5) * sx, (guiY + 0.5) * sy};
        });
        ctx.getInput().setCursorPos(p[0], p[1]);
    }

    private static void click(ClientGameTestContext ctx, int[] at, int button) {
        if (at == null) throw new AssertionError("menu test: row to click is not on screen");
        cursor(ctx, at[0], at[1]);
        ctx.waitTicks(2);
        ctx.getInput().pressMouse(button);
        ctx.waitTicks(3);
    }

    private static int[] row(ClientGameTestContext ctx, Class<? extends Module> type, String setting) {
        return ctx.computeOnClient(c -> c.gui.screen() instanceof ClickGuiScreen s
            ? s.rowCenter(ModuleManager.get().get(type), setting) : null);
    }

    private static final int[] RENDER = {60, 80}, MOVEMENT = {185, 80};

    /** Scrolls the panel under {@code panelAt} with the mouse wheel until the row is fully shown. */
    private static int[] reveal(ClientGameTestContext ctx, int[] panelAt, Class<? extends Module> type, String setting) {
        for (int i = 0; i < 60; i++) {
            int[] r = row(ctx, type, setting);
            if (r != null) return r;
            cursor(ctx, panelAt[0], panelAt[1]);
            ctx.waitTick();
            ctx.getInput().scroll(i < 20 ? -1 : 1);
            ctx.waitTicks(2);
        }
        return null;
    }

    private static <T extends Module> T mod(Class<T> type) {
        return ModuleManager.get().get(type);
    }

    /**
     * Drives the menu the way a player does: real key presses, cursor moves and clicks - nothing
     * set directly. Opens it with the keybind, toggles, expands, flips a setting, drags a slider,
     * middle-click resets, searches, binds a key and uses it, and drags a HUD element in the editor.
     */
    private static void menuInteraction(ClientGameTestContext ctx) {
        ctx.runOnClient(client -> {
            allOff();
            for (Module m : ModuleManager.get().getModules()) ClickGuiScreen.setExpanded(m, false);
            mod(AutoSprint.class).setKeyBind(-1);
        });
        ctx.waitTicks(5);

        // open with Right Shift
        ctx.getInput().pressKey(InputConstants.KEY_RSHIFT);
        ctx.waitForScreen(ClickGuiScreen.class);
        ctx.waitTicks(3);
        check(true, "Right Shift opens the menu");
        check(row(ctx, Fullbright.class, null) == null, "RENDER panel remembers its scroll (Fullbright scrolled off)");
        // mouse wheel up over the RENDER panel brings it back
        cursor(ctx, 60, 80);
        ctx.waitTicks(2);
        for (int i = 0; i < 10; i++) ctx.getInput().scroll(1);
        ctx.waitTicks(3);
        check(row(ctx, Fullbright.class, null) != null, "mouse wheel scrolls the RENDER panel back up");
        ctx.takeScreenshot("08a_menu_opened");

        // left-click toggles a module on and off
        click(ctx, row(ctx, Fullbright.class, null), 0);
        check(ctx.computeOnClient(c -> mod(Fullbright.class).isEnabled()), "left-click enables Fullbright");
        click(ctx, row(ctx, Fullbright.class, null), 0);
        check(ctx.computeOnClient(c -> !mod(Fullbright.class).isEnabled()), "second left-click disables Fullbright");

        // right-click expands; a boolean setting flips on click
        click(ctx, row(ctx, Xray.class, null), 1);
        check(reveal(ctx, RENDER, Xray.class, "diamond") != null, "right-click shows Xray settings");
        click(ctx, row(ctx, Xray.class, "diamond"), 0);
        check(ctx.computeOnClient(c -> !((Setting<Boolean>) setting(Xray.class, "diamond")).get()), "click turns diamond off");
        ctx.takeScreenshot("08_menu_clicked_settings");
        click(ctx, row(ctx, Xray.class, "diamond"), 0);
        check(ctx.computeOnClient(c -> ((Setting<Boolean>) setting(Xray.class, "diamond")).get()), "click turns diamond back on");

        // slider: click the far left of the radius track -> minimum; middle-click -> default
        int[] radius = reveal(ctx, RENDER, Xray.class, "radius");
        check(radius != null, "radius slider visible");
        click(ctx, new int[]{radius[0] - 50, radius[1]}, 0);
        ctx.getInput().releaseMouse(0);
        int min = ctx.computeOnClient(c -> ((Setting<Integer>) setting(Xray.class, "radius")).get());
        int def = ctx.computeOnClient(c -> (Integer) setting(Xray.class, "radius").defaultValue);
        check(min < def, "slider drag lowers radius (" + def + " -> " + min + ")");
        click(ctx, reveal(ctx, RENDER, Xray.class, "radius"), 2);
        check(ctx.computeOnClient(c -> ((Setting<Integer>) setting(Xray.class, "radius")).get()) == def,
            "middle-click resets radius to " + def);
        click(ctx, reveal(ctx, RENDER, Xray.class, null), 1);
        check(row(ctx, Xray.class, "diamond") == null, "right-click collapses Xray");

        // search filters, ESC clears it without closing
        ctx.getInput().typeChars("sprint");
        ctx.waitTicks(3);
        check(row(ctx, AutoSprint.class, null) != null, "search 'sprint' shows AutoSprint");
        check(row(ctx, Fullbright.class, null) == null, "search 'sprint' hides Fullbright");
        ctx.takeScreenshot("09_menu_search");
        ctx.getInput().pressKey(InputConstants.KEY_ESCAPE);
        ctx.waitTicks(3);
        check(ctx.computeOnClient(c -> c.gui.screen() instanceof ClickGuiScreen s && s.searchText().isEmpty()),
            "first ESC clears search, menu stays open");

        // bind AutoSprint to K through the bind row
        click(ctx, row(ctx, AutoSprint.class, null), 1);
        click(ctx, reveal(ctx, MOVEMENT, AutoSprint.class, "bind"), 0);
        ctx.getInput().pressKey(InputConstants.KEY_K);
        ctx.waitTicks(3);
        check(ctx.computeOnClient(c -> mod(AutoSprint.class).getKeyBind()) == InputConstants.KEY_K, "bind row captures K");
        ctx.takeScreenshot("10_menu_keybind");

        // ESC closes, then K toggles the module in game
        ctx.getInput().pressKey(InputConstants.KEY_ESCAPE);
        ctx.waitTicks(5);
        check(ctx.computeOnClient(c -> c.gui.screen() == null), "second ESC closes the menu");
        ctx.getInput().pressKey(InputConstants.KEY_K);
        ctx.waitTicks(5);
        check(ctx.computeOnClient(c -> mod(AutoSprint.class).isEnabled()), "K toggles AutoSprint on in game");
        ctx.runOnClient(client -> on(Notifications.class));
        ctx.getInput().pressKey(InputConstants.KEY_K);
        ctx.waitTicks(5);
        check(ctx.computeOnClient(c -> !mod(AutoSprint.class).isEnabled()), "K toggles AutoSprint off again");
        ctx.takeScreenshot("11_keybind_toast");

        // reopen, middle-click the bind row clears it
        ctx.getInput().pressKey(InputConstants.KEY_RSHIFT);
        ctx.waitForScreen(ClickGuiScreen.class);
        ctx.waitTicks(3);
        click(ctx, reveal(ctx, MOVEMENT, AutoSprint.class, "bind"), 2);
        check(ctx.computeOnClient(c -> mod(AutoSprint.class).getKeyBind()) == -1, "middle-click clears the keybind");
        click(ctx, reveal(ctx, MOVEMENT, AutoSprint.class, null), 1);

        // Edit HUD button -> editor; drag HudInfo; ESC back to the menu
        ctx.runOnClient(client -> { on(HudInfo.class); mod(HudInfo.class).resetPosition(); });
        ctx.waitTicks(3);
        click(ctx, new int[]{164 + 28, 15}, 0);
        ctx.waitForScreen(HudEditorScreen.class);
        ctx.waitTicks(3);
        check(true, "Edit HUD button opens the editor");

        int[] from = ctx.computeOnClient(c -> {
            HudInfo h = mod(HudInfo.class);
            return new int[]{h.hudX() + 5, h.hudY() + 5};
        });
        cursor(ctx, from[0], from[1]);
        ctx.waitTicks(2);
        ctx.getInput().holdMouse(0);
        ctx.waitTicks(2);
        for (int i = 1; i <= 6; i++) {
            cursor(ctx, from[0] + i * 20, from[1] + i * 15);
            ctx.waitTicks(1);
        }
        ctx.getInput().releaseMouse(0);
        ctx.waitTicks(3);
        int[] to = ctx.computeOnClient(c -> new int[]{mod(HudInfo.class).hudX(), mod(HudInfo.class).hudY()});
        check(to[0] > from[0] + 60 && to[1] > from[1] + 40 && mod(HudInfo.class).isMoved(),
            "dragging HudInfo moves it (" + from[0] + "," + from[1] + " -> " + to[0] + "," + to[1] + ")");
        ctx.takeScreenshot("12_editor_dragged");

        ctx.getInput().pressKey(InputConstants.KEY_ESCAPE);
        ctx.waitForScreen(ClickGuiScreen.class);
        check(true, "ESC in the editor goes back to the menu");
        ctx.getInput().pressKey(InputConstants.KEY_ESCAPE);
        ctx.waitTicks(5);
        check(ctx.computeOnClient(c -> c.gui.screen() == null), "ESC closes the menu");
    }

    @Override
    public void runTest(ClientGameTestContext context) {
        try (TestSingleplayerContext sp = context.worldBuilder().create()) {
            sp.getServer().runCommand("/fill -4 199 -6 14 199 6 minecraft:stone");
            sp.getServer().runCommand("/tp @a 0 200 0 -90 0");
            sp.getServer().runCommand("/fill 3 200 -3 5 204 3 minecraft:stone");
            sp.getServer().runCommand("/setblock 4 202 -2 minecraft:diamond_ore");
            sp.getServer().runCommand("/setblock 4 202 0 minecraft:gold_ore");
            sp.getServer().runCommand("/setblock 4 202 2 minecraft:redstone_ore");
            sp.getServer().runCommand("/setblock 4 201 -2 minecraft:copper_ore");
            sp.getServer().runCommand("/setblock 4 201 2 minecraft:iron_ore");
            sp.getServer().runCommand("/setblock 4 200 -2 minecraft:lapis_ore");
            sp.getServer().runCommand("/setblock 4 200 0 minecraft:deepslate_emerald_ore");
            sp.getServer().runCommand("/setblock 4 200 2 minecraft:coal_ore");
            sp.getServer().runCommand("/summon zombie 8 200 0 {NoAI:1b,PersistenceRequired:1b}");
            sp.getConnection().waitForChunksRender();
            context.runOnClient(client -> allOff());
            context.waitTicks(20);

            // --- Xray: defaults (copper and coal are off by default -> 6 of the 8 ores) ---
            context.runOnClient(client -> on(Xray.class));
            context.waitTicks(60);
            context.takeScreenshot("01_xray_defaults");

            // --- Xray: switch gold and redstone off -> should vanish immediately ---
            context.runOnClient(client -> {
                ((Setting<Boolean>) setting(Xray.class, "gold")).set(false);
                ((Setting<Boolean>) setting(Xray.class, "redstone")).set(false);
            });
            context.waitTicks(5);
            context.takeScreenshot("02_xray_gold_redstone_off");

            // --- Menu with Xray's ore toggles visible ---
            context.runOnClient(client -> {
                ClickGuiScreen.setExpanded(ModuleManager.get().get(Xray.class), true);
                client.gui.setScreen(new ClickGuiScreen());
            });
            context.waitTicks(5);
            context.runOnClient(client -> {
                if (client.gui.screen() instanceof ClickGuiScreen s) s.mouseScrolled(60, 90, 0, -4);
            });
            context.waitTicks(10);
            context.takeScreenshot("03_menu_xray_toggles");

            // --- HUD elements at their default positions ---
            sp.getServer().runCommand("/item replace entity @a armor.chest with minecraft:diamond_chestplate");
            sp.getServer().runCommand("/item replace entity @a armor.head with minecraft:iron_helmet[damage=150]");
            sp.getServer().runCommand("/give @a minecraft:totem_of_undying 3");
            sp.getServer().runCommand("/give @a minecraft:golden_apple 12");
            sp.getServer().runCommand("/effect give @a minecraft:speed 120 1");
            context.runOnClient(client -> {
                client.gui.setScreen(null);
                ClickGuiScreen.setExpanded(ModuleManager.get().get(Xray.class), false);
                allOff();
                on(Esp.class);
                on(HudInfo.class);
                on(ArmorHud.class);
                on(PotionHud.class);
                on(KeyStrokes.class);
                on(ActiveModules.class);
                on(TotemCounter.class);
                on(Notifications.class);
                Toasts.push("Esp ON", Theme.ACCENT);
            });
            context.waitTicks(20);
            context.takeScreenshot("04_hud_default");

            // --- Move two HUD elements: HudInfo to the middle, ActiveModules bottom-left ---
            context.runOnClient(client -> {
                int w = client.getWindow().getGuiScaledWidth(), h = client.getWindow().getGuiScaledHeight();
                var info = ModuleManager.get().get(HudInfo.class);
                info.moveTo(w / 2 - info.hudWidth() / 2, h / 2 - 40);
                var list = ModuleManager.get().get(ActiveModules.class);
                list.moveTo(4, h - list.hudHeight() - 60);
            });
            context.waitTicks(10);
            context.takeScreenshot("05_hud_moved");

            // --- HUD editor open on top ---
            context.runOnClient(client -> client.gui.setScreen(new HudEditorScreen(new ClickGuiScreen())));
            context.waitTicks(10);
            context.takeScreenshot("06_hud_editor");

            // --- Storage ESP: chest, ender chest, shulker, and a chest minecart ---
            sp.getServer().runCommand("/setblock 7 200 0 minecraft:chest");
            sp.getServer().runCommand("/setblock 7 201 0 minecraft:ender_chest");
            sp.getServer().runCommand("/setblock 7 202 0 minecraft:purple_shulker_box");
            sp.getServer().runCommand("/summon chest_minecart 7 200 -2");
            context.runOnClient(client -> {
                client.gui.setScreen(null);
                allOff();
                on(StorageEsp.class);
            });
            context.waitTicks(40);
            context.takeScreenshot("07_storage_esp");

            menuInteraction(context);

            // --- Autosave: flip something, wait past the interval, read novautils.json back ---
            context.runOnClient(client -> on(Notifier.class));
            context.waitTicks(230);
            Path configFile = FabricLoader.getInstance().getConfigDir().resolve("novautils.json");
            try {
                String json = Files.readString(configFile);
                int at = json.indexOf("\"Notifier\"");
                if (at < 0 || !json.substring(at, Math.min(json.length(), at + 120)).contains("\"enabled\": true")) {
                    throw new AssertionError("autosave did not write Notifier as enabled");
                }
                System.out.println("[novautils-test] autosave verified");
            } catch (java.io.IOException e) {
                throw new AssertionError("autosave never created " + configFile, e);
            }

            // --- Config migration: an old config where Xray "diamond" was a colour int ---
            context.runOnClient(client -> {
                try {
                    Files.writeString(configFile, """
                        { "_configVersion": 2,
                          "Xray": { "enabled": true, "keyBind": -1,
                                    "settings": { "diamond": 1946874080, "radius": 20 } } }
                        """);
                } catch (java.io.IOException e) {
                    throw new AssertionError(e);
                }
                ((Setting<Boolean>) setting(Xray.class, "diamond")).set(true);
                Config.get().load(ModuleManager.get().getModules());
                boolean diamond = ((Setting<Boolean>) setting(Xray.class, "diamond")).get();
                int radius = ((Setting<Integer>) setting(Xray.class, "radius")).get();
                if (!diamond) throw new AssertionError("old int 'diamond' value switched the diamond toggle off");
                if (radius != 20) throw new AssertionError("valid old value (radius=20) was not loaded, got " + radius);
                System.out.println("[novautils-test] config migration verified");
            });
        }
    }
}
