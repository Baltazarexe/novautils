package dev.novautils.test;

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
