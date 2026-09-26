package dev.novautils.test;

import dev.novautils.gui.screens.ClickGuiScreen;
import dev.novautils.modules.ModuleManager;
import dev.novautils.modules.render.Esp;
import dev.novautils.modules.render.Xray;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;

/** Dev-only: builds a scene with a zombie behind a stone wall and screenshots ESP off / ESP on / the menu. */
@SuppressWarnings("unchecked")
public class NovaClientGameTest implements FabricClientGameTest {
    @SuppressWarnings("unchecked")
    private static void setEspMode(Esp.Mode m) {
        for (dev.novautils.settings.Setting<?> s : ModuleManager.get().get(Esp.class).getSettings()) {
            if (s.name.equals("mode")) ((dev.novautils.settings.Setting<Esp.Mode>) s).set(m);
        }
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
            context.waitTicks(20);
            context.takeScreenshot("1_esp_off");

            context.runOnClient(client -> ModuleManager.get().get(Esp.class).setEnabled(true));
            context.waitTicks(10);
            context.takeScreenshot("2_chams");

            context.runOnClient(client -> setEspMode(Esp.Mode.BOX));
            context.waitTicks(10);
            context.takeScreenshot("2b_box");

            context.runOnClient(client -> {
                ModuleManager.get().get(Esp.class).setEnabled(false);
                ModuleManager.get().get(Xray.class).setEnabled(true);
            });
            context.waitTicks(60);
            context.takeScreenshot("3_xray_on");

            context.runOnClient(client -> client.gui.setScreen(new ClickGuiScreen()));
            context.waitTicks(10);
            context.takeScreenshot("4_menu");

            context.runOnClient(client -> {
                ModuleManager.get().get(Esp.class).setEnabled(true);
                ModuleManager.get().get(dev.novautils.modules.render.Xray.class).setEnabled(true);
                dev.novautils.modules.ModuleManager.get().get(dev.novautils.modules.movement.Speed.class).setEnabled(true);
                ClickGuiScreen.setExpanded(ModuleManager.get().get(Esp.class), false);
                ClickGuiScreen.setExpanded(ModuleManager.get().get(dev.novautils.modules.render.Xray.class), true);
                for (dev.novautils.settings.Setting<?> s : ModuleManager.get().get(dev.novautils.modules.render.Xray.class).getSettings()) {
                    if (s.name.equals("diamond")) ClickGuiScreen.setColorExpanded(s, true);
                }
                ClickGuiScreen screen = new ClickGuiScreen();
                client.gui.setScreen(screen);
            });
            context.waitTicks(5);
            context.runOnClient(client -> {
                if (client.gui.screen() instanceof ClickGuiScreen s) s.mouseScrolled(60, 90, 0, -4);
            });
            context.waitTicks(10);
            context.takeScreenshot("5_menu_active");

            sp.getServer().runCommand("/give @a minecraft:diamond_chestplate");
            sp.getServer().runCommand("/effect give @a minecraft:speed 120 1");
            sp.getServer().runCommand("/effect give @a minecraft:night_vision 60 0");
            context.runOnClient(client -> {
                client.gui.setScreen(null);
                ModuleManager.get().get(dev.novautils.modules.misc.ArmorHud.class).setEnabled(true);
                ModuleManager.get().get(dev.novautils.modules.misc.PotionHud.class).setEnabled(true);
                ModuleManager.get().get(dev.novautils.modules.misc.KeyStrokes.class).setEnabled(true);
                ModuleManager.get().get(dev.novautils.modules.misc.HudInfo.class).setEnabled(true);
            });
            context.waitTicks(30);
            context.takeScreenshot("6_huds");

            context.runOnClient(client -> {
                ModuleManager.get().get(dev.novautils.modules.misc.ActiveModules.class).setEnabled(true);
                ModuleManager.get().get(dev.novautils.modules.movement.Flight.class).setEnabled(true);
                ModuleManager.get().get(dev.novautils.modules.render.Fullbright.class).setEnabled(true);
            });
            context.waitTicks(20);
            context.takeScreenshot("6b_arraylist");

            context.runOnClient(client -> {
                for (dev.novautils.settings.Setting<?> s : ModuleManager.get().get(dev.novautils.modules.misc.ActiveModules.class).getSettings()) {
                    if (s.name.equals("show-disabled")) ((dev.novautils.settings.Setting<Boolean>) s).set(true);
                    if (s.name.equals("sort")) ((dev.novautils.settings.Setting<dev.novautils.modules.misc.ActiveModules.Sort>) s).set(dev.novautils.modules.misc.ActiveModules.Sort.CATEGORY);
                }
            });
            context.waitTicks(20);
            context.takeScreenshot("6c_arraylist_all");

            context.runOnClient(client -> {
                for (dev.novautils.settings.Setting<?> s : ModuleManager.get().get(dev.novautils.modules.render.Fullbright.class).getSettings()) {
                    if (s.name.equals("mode")) ((dev.novautils.settings.Setting<dev.novautils.modules.render.Fullbright.Mode>) s).set(dev.novautils.modules.render.Fullbright.Mode.LIGHTMAP);
                }
            });
            context.waitTicks(40);
            context.takeScreenshot("6d_fullbright_lightmap");

            context.runOnClient(client -> {
                for (dev.novautils.modules.Module m : ModuleManager.get().getModules()) m.setEnabled(false);
            });
            context.waitTicks(20);
            sp.getServer().runCommand("/summon chest_minecart 7 200 -2");
            sp.getServer().runCommand("/summon hopper_minecart 7 200 2");
            sp.getServer().runCommand("/setblock 7 200 0 minecraft:chest");
            context.runOnClient(client -> {
                ModuleManager.get().get(dev.novautils.modules.render.StorageEsp.class).setEnabled(true);
                ModuleManager.get().get(dev.novautils.modules.player.FastBreak.class).setEnabled(true);
            });
            context.waitTicks(40);
            context.takeScreenshot("7b_storage_esp");

            context.takeScreenshot("7_all_off");

            sp.getServer().runCommand("/item replace entity @a armor.chest with minecraft:diamond_chestplate");
            sp.getServer().runCommand("/item replace entity @a armor.head with minecraft:iron_helmet[damage=150]");
            context.runOnClient(client -> ModuleManager.get().get(dev.novautils.modules.misc.ArmorHud.class).setEnabled(true));
            context.waitTicks(20);
            context.takeScreenshot("8_only_armorhud");

            context.runOnClient(client -> {
                ModuleManager.get().get(dev.novautils.modules.misc.ArmorHud.class).setEnabled(false);
                ModuleManager.get().get(Esp.class).setEnabled(true);
            });
            context.waitTicks(20);
            context.takeScreenshot("9_only_esp");
        }
    }
}
