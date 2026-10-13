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

    /**
     * Screenshot. With Sodium the framework's own screenshot (which re-renders the frame) crashes
     * Sodium, so press F2 instead - vanilla saves the frame that was just drawn to run/screenshots.
     */
    private static void shot(ClientGameTestContext ctx, String name) {
        if (FabricLoader.getInstance().isModLoaded("sodium")) {
            System.out.println("[novautils-test] screenshot (F2): " + name);
            ctx.getInput().pressKey(InputConstants.KEY_F2);
            ctx.waitTicks(2);
        } else {
            ctx.takeScreenshot(name);
        }
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

    private static void clickCategory(ClientGameTestContext ctx, dev.novautils.modules.Category cat) {
        click(ctx, ctx.computeOnClient(c -> ((ClickGuiScreen) dev.novautils.utils.PlatformCompat.currentScreen(c)).categoryCenter(cat)), 0);
    }

    /** Scrolls the panel under {@code panelAt} with the mouse wheel until the row is fully shown. */
    private static int[] reveal(ClientGameTestContext ctx, int[] panelAt, Class<? extends Module> type, String setting) {
        for (int i = 0; i < 60; i++) {
            int[] r = row(ctx, type, setting);
            if (r != null) return r;
            int[] at = ctx.computeOnClient(c -> dev.novautils.utils.PlatformCompat.currentScreen(c) instanceof ClickGuiScreen s
                ? (setting == null ? s.listCenter() : s.settingsCenter()) : new int[]{0, 0});
            cursor(ctx, at[0], at[1]);
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
        clickCategory(ctx, dev.novautils.modules.Category.RENDER);
        check(reveal(ctx, RENDER, Fullbright.class, null) != null, "clicking Render in the sidebar lists its modules");
        shot(ctx, "08a_menu_opened");

        // left-click toggles a module on and off
        click(ctx, row(ctx, Fullbright.class, null), 0);
        check(ctx.computeOnClient(c -> mod(Fullbright.class).isEnabled()), "left-click enables Fullbright");
        click(ctx, row(ctx, Fullbright.class, null), 0);
        check(ctx.computeOnClient(c -> !mod(Fullbright.class).isEnabled()), "second left-click disables Fullbright");

        // right-click expands; a boolean setting flips on click
        click(ctx, reveal(ctx, RENDER, Xray.class, null), 1);
        check(reveal(ctx, RENDER, Xray.class, "diamond") != null, "right-click shows Xray settings");
        click(ctx, row(ctx, Xray.class, "diamond"), 0);
        check(ctx.computeOnClient(c -> !((Setting<Boolean>) setting(Xray.class, "diamond")).get()), "click turns diamond off");
        shot(ctx, "08_menu_clicked_settings");
        click(ctx, row(ctx, Xray.class, "diamond"), 0);
        check(ctx.computeOnClient(c -> ((Setting<Boolean>) setting(Xray.class, "diamond")).get()), "click turns diamond back on");

        // slider: click the far left of the radius track -> minimum; middle-click -> default
        int[] radius = reveal(ctx, RENDER, Xray.class, "radius");
        check(radius != null, "radius slider visible");
        click(ctx, ctx.computeOnClient(c -> ((ClickGuiScreen) dev.novautils.utils.PlatformCompat.currentScreen(c)).sliderPoint(mod(Xray.class), "radius", 0f)), 0);
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
        shot(ctx, "09_menu_search");
        ctx.getInput().pressKey(InputConstants.KEY_ESCAPE);
        ctx.waitTicks(3);
        check(ctx.computeOnClient(c -> c.gui.screen() instanceof ClickGuiScreen s && s.searchText().isEmpty()),
            "first ESC clears search, menu stays open");

        // bind AutoSprint to K through the bind row
        clickCategory(ctx, dev.novautils.modules.Category.MOVEMENT);
        click(ctx, row(ctx, AutoSprint.class, null), 1);
        click(ctx, reveal(ctx, MOVEMENT, AutoSprint.class, "bind"), 0);
        ctx.getInput().pressKey(InputConstants.KEY_K);
        ctx.waitTicks(3);
        check(ctx.computeOnClient(c -> mod(AutoSprint.class).getKeyBind()) == InputConstants.KEY_K, "bind row captures K");
        shot(ctx, "10_menu_keybind");

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
        shot(ctx, "11_keybind_toast");

        // reopen, middle-click the bind row clears it
        ctx.getInput().pressKey(InputConstants.KEY_RSHIFT);
        ctx.waitForScreen(ClickGuiScreen.class);
        ctx.waitTicks(3);
        click(ctx, reveal(ctx, MOVEMENT, AutoSprint.class, "bind"), 2);
        check(ctx.computeOnClient(c -> mod(AutoSprint.class).getKeyBind()) == -1, "middle-click clears the keybind");

        // mouse button binds: side button (mouse 4) through the bind row, then use it in game
        click(ctx, reveal(ctx, MOVEMENT, AutoSprint.class, "bind"), 0);
        ctx.getInput().pressMouse(3);
        ctx.waitTicks(3);
        check(ctx.computeOnClient(c -> mod(AutoSprint.class).getKeyBind()) == dev.novautils.utils.Keybinds.mouse(3),
            "bind row takes a mouse side button (" + ctx.computeOnClient(c -> dev.novautils.utils.Keybinds.name(mod(AutoSprint.class).getKeyBind())) + ")");
        ctx.getInput().pressKey(InputConstants.KEY_ESCAPE);
        ctx.waitTicks(5);
        ctx.getInput().pressMouse(3);
        ctx.waitTicks(5);
        check(ctx.computeOnClient(c -> mod(AutoSprint.class).isEnabled()), "mouse 4 toggles AutoSprint on in game");
        ctx.getInput().pressMouse(3);
        ctx.waitTicks(5);
        check(ctx.computeOnClient(c -> !mod(AutoSprint.class).isEnabled()), "mouse 4 toggles it off again");
        ctx.runOnClient(c -> mod(AutoSprint.class).setKeyBind(-1));
        ctx.getInput().pressKey(InputConstants.KEY_RSHIFT);
        ctx.waitForScreen(ClickGuiScreen.class);
        ctx.waitTicks(3);
        click(ctx, reveal(ctx, MOVEMENT, AutoSprint.class, null), 1);

        // Edit HUD button -> editor; drag HudInfo; ESC back to the menu
        ctx.runOnClient(client -> { on(HudInfo.class); mod(HudInfo.class).resetPosition(); });
        ctx.waitTicks(3);
        click(ctx, ctx.computeOnClient(c -> ((ClickGuiScreen) dev.novautils.utils.PlatformCompat.currentScreen(c)).hudButtonCenter()), 0);
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
        shot(ctx, "12_editor_dragged");

        ctx.getInput().pressKey(InputConstants.KEY_ESCAPE);
        ctx.waitForScreen(ClickGuiScreen.class);
        check(true, "ESC in the editor goes back to the menu");
        ctx.getInput().pressKey(InputConstants.KEY_ESCAPE);
        ctx.waitTicks(5);
        check(ctx.computeOnClient(c -> c.gui.screen() == null), "ESC closes the menu");
    }

    private static void cmd(TestSingleplayerContext sp, String command) {
        sp.getServer().runCommand(command);
    }

    private static double[] pos(ClientGameTestContext ctx) {
        return ctx.computeOnClient(c -> new double[]{c.player.getX(), c.player.getY(), c.player.getZ()});
    }

    private static void walk(ClientGameTestContext ctx, int ticks) {
        ctx.getInput().holdKey(o -> o.keyUp);
        ctx.waitTicks(ticks);
        ctx.getInput().releaseKey(o -> o.keyUp);
        ctx.waitTicks(3);
    }

    /** Waits for full charge, hits the tagged husk once, returns the damage it took. */
    private static float hitOnce(ClientGameTestContext ctx) {
        ctx.waitTicks(30);
        return ctx.computeOnClient(c -> {
            for (var e : c.level.entitiesForRendering()) {
                if (e instanceof net.minecraft.world.entity.LivingEntity l && e instanceof net.minecraft.world.entity.monster.zombie.Husk) {
                    float before = l.getHealth();
                    c.gameMode.attack(c.player, e);
                    return before;
                }
            }
            throw new AssertionError("no husk");
        }) - huskHealthLater(ctx);
    }

    private static float huskHealthLater(ClientGameTestContext ctx) {
        ctx.waitTicks(15);
        return ctx.computeOnClient(c -> {
            for (var e : c.level.entitiesForRendering()) {
                if (e instanceof net.minecraft.world.entity.monster.zombie.Husk h) return h.getHealth();
            }
            return 0f;
        });
    }

    private static void only(ClientGameTestContext ctx, Class<? extends Module> type) {
        ctx.runOnClient(c -> {
            allOff();
            on(type);
        });
        ctx.waitTicks(2);
    }

    /**
     * Checks that the reworked modules actually do their job in a real (singleplayer) world, in
     * survival: movement through the physics hook, packet edits, silent rotations, block placing
     * and breaking, eating, fighting, plus screenshots of the new world labels.
     */
    private static void featureChecks(ClientGameTestContext ctx, TestSingleplayerContext sp) {
        ctx.runOnClient(c -> {
            c.gui.setScreen(null);
            allOff();
        });
        cmd(sp, "/difficulty easy");
        cmd(sp, "/gamemode survival @a");
        cmd(sp, "/effect clear @a");
        cmd(sp, "/clear @a");
        cmd(sp, "/effect give @a minecraft:instant_health 1 10");
        ctx.waitTicks(10);

        // Speed: 15 ticks of W, walking covers ~3.2 blocks, Speed(0.36) ~5.4
        cmd(sp, "/tp @a 0 200 5 -90 0");
        ctx.waitTicks(5);
        only(ctx, dev.novautils.modules.movement.Speed.class);
        double x0 = pos(ctx)[0];
        walk(ctx, 15);
        double moved = pos(ctx)[0] - x0;
        check(moved > 4.5, "Speed moves faster than walking (" + String.format("%.2f", moved) + " blocks in 15 ticks)");

        // SafeWalk: walk west off the platform edge (x=-4) - must stay on it
        cmd(sp, "/tp @a -2 200 5 90 0");
        ctx.waitTicks(5);
        only(ctx, dev.novautils.modules.movement.SafeWalk.class);
        walk(ctx, 40);
        double[] p = pos(ctx);
        check(p[1] > 199.5 && p[0] > -4.5, "SafeWalk stops at the edge (x=" + String.format("%.2f", p[0]) + ", y=" + String.format("%.2f", p[1]) + ")");

        // NoFall: 25-block drop in survival would nearly kill; must take no damage
        cmd(sp, "/tp @a 0 225 5 -90 0");
        ctx.runOnClient(c -> { allOff(); on(dev.novautils.modules.movement.NoFall.class); });
        ctx.waitTicks(60);
        float hp = ctx.computeOnClient(c -> c.player.getHealth());
        check(hp >= 19.5f && pos(ctx)[1] < 200.5, "NoFall: 25-block fall, health still " + hp);

        // Scaffold: walk east off the platform (ends at x=14) with stone in the hotbar
        cmd(sp, "/give @a minecraft:stone 64");
        cmd(sp, "/tp @a 12 200 5 -90 0");
        ctx.waitTicks(5);
        only(ctx, dev.novautils.modules.movement.Scaffold.class);
        walk(ctx, 50);
        p = pos(ctx);
        boolean bridged = ctx.computeOnClient(c -> !c.level.getBlockState(new net.minecraft.core.BlockPos(15, 199, 5)).isAir());
        check(bridged && p[1] > 199.5 && p[0] > 15, "Scaffold builds a bridge over the void (x=" + String.format("%.1f", p[0]) + ", y=" + String.format("%.1f", p[1]) + ")");
        shot(ctx, "13_scaffold");

        // Jesus: walk from the platform onto a pool
        cmd(sp, "/fill -3 196 7 3 199 11 minecraft:stone");
        cmd(sp, "/fill -2 199 8 2 199 10 minecraft:water");
        cmd(sp, "/tp @a 0 200 6 0 0");
        ctx.waitTicks(10);
        only(ctx, dev.novautils.modules.movement.Jesus.class);
        walk(ctx, 12);
        p = pos(ctx);
        boolean wet = ctx.computeOnClient(c -> c.player.isInWater());
        check(p[2] > 8 && p[1] > 199.9 && !wet, "Jesus walks on water (z=" + String.format("%.1f", p[2]) + ", y=" + String.format("%.2f", p[1]) + ", inWater=" + wet + ")");
        shot(ctx, "14_jesus");

        // AutoEat: drain the food bar, then it must eat the bread by itself
        cmd(sp, "/tp @a -2 200 5 90 0");
        cmd(sp, "/effect give @a minecraft:hunger 30 255");
        ctx.runOnClient(c -> allOff());
        ctx.waitFor(c -> c.player.getFoodData().getFoodLevel() <= 10, 600);
        cmd(sp, "/effect clear @a minecraft:hunger");
        cmd(sp, "/give @a minecraft:bread 16");
        ctx.waitTicks(5);
        int before = ctx.computeOnClient(c -> c.player.getFoodData().getFoodLevel());
        only(ctx, dev.novautils.modules.player.AutoEat.class);
        ctx.waitTicks(80);
        int after = ctx.computeOnClient(c -> c.player.getFoodData().getFoodLevel());
        boolean released = ctx.computeOnClient(c -> !c.options.keyUse.isDown());
        check(after > before + 4 && released, "AutoEat ate bread (food " + before + " -> " + after + ") and let go of the use key");

        // AutoFight: zombie in the open next to us gets hit (with silent rotations)
        cmd(sp, "/tp @a 0 200 5 -90 0");
        cmd(sp, "/summon minecraft:zombie 2 200 5 {NoAI:1b,PersistenceRequired:1b,Tags:[\"fight\"]}");
        ctx.waitTicks(10);
        only(ctx, dev.novautils.modules.combat.AutoFight.class);
        ctx.waitTicks(60);
        float zombieHp = ctx.computeOnClient(c -> {
            for (var e : c.level.entitiesForRendering()) {
                if (e instanceof net.minecraft.world.entity.monster.zombie.Zombie z && e.distanceToSqr(2, 200, 5) < 4) return z.getHealth();
            }
            return 0f;
        });
        check(zombieHp < 18f, "AutoFight hits the zombie (health " + zombieHp + ")");
        cmd(sp, "/kill @e[tag=fight]");

        // AutoFight + neutral mobs: a calm piglin (we wear gold) is left alone, an angry one is fought
        cmd(sp, "/tp @a 0 200 5 -90 0");
        cmd(sp, "/item replace entity @a armor.head with minecraft:golden_helmet");
        cmd(sp, "/summon minecraft:piglin 2 200 5 {NoAI:1b,PersistenceRequired:1b,IsImmuneToZombification:1b,Tags:[\"calm\"]}");
        ctx.waitTicks(10);
        only(ctx, dev.novautils.modules.combat.AutoFight.class);
        ctx.waitTicks(60);
        float calmHp = ctx.computeOnClient(c -> {
            for (var e : c.level.entitiesForRendering()) if (e instanceof net.minecraft.world.entity.monster.piglin.Piglin pg) return pg.getHealth() / pg.getMaxHealth();
            return -1f;
        });
        check(calmHp == 1f, "AutoFight leaves a calm piglin alone (health " + calmHp + ")");
        cmd(sp, "/kill @e[tag=calm]");
        cmd(sp, "/item replace entity @a armor.head with minecraft:air");
        cmd(sp, "/effect give @a minecraft:resistance 30 4 true");
        cmd(sp, "/summon minecraft:piglin 3 200 5 {PersistenceRequired:1b,IsImmuneToZombification:1b,Tags:[\"angry\"],HandItems:[{id:\"minecraft:golden_sword\",count:1},{}]}");
        ctx.waitFor(c -> {
            for (var e : c.level.entitiesForRendering()) if (e instanceof net.minecraft.world.entity.monster.piglin.Piglin pg) return pg.getHealth() < pg.getMaxHealth();
            return false;
        }, 300);
        check(true, "AutoFight fights back against a piglin that's angry at us");
        cmd(sp, "/kill @e[tag=angry]");
        cmd(sp, "/effect clear @a");
        ctx.runOnClient(c -> allOff());

        // AutoFight: shulkers count as hostile, and their bullets get hit out of the air
        cmd(sp, "/tp @a 0 200 5 -90 0");
        cmd(sp, "/effect give @a minecraft:resistance 30 4 true");
        cmd(sp, "/summon minecraft:shulker 2 200 5 {NoAI:1b,PersistenceRequired:1b,Peek:100b,Tags:[\"shul\"]}");
        ctx.waitTicks(5);
        only(ctx, dev.novautils.modules.combat.AutoFight.class);
        ctx.waitFor(c -> {
            for (var e : c.level.entitiesForRendering()) if (e instanceof net.minecraft.world.entity.monster.Shulker sh) return sh.getHealth() < sh.getMaxHealth();
            return false;
        }, 200);
        check(true, "AutoFight attacks shulkers");
        cmd(sp, "/kill @e[tag=shul]");
        cmd(sp, "/summon minecraft:shulker_bullet 2.5 201 5 {NoGravity:1b,Tags:[\"bullet\"]}");
        ctx.waitTicks(3);
        ctx.waitFor(c -> {
            for (var e : c.level.entitiesForRendering()) if (e instanceof net.minecraft.world.entity.projectile.ShulkerBullet) return false;
            return true;
        }, 200);
        check(true, "AutoFight hits a shulker bullet out of the air");
        cmd(sp, "/kill @e[tag=bullet]");
        cmd(sp, "/effect clear @a");
        ctx.runOnClient(c -> allOff());

        // Criticals: one full-charge sword hit without, one with - the second must do ~1.5x
        cmd(sp, "/kill @e[type=minecraft:item]");
        cmd(sp, "/item replace entity @a weapon.mainhand with minecraft:iron_sword");
        cmd(sp, "/tp @a 0 200 5 -90 0");
        cmd(sp, "/summon minecraft:husk 2 200 5 {NoAI:1b,PersistenceRequired:1b,Tags:[\"crit\"]}");
        ctx.waitTicks(10);
        ctx.runOnClient(c -> {
            allOff();
            var inv = c.player.getInventory();
            for (int i = 0; i < 9; i++) if (inv.getItem(i).is(net.minecraft.world.item.Items.IRON_SWORD)) dev.novautils.utils.InvUtils.selectHotbar(i);
        });
        float normal = hitOnce(ctx);
        ctx.runOnClient(c -> on(dev.novautils.modules.combat.Criticals.class));
        float crit = hitOnce(ctx);
        check(crit > normal * 1.3f, "Criticals (MINI_JUMP): normal hit " + normal + " dmg, with Criticals " + crit + " dmg");
        cmd(sp, "/kill @e[tag=crit]");
        // PACKET mode on a fresh husk
        cmd(sp, "/summon minecraft:husk 2 200 5 {NoAI:1b,PersistenceRequired:1b,Tags:[\"crit\"]}");
        ctx.waitTicks(10);
        ctx.runOnClient(c -> ((Setting<Object>) (Setting) setting(dev.novautils.modules.combat.Criticals.class, "mode")).set(dev.novautils.modules.combat.Criticals.Mode.PACKET));
        float packetCrit = hitOnce(ctx);
        check(packetCrit > normal * 1.3f, "Criticals (PACKET): " + packetCrit + " dmg");
        cmd(sp, "/kill @e[tag=crit]");

        // Nuker (FLATTEN): two dirt blocks next to us, nothing under our feet level
        cmd(sp, "/tp @a -2 200 5 90 0");
        cmd(sp, "/setblock -3 200 3 minecraft:dirt");
        cmd(sp, "/setblock -3 201 3 minecraft:dirt");
        ctx.waitTicks(5);
        only(ctx, dev.novautils.modules.world.Nuker.class);
        ctx.waitFor(c -> c.level.getBlockState(new net.minecraft.core.BlockPos(-3, 200, 3)).isAir()
            && c.level.getBlockState(new net.minecraft.core.BlockPos(-3, 201, 3)).isAir(), 200);
        boolean floorKept = ctx.computeOnClient(c -> !c.level.getBlockState(new net.minecraft.core.BlockPos(-2, 199, 5)).isAir());
        check(floorKept, "Nuker cleared the dirt and left the floor alone");

        // TPS meter: singleplayer runs at ~20
        float tps = ctx.computeOnClient(c -> dev.novautils.utils.ServerStats.tps());
        check(tps > 15f && tps <= 20.01f, "TPS meter reads " + tps);

        // Search: a spawner and an end portal frame next to the platform
        cmd(sp, "/setblock -3 200 -5 minecraft:spawner");
        cmd(sp, "/setblock -3 200 -4 minecraft:end_portal_frame");
        only(ctx, dev.novautils.modules.render.Search.class);
        ctx.waitFor(c -> ModuleManager.get().get(dev.novautils.modules.render.Search.class).count() >= 2, 200);
        check(true, "Search found the spawner and the end portal frame");

        // AutoMend: damaged Mending chestplate + XP bottles in the hotbar
        cmd(sp, "/clear @a");
        cmd(sp, "/tp @a -2 200 -2 90 0");
        cmd(sp, "/item replace entity @a armor.chest with minecraft:iron_chestplate[damage=200,enchantments={\"minecraft:mending\":1}]");
        cmd(sp, "/give @a minecraft:experience_bottle 64");
        ctx.waitTicks(5);
        int damageBefore = ctx.computeOnClient(c -> c.player.getItemBySlot(net.minecraft.world.entity.EquipmentSlot.CHEST).getDamageValue());
        only(ctx, dev.novautils.modules.player.AutoMend.class);
        ctx.waitTicks(120);
        int damageAfter = ctx.computeOnClient(c -> c.player.getItemBySlot(net.minecraft.world.entity.EquipmentSlot.CHEST).getDamageValue());
        float pitch = ctx.computeOnClient(c -> c.player.getXRot());
        check(damageAfter < damageBefore - 20 && Math.abs(pitch) < 1f,
            "AutoMend repaired the chestplate (damage " + damageBefore + " -> " + damageAfter + ") without moving the camera");

        // ElytraReplace: worn elytra with 4 uses left, a fresh one in the bag
        cmd(sp, "/clear @a");
        cmd(sp, "/item replace entity @a armor.chest with minecraft:elytra[damage=428]");
        cmd(sp, "/give @a minecraft:elytra");
        ctx.waitTicks(5);
        only(ctx, dev.novautils.modules.player.ElytraReplace.class);
        ctx.waitTicks(20);
        int wornDamage = ctx.computeOnClient(c -> c.player.getItemBySlot(net.minecraft.world.entity.EquipmentSlot.CHEST).getDamageValue());
        check(wornDamage == 0, "ElytraReplace swapped in the fresh elytra (worn damage now " + wornDamage + ")");

        // AutoFish: lure III rod facing the pool; must catch something by itself
        cmd(sp, "/clear @a");
        cmd(sp, "/fill -3 196 7 3 198 11 minecraft:water");
        cmd(sp, "/fill -2 199 8 2 199 10 minecraft:water");
        cmd(sp, "/give @a minecraft:fishing_rod[enchantments={\"minecraft:lure\":3}]");
        cmd(sp, "/tp @a 0 200 6 0 35");
        ctx.waitTicks(10);
        ctx.runOnClient(c -> {
            var inv = c.player.getInventory();
            for (int i = 0; i < 9; i++) if (inv.getItem(i).is(net.minecraft.world.item.Items.FISHING_ROD)) dev.novautils.utils.InvUtils.selectHotbar(i);
        });
        only(ctx, dev.novautils.modules.world.AutoFish.class);
        ctx.waitFor(c -> {
            var inv = c.player.getInventory();
            for (int i = 0; i < 36; i++) {
                var s = inv.getItem(i);
                if (!s.isEmpty() && !s.is(net.minecraft.world.item.Items.FISHING_ROD)) return true;
            }
            return false;
        }, 1200);
        check(true, "AutoFish caught something on its own");
        ctx.runOnClient(c -> allOff());

        // LightOverlay: torch suggestion must actually light what it promises
        cmd(sp, "/tp @a 8 200 -4 90 45");
        ctx.waitTicks(10);
        only(ctx, dev.novautils.modules.render.LightOverlay.class);
        ctx.waitTicks(5);
        int darkBefore = ctx.computeOnClient(c -> ModuleManager.get().get(dev.novautils.modules.render.LightOverlay.class).darkCount());
        var spot = ctx.computeOnClient(c -> {
            var s = ModuleManager.get().get(dev.novautils.modules.render.LightOverlay.class).torchSpots();
            return s.isEmpty() ? null : s.get(0);
        });
        check(spot != null && spot.lights() > 0, "LightOverlay suggests a torch spot (" + (spot == null ? "none" : spot.pos().toShortString() + " lights " + spot.lights()) + " of " + darkBefore + " dark tiles)");
        shot(ctx, "17_light_overlay_torch");
        cmd(sp, "/setblock " + spot.pos().getX() + " " + spot.pos().getY() + " " + spot.pos().getZ() + " minecraft:torch");
        ctx.waitTicks(50);
        int darkAfter = ctx.computeOnClient(c -> ModuleManager.get().get(dev.novautils.modules.render.LightOverlay.class).darkCount());
        int removed = darkBefore - darkAfter;
        check(removed >= spot.lights() * 0.9, "placing the torch there lit " + removed + " tiles (predicted " + spot.lights() + ")");
        ctx.runOnClient(c -> allOff());

        // LightOverlay: suggestions stay put when you take a few steps
        cmd(sp, "/tp @a 8 200 -4 90 45");
        ctx.runOnClient(c -> allOff());
        cmd(sp, "/fill -4 200 -6 14 200 6 minecraft:air replace minecraft:torch");
        ctx.waitTicks(10);
        only(ctx, dev.novautils.modules.render.LightOverlay.class);
        ctx.waitTicks(5);
        var firstSpot = ctx.computeOnClient(c -> ModuleManager.get().get(dev.novautils.modules.render.LightOverlay.class).torchSpots().get(0).pos());
        cmd(sp, "/tp @a 10 200 -2 90 45");
        ctx.waitTicks(45);
        cmd(sp, "/tp @a 7 200 -3 90 45");
        ctx.waitTicks(45);
        var laterSpot = ctx.computeOnClient(c -> ModuleManager.get().get(dev.novautils.modules.render.LightOverlay.class).torchSpots().get(0).pos());
        check(firstSpot.equals(laterSpot), "torch suggestion stays put while walking (" + firstSpot.toShortString() + " -> " + laterSpot.toShortString() + ")");
        ctx.runOnClient(c -> allOff());

        // Freecam: camera flies, body stays
        cmd(sp, "/tp @a 0 200 0 -90 0");
        ctx.waitTicks(10);
        double[] bodyBefore = pos(ctx);
        only(ctx, dev.novautils.modules.render.Freecam.class);
        ctx.waitTicks(5);
        walk(ctx, 30);
        double[] bodyAfter = pos(ctx);
        double camMoved = ctx.computeOnClient(c -> dev.novautils.utils.PlatformCompat.camera(c).position().distanceTo(new net.minecraft.world.phys.Vec3(bodyAfter[0], bodyAfter[1] + 1.62, bodyAfter[2])));
        double bodyMoved = Math.hypot(bodyAfter[0] - bodyBefore[0], bodyAfter[2] - bodyBefore[2]);
        check(bodyMoved < 0.1 && camMoved > 3, "Freecam: camera moved " + String.format("%.1f", camMoved) + " blocks, body " + String.format("%.2f", bodyMoved));
        // turn around (1200 x 0.15 = 180 degrees) to look back at the body we left
        ctx.runOnClient(c -> ModuleManager.get().get(dev.novautils.modules.render.Freecam.class).turn(1200, 60));
        ctx.waitTicks(3);
        shot(ctx, "18_freecam");
        ctx.runOnClient(c -> allOff());
        ctx.waitTicks(5);

        // InventoryMove: walking with the inventory open
        cmd(sp, "/tp @a 0 200 5 -90 0");
        ctx.waitTicks(5);
        only(ctx, dev.novautils.modules.movement.InventoryMove.class);
        ctx.runOnClient(c -> dev.novautils.utils.PlatformCompat.openScreen(c, new net.minecraft.client.gui.screens.inventory.InventoryScreen(c.player)));
        ctx.waitTicks(5);
        double invX = pos(ctx)[0];
        walk(ctx, 20);
        double invMoved = pos(ctx)[0] - invX;
        ctx.runOnClient(c -> dev.novautils.utils.PlatformCompat.openScreen(c, null));
        ctx.waitTicks(5);
        boolean upReleased = ctx.computeOnClient(c -> !c.options.keyUp.isDown());
        check(invMoved > 2 && upReleased, "InventoryMove: walked " + String.format("%.1f", invMoved) + " blocks with the inventory open, key released after");

        // Trajectories: predicted pearl landing vs where the pearl really takes us
        cmd(sp, "/clear @a");
        cmd(sp, "/give @a minecraft:ender_pearl 4");
        cmd(sp, "/tp @a 0 200 0 -90 30");
        ctx.waitTicks(10);
        only(ctx, dev.novautils.modules.render.Trajectories.class);
        ctx.runOnClient(c -> {
            var inv = c.player.getInventory();
            for (int i = 0; i < 9; i++) if (inv.getItem(i).is(net.minecraft.world.item.Items.ENDER_PEARL)) dev.novautils.utils.InvUtils.selectHotbar(i);
        });
        ctx.waitTicks(5);
        var predicted = ctx.computeOnClient(c -> ModuleManager.get().get(dev.novautils.modules.render.Trajectories.class).lastLanding());
        shot(ctx, "19_trajectories");
        ctx.runOnClient(c -> c.gameMode.useItem(c.player, net.minecraft.world.InteractionHand.MAIN_HAND));
        ctx.waitTicks(60);
        double[] landed = pos(ctx);
        double miss = predicted == null ? 99 : Math.hypot(landed[0] - predicted.x, landed[2] - predicted.z);
        check(miss < 1.0, "Trajectories: pearl landed " + String.format("%.2f", miss) + " blocks from the prediction " + (predicted == null ? "" : predicted));
        ctx.runOnClient(c -> allOff());

        // BetterChat: repeats collapse into one line with a counter, timestamps, long history
        ctx.runOnClient(c -> {
            on(dev.novautils.modules.misc.BetterChat.class);
            for (int i = 0; i < 3; i++) c.player.sendSystemMessage(net.minecraft.network.chat.Component.literal("spam test"));
        });
        ctx.waitTicks(3);
        String newest = ctx.computeOnClient(c -> dev.novautils.modules.misc.BetterChat.history().get(0).content().getString());
        long copies = ctx.computeOnClient(c -> dev.novautils.modules.misc.BetterChat.history().stream().filter(m -> m.content().getString().contains("spam test")).count());
        check(copies == 1 && newest.contains("(x3)") && newest.startsWith("["), "BetterChat: 3 repeats -> one line \"" + newest + "\"");
        ctx.runOnClient(c -> {
            for (int i = 0; i < 160; i++) c.player.sendSystemMessage(net.minecraft.network.chat.Component.literal("line " + i));
        });
        ctx.waitTicks(3);
        int kept = ctx.computeOnClient(c -> dev.novautils.modules.misc.BetterChat.history().size());
        check(kept > 150, "BetterChat keeps more than vanilla's 100 messages (" + kept + ")");

        // AirPlace: look up into empty air with stone in hand, right-click -> block appears there
        cmd(sp, "/clear @a");
        cmd(sp, "/give @a minecraft:stone 16");
        cmd(sp, "/tp @a -2 200 -5 0 -40");
        ctx.waitTicks(10);
        ctx.runOnClient(c -> {
            var inv = c.player.getInventory();
            for (int i = 0; i < 9; i++) if (inv.getItem(i).is(net.minecraft.world.item.Items.STONE)) dev.novautils.utils.InvUtils.selectHotbar(i);
        });
        only(ctx, dev.novautils.modules.world.AirPlace.class);
        ctx.waitTicks(3);
        var airTarget = ctx.computeOnClient(c -> ModuleManager.get().get(dev.novautils.modules.world.AirPlace.class).target());
        check(airTarget != null, "AirPlace finds a spot in the air (" + airTarget + ")");
        ctx.getInput().pressKey(o -> o.keyUse);
        ctx.waitTicks(10);
        boolean placed = ctx.computeOnClient(c -> c.level.getBlockState(airTarget).is(net.minecraft.world.level.block.Blocks.STONE));
        check(placed, "right-click placed stone in mid-air at " + airTarget.toShortString());
        ctx.runOnClient(c -> allOff());

        // ViewModel: bigger hand, moved right and tilted
        ctx.runOnClient(c -> {
            allOff();
            ((Setting<Double>) setting(dev.novautils.modules.render.ViewModel.class, "scale")).set(1.6);
            ((Setting<Double>) setting(dev.novautils.modules.render.ViewModel.class, "x")).set(0.3);
            ((Setting<Integer>) setting(dev.novautils.modules.render.ViewModel.class, "rotate-z")).set(25);
            on(dev.novautils.modules.render.ViewModel.class);
        });
        ctx.waitTicks(5);
        shot(ctx, "21_viewmodel");
        ctx.runOnClient(c -> allOff());

        // InventoryHUD: screenshot
        cmd(sp, "/give @a minecraft:diamond 23");
        cmd(sp, "/give @a minecraft:cobblestone 640");
        cmd(sp, "/give @a minecraft:oak_log 64");
        cmd(sp, "/give @a minecraft:golden_apple 5");
        ctx.runOnClient(c -> { allOff(); on(dev.novautils.modules.misc.InventoryHud.class); });
        ctx.waitTicks(10);
        shot(ctx, "20_inventory_hud");
        ctx.runOnClient(c -> allOff());

        // Zoom, Nametags and a waypoint label: screenshot only
        cmd(sp, "/tp @a 0 200 0 -90 0");
        cmd(sp, "/summon minecraft:zombie 7 200 1 {NoAI:1b,PersistenceRequired:1b}");
        cmd(sp, "/summon minecraft:villager 2 200 -5 {NoAI:1b,PersistenceRequired:1b}");
        cmd(sp, "/effect give @e[type=minecraft:zombie] minecraft:fire_resistance infinite 0 true");
        ctx.runOnClient(c -> {
            allOff();
            ((Setting<Boolean>) setting(dev.novautils.modules.render.Nametags.class, "mobs")).set(true);
            on(dev.novautils.modules.render.Nametags.class);
            on(dev.novautils.modules.render.Waypoints.class);
            ModuleManager.get().get(dev.novautils.modules.render.Waypoints.class).add("base", 60, 200, 30);
        });
        ctx.waitTicks(20);
        shot(ctx, "15_nametags_waypoint");
        ctx.runOnClient(c -> {
            ((Setting<Boolean>) setting(dev.novautils.modules.render.Zoom.class, "hold")).set(false);
            on(dev.novautils.modules.render.Zoom.class);
        });
        ctx.waitTicks(30);
        shot(ctx, "16_zoom");
        ctx.runOnClient(c -> {
            allOff();
            ModuleManager.get().get(dev.novautils.modules.render.Waypoints.class).remove("base");
        });
        cmd(sp, "/gamemode creative @a");
        ctx.waitTicks(20);
    }

    private static String blockAt(ClientGameTestContext ctx, int x, int y, int z) {
        return ctx.computeOnClient(c -> c.level.getBlockState(new net.minecraft.core.BlockPos(x, y, z)).toString());
    }

    private static int count(ClientGameTestContext ctx, net.minecraft.world.item.Item item) {
        return ctx.computeOnClient(c -> {
            int n = 0;
            var inv = c.player.getInventory();
            for (int i = 0; i < 36; i++) if (inv.getItem(i).is(item)) n += inv.getItem(i).getCount();
            return n;
        });
    }

    private static void useBlock(ClientGameTestContext ctx, int x, int y, int z) {
        ctx.runOnClient(c -> {
            var p = new net.minecraft.core.BlockPos(x, y, z);
            c.gameMode.useItemOn(c.player, net.minecraft.world.InteractionHand.MAIN_HAND,
                new net.minecraft.world.phys.BlockHitResult(net.minecraft.world.phys.Vec3.atCenterOf(p), net.minecraft.core.Direction.UP, p, false));
        });
    }

    /**
     * Commands, profiles, favourites, InventorySort, AutoDrop, AutoCompact, ChestTracker,
     * AutoTunnel, AutoBuild, FallPredict, ChunkBorders.
     */
    private static void toolChecks(ClientGameTestContext ctx, TestSingleplayerContext sp) {
        cmd(sp, "/gamemode survival @a");
        cmd(sp, "/clear @a");
        cmd(sp, "/kill @e[type=!minecraft:player]");
        cmd(sp, "/fill -6 199 -6 14 199 6 minecraft:stone");
        cmd(sp, "/fill -6 200 -6 14 206 6 minecraft:air");
        cmd(sp, "/tp @a 0 200 0 -90 0");
        ctx.runOnClient(c -> allOff());
        ctx.waitTicks(10);

        // --- chat commands and profiles
        ctx.runOnClient(c -> {
            dev.novautils.commands.Commands.handle(".t Fullbright");
            dev.novautils.commands.Commands.handle(".set Zoom zoom 8");
            dev.novautils.commands.Commands.handle(".bind AutoSprint n");
            dev.novautils.commands.Commands.handle(".wp add testhome");
        });
        boolean cmdsOk = ctx.computeOnClient(c -> mod(Fullbright.class).isEnabled()
            && ((Setting<Double>) setting(dev.novautils.modules.render.Zoom.class, "zoom")).get() == 8.0
            && mod(AutoSprint.class).getKeyBind() == InputConstants.KEY_N
            && ModuleManager.get().get(dev.novautils.modules.render.Waypoints.class).list().toString().contains("testhome"));
        check(cmdsOk, "chat commands toggle, set, bind and add waypoints");
        ctx.runOnClient(c -> {
            dev.novautils.commands.Commands.handle(".profile save nova_test");
            allOff();
            mod(AutoSprint.class).setKeyBind(-1);
            dev.novautils.commands.Commands.handle(".profile load nova_test");
        });
        check(ctx.computeOnClient(c -> mod(Fullbright.class).isEnabled() && mod(AutoSprint.class).getKeyBind() == InputConstants.KEY_N),
            "profile save/load restores modules and binds");
        ctx.runOnClient(c -> {
            dev.novautils.commands.Commands.handle(".config export");
            dev.novautils.commands.Commands.handle(".profile del nova_test");
            dev.novautils.commands.Commands.handle(".wp del testhome");
        });
        check(ctx.computeOnClient(c -> c.keyboardHandler.getClipboard().contains("_configVersion")), "config export copies the config");
        boolean sentToServer = ctx.computeOnClient(c -> {
            // the mixin must swallow the command: it never reaches chat as a message
            c.player.connection.sendChat(".t Fullbright");
            return !mod(Fullbright.class).isEnabled();
        });
        check(sentToServer, "typing a . command in chat runs it");
        ctx.runOnClient(c -> {
            allOff();
            mod(AutoSprint.class).setKeyBind(-1);
            ((Setting<Double>) setting(dev.novautils.modules.render.Zoom.class, "zoom")).reset();
        });

        // --- favourites survive a config round trip
        ctx.runOnClient(c -> {
            mod(Xray.class).setFavorite(true);
            ModuleManager.get().saveConfig();
            mod(Xray.class).setFavorite(false);
            Config.get().load(ModuleManager.get().getModules());
        });
        check(ctx.computeOnClient(c -> mod(Xray.class).isFavorite()), "favourite modules are saved");
        ctx.runOnClient(c -> {
            mod(Xray.class).setFavorite(false);
            allOff();
        });

        // --- InventorySort: scrambled main inventory comes out grouped and ordered
        cmd(sp, "/item replace entity @a container.9 with minecraft:stone 5");
        cmd(sp, "/item replace entity @a container.10 with minecraft:apple 3");
        cmd(sp, "/item replace entity @a container.11 with minecraft:stone 7");
        cmd(sp, "/item replace entity @a container.14 with minecraft:bone 2");
        cmd(sp, "/item replace entity @a container.20 with minecraft:apple 1");
        ctx.waitTicks(5);
        ctx.runOnClient(c -> on(dev.novautils.modules.player.InventorySort.class));
        ctx.waitTicks(10);
        String sorted = ctx.computeOnClient(c -> {
            StringBuilder b = new StringBuilder();
            for (int i = 9; i < 15; i++) b.append(net.minecraft.core.registries.BuiltInRegistries.ITEM.getKey(c.player.getInventory().getItem(i).getItem()).getPath()).append(',');
            return b.toString();
        });
        System.out.println("[novautils-test] InventorySort: " + sorted);
        check(sorted.startsWith("apple,apple,bone,stone,stone,air"), "InventorySort groups and orders the inventory");
        cmd(sp, "/clear @a");

        // --- AutoDrop throws dirt out of the main inventory
        cmd(sp, "/item replace entity @a container.12 with minecraft:dirt 16");
        ctx.runOnClient(c -> on(dev.novautils.modules.player.AutoDrop.class));
        ctx.waitTicks(10);
        check(count(ctx, net.minecraft.world.item.Items.DIRT) == 0, "AutoDrop throws junk away");
        ctx.runOnClient(c -> allOff());
        cmd(sp, "/kill @e[type=minecraft:item]");
        cmd(sp, "/clear @a");

        // --- AutoCompact: 18 iron ingots at a crafting table become 2 blocks
        cmd(sp, "/setblock 1 200 2 minecraft:crafting_table");
        cmd(sp, "/give @a minecraft:iron_ingot 18");
        ctx.waitTicks(5);
        ctx.runOnClient(c -> on(dev.novautils.modules.world.AutoCompact.class));
        useBlock(ctx, 1, 200, 2);
        ctx.waitTicks(30);
        int blocks = count(ctx, net.minecraft.world.item.Items.IRON_BLOCK);
        System.out.println("[novautils-test] AutoCompact: " + blocks + " iron blocks, " + count(ctx, net.minecraft.world.item.Items.IRON_INGOT) + " ingots left");
        check(blocks == 2, "AutoCompact crafts ingots into blocks");
        ctx.runOnClient(c -> {
            c.player.closeContainer();
            allOff();
        });
        cmd(sp, "/clear @a");
        cmd(sp, "/setblock 1 200 2 minecraft:air");

        // --- ChestTracker remembers a chest's contents, .find points at it
        cmd(sp, "/setblock -2 200 2 minecraft:chest{Items:[{Slot:0b,id:\"minecraft:diamond\",count:7}]}");
        ctx.waitTicks(5);
        ctx.runOnClient(c -> on(dev.novautils.modules.misc.ChestTracker.class));
        useBlock(ctx, -2, 200, 2);
        ctx.waitTicks(10);
        ctx.runOnClient(c -> c.player.closeContainer());
        ctx.waitTicks(3);
        String found = ctx.computeOnClient(c -> mod(dev.novautils.modules.misc.ChestTracker.class).find("diamond").toString());
        System.out.println("[novautils-test] ChestTracker: " + found);
        check(found.contains("7x at -2 200 2"), "ChestTracker finds the diamonds in the chest");
        cmd(sp, "/setblock -2 200 2 minecraft:air");

        // --- AutoTunnel digs a 1x2 tunnel east
        cmd(sp, "/fill 2 200 -1 9 201 1 minecraft:stone");
        cmd(sp, "/give @a minecraft:diamond_pickaxe");
        cmd(sp, "/tp @a 0 200 0 -90 0");
        ctx.waitTicks(10);
        ctx.runOnClient(c -> {
            ((Setting<Integer>) setting(dev.novautils.modules.world.AutoTunnel.class, "max-length")).set(6);
            ((Setting<Boolean>) setting(dev.novautils.modules.world.AutoTunnel.class, "torches")).set(false);
            on(dev.novautils.modules.world.AutoTunnel.class);
        });
        ctx.waitTicks(200);
        String tunnel = ctx.computeOnClient(c -> {
            StringBuilder b = new StringBuilder();
            for (int x = 2; x <= 5; x++) for (int y = 200; y <= 201; y++) b.append(c.level.getBlockState(new net.minecraft.core.BlockPos(x, y, 0)).isAir() ? 'o' : '#');
            return b + " x=" + String.format("%.1f", c.player.getX());
        });
        System.out.println("[novautils-test] AutoTunnel: " + tunnel);
        check(tunnel.startsWith("oooooooo") && ctx.computeOnClient(c -> c.player.getX() > 3), "AutoTunnel digs and walks");
        shot(ctx, "27_autotunnel");
        ctx.runOnClient(c -> allOff());
        cmd(sp, "/fill -6 200 -6 14 206 6 minecraft:air");
        cmd(sp, "/clear @a");
        cmd(sp, "/kill @e[type=minecraft:item]");

        // --- AutoBuild: a 3 wide, 2 tall wall two blocks ahead
        cmd(sp, "/tp @a 0 200 0 -90 0");
        cmd(sp, "/give @a minecraft:cobblestone 32");
        ctx.waitTicks(10);
        ctx.runOnClient(c -> {
            ((Setting<dev.novautils.modules.world.AutoBuild.Shape>) setting(dev.novautils.modules.world.AutoBuild.class, "shape")).set(dev.novautils.modules.world.AutoBuild.Shape.WALL);
            ((Setting<Integer>) setting(dev.novautils.modules.world.AutoBuild.class, "size")).set(3);
            ((Setting<Integer>) setting(dev.novautils.modules.world.AutoBuild.class, "height")).set(2);
            on(dev.novautils.modules.world.AutoBuild.class);
        });
        ctx.waitTicks(40);
        int wall = ctx.computeOnClient(c -> {
            int n = 0;
            for (int z = -1; z <= 1; z++) for (int y = 200; y <= 201; y++) if (c.level.getBlockState(new net.minecraft.core.BlockPos(2, y, z)).is(net.minecraft.world.level.block.Blocks.COBBLESTONE)) n++;
            return n;
        });
        System.out.println("[novautils-test] AutoBuild: " + wall + "/6 wall blocks");
        check(wall == 6, "AutoBuild builds the wall");
        shot(ctx, "28_autobuild");
        ctx.runOnClient(c -> allOff());
        cmd(sp, "/fill -6 200 -6 14 206 6 minecraft:air");
        cmd(sp, "/clear @a");

        // --- menu: the little switch toggles; switching to the classic menu and back
        cmd(sp, "/tp @a 0 200 0 -90 0");
        ctx.runOnClient(c -> {
            allOff();
            dev.novautils.utils.PlatformCompat.openScreen(c, new ClickGuiScreen());
        });
        ctx.waitTicks(5);
        clickCategory(ctx, dev.novautils.modules.Category.RENDER);
        int[] sw = null;
        for (int i = 0; i < 40 && sw == null; i++) {
            sw = ctx.computeOnClient(c -> dev.novautils.utils.PlatformCompat.currentScreen(c) instanceof ClickGuiScreen s ? s.switchCenter(mod(Fullbright.class)) : null);
            if (sw == null) reveal(ctx, null, Fullbright.class, null);
        }
        click(ctx, sw, 0);
        check(ctx.computeOnClient(c -> mod(Fullbright.class).isEnabled()), "clicking a module's switch turns it on");
        click(ctx, ctx.computeOnClient(c -> ((ClickGuiScreen) dev.novautils.utils.PlatformCompat.currentScreen(c)).styleButtonCenter()), 0);
        ctx.waitTicks(5);
        check(ctx.computeOnClient(c -> dev.novautils.utils.PlatformCompat.currentScreen(c) instanceof dev.novautils.gui.screens.ClassicGuiScreen
            && dev.novautils.gui.GuiState.get().isClassic()), "Classic menu button opens the classic menu");
        int[] classicRow = ctx.computeOnClient(c -> ((dev.novautils.gui.screens.ClassicGuiScreen) dev.novautils.utils.PlatformCompat.currentScreen(c)).rowCenter(mod(dev.novautils.modules.render.Zoom.class), null));
        click(ctx, classicRow, 0);
        check(ctx.computeOnClient(c -> mod(dev.novautils.modules.render.Zoom.class).isEnabled()), "left-click in the classic menu toggles a module");
        shot(ctx, "30_classic_menu");
        click(ctx, new int[]{252, 15}, 0);
        ctx.waitTicks(5);
        check(ctx.computeOnClient(c -> dev.novautils.utils.PlatformCompat.currentScreen(c) instanceof ClickGuiScreen
            && !dev.novautils.gui.GuiState.get().isClassic()), "Nova menu button goes back");
        ctx.runOnClient(c -> {
            dev.novautils.utils.PlatformCompat.openScreen(c, null);
            allOff();
        });

        // --- HealthBars over a mob (screenshot)
        cmd(sp, "/summon minecraft:zombie 4 200 0 {NoAI:1b,PersistenceRequired:1b,Health:12f}");
        cmd(sp, "/summon minecraft:pig 4 200 3 {NoAI:1b,PersistenceRequired:1b}");
        ctx.runOnClient(c -> on(dev.novautils.modules.render.HealthBars.class));
        ctx.waitTicks(20);
        shot(ctx, "31_health_bars");
        ctx.runOnClient(c -> allOff());
        cmd(sp, "/kill @e[type=!minecraft:player]");

        // --- every ESP style (screenshots)
        cmd(sp, "/summon minecraft:zombie 7 200 -2 {NoAI:1b,PersistenceRequired:1b,Health:9f}");
        cmd(sp, "/summon minecraft:skeleton 8 200 1 {NoAI:1b,PersistenceRequired:1b}");
        cmd(sp, "/summon minecraft:cow 5 200 3 {NoAI:1b,PersistenceRequired:1b}");
        cmd(sp, "/tp @a 0 200 0 -90 5");
        for (Esp.Mode espMode : Esp.Mode.values()) {
            ctx.runOnClient(c -> {
                ((Setting<Esp.Mode>) setting(Esp.class, "mode")).set(espMode);
                ((Setting<Boolean>) setting(Esp.class, "passive-mobs")).set(true);
                ((Setting<Boolean>) setting(Esp.class, "name-distance")).set(true);
                on(Esp.class);
            });
            ctx.waitTicks(10);
            shot(ctx, "32_esp_" + espMode.name().toLowerCase());
        }
        // chams through a wall: solid, then textured + rainbow
        cmd(sp, "/fill 3 200 -4 3 203 4 minecraft:stone");
        ctx.runOnClient(c -> ((Setting<Esp.Mode>) setting(Esp.class, "mode")).set(Esp.Mode.CHAMS));
        ctx.waitTicks(10);
        shot(ctx, "33_chams_wall_solid");
        ctx.runOnClient(c -> {
            ((Setting<Esp.ChamsStyle>) setting(Esp.class, "chams-style")).set(Esp.ChamsStyle.TEXTURED);
            ((Setting<Boolean>) setting(Esp.class, "rainbow")).set(true);
        });
        ctx.waitTicks(10);
        shot(ctx, "34_chams_wall_textured_rainbow");
        cmd(sp, "/fill 3 200 -4 3 203 4 minecraft:air");
        ctx.runOnClient(c -> {
            for (Setting<?> s : mod(Esp.class).getSettings()) s.reset();
            allOff();
        });
        cmd(sp, "/kill @e[type=!minecraft:player]");

        // --- AutoCommand fires on a timer and stops after N runs
        ctx.runOnClient(c -> {
            allOff();
            var ac = mod(dev.novautils.modules.misc.AutoCommand.class);
            ac.setCommand("/time set day");
            ((Setting<Double>) setting(dev.novautils.modules.misc.AutoCommand.class, "delay-seconds")).set(1.0);
            ((Setting<Integer>) setting(dev.novautils.modules.misc.AutoCommand.class, "runs")).set(3);
            ((Setting<Boolean>) setting(dev.novautils.modules.misc.AutoCommand.class, "first-now")).set(true);
            on(dev.novautils.modules.misc.AutoCommand.class);
        });
        for (int i = 0; i < 120 && ctx.computeOnClient(c -> mod(dev.novautils.modules.misc.AutoCommand.class).isEnabled()); i++) ctx.waitTick();
        int ran = ctx.computeOnClient(c -> mod(dev.novautils.modules.misc.AutoCommand.class).ranCount());
        System.out.println("[novautils-test] AutoCommand ran " + ran + " times");
        check(ran == 3 && !ctx.computeOnClient(c -> mod(dev.novautils.modules.misc.AutoCommand.class).isEnabled()), "AutoCommand fires on a timer and stops after N runs");
        ctx.runOnClient(c -> allOff());

        // --- FallPredict sees the landing spot; ChunkBorders knows the singleplayer seed
        cmd(sp, "/tp @a 0 215 0 -90 60");
        ctx.runOnClient(c -> {
            on(dev.novautils.modules.render.FallPredict.class);
            on(dev.novautils.modules.render.ChunkBorders.class);
        });
        ctx.waitTicks(3);
        var landing = ctx.computeOnClient(c -> mod(dev.novautils.modules.render.FallPredict.class).landing());
        System.out.println("[novautils-test] FallPredict: " + landing);
        check(landing != null && Math.abs(landing.y - 200) < 0.6, "FallPredict finds the floor below");
        check(ctx.computeOnClient(c -> mod(dev.novautils.modules.render.ChunkBorders.class).seed() != null), "ChunkBorders knows the seed");
        shot(ctx, "29_fall_predict");
        ctx.waitTicks(40);
        ctx.runOnClient(c -> allOff());
        cmd(sp, "/gamemode creative @a");
        cmd(sp, "/tp @a 0 200 0 -90 0");
        ctx.waitTicks(10);
    }

    /** ShulkerPeek, Minimap, HitsToKill, BreakIndicator, AutoFarm, AutoSmelt, PacketMine, BoatFly. */
    private static void newFeatureChecks(ClientGameTestContext ctx, TestSingleplayerContext sp) {
        cmd(sp, "/gamemode survival @a");
        cmd(sp, "/clear @a");
        cmd(sp, "/fill -6 199 -6 14 199 6 minecraft:stone");
        cmd(sp, "/fill -6 200 -6 14 206 6 minecraft:air");
        cmd(sp, "/kill @e[type=!minecraft:player]");
        cmd(sp, "/tp @a 0 200 0 -90 30");
        ctx.runOnClient(c -> allOff());
        ctx.waitTicks(10);

        // ShulkerPeek: contents read from the item, then the grid drawn over the inventory
        cmd(sp, "/give @a minecraft:purple_shulker_box[container=[{slot:0,item:{id:\"minecraft:diamond\",count:5}},{slot:13,item:{id:\"minecraft:golden_apple\",count:3}}]]");
        ctx.waitTicks(5);
        int diamonds = ctx.computeOnClient(c -> {
            for (var s : dev.novautils.modules.render.ShulkerPeek.contents(c.player.getInventory().getItem(0))) {
                if (s.is(net.minecraft.world.item.Items.DIAMOND)) return s.getCount();
            }
            return 0;
        });
        check(diamonds == 5, "ShulkerPeek reads the box contents");
        ctx.runOnClient(c -> {
            on(dev.novautils.modules.render.ShulkerPeek.class);
            c.gui.setScreen(new net.minecraft.client.gui.screens.inventory.InventoryScreen(c.player));
        });
        ctx.waitTicks(5);
        int[] slot0 = ctx.computeOnClient(c -> {
            var s = (net.minecraft.client.gui.screens.inventory.AbstractContainerScreen<?>) c.gui.screen();
            try {
                var lf = net.minecraft.client.gui.screens.inventory.AbstractContainerScreen.class.getDeclaredField("leftPos");
                var tf = net.minecraft.client.gui.screens.inventory.AbstractContainerScreen.class.getDeclaredField("topPos");
                lf.setAccessible(true);
                tf.setAccessible(true);
                var sl = s.getMenu().getSlot(36);
                return new int[]{lf.getInt(s) + sl.x + 8, tf.getInt(s) + sl.y + 8};
            } catch (ReflectiveOperationException e) {
                throw new AssertionError(e);
            }
        });
        cursor(ctx, slot0[0], slot0[1]);
        ctx.waitTicks(5);
        shot(ctx, "24_shulker_peek");
        ctx.runOnClient(c -> c.gui.setScreen(null));
        cmd(sp, "/clear @a");

        // AutoFarm: two ripe wheat on farmland get harvested and replanted from the hotbar
        cmd(sp, "/fill -3 199 2 -2 199 2 minecraft:farmland");
        cmd(sp, "/fill -3 200 2 -2 200 2 minecraft:wheat[age=7]");
        cmd(sp, "/give @a minecraft:wheat_seeds 8");
        ctx.waitTicks(5);
        ctx.runOnClient(c -> on(dev.novautils.modules.world.AutoFarm.class));
        for (int i = 0; i < 80 && !blockAt(ctx, -3, 200, 2).contains("age=0"); i++) ctx.waitTick();
        String farmed = blockAt(ctx, -3, 200, 2);
        System.out.println("[novautils-test] AutoFarm: -3 200 2 is now " + farmed);
        check(farmed.contains("wheat") && farmed.contains("age=0"), "AutoFarm harvested and replanted wheat");
        ctx.runOnClient(c -> allOff());
        cmd(sp, "/clear @a");

        // PacketMine: one click on stone, look away, it still breaks
        cmd(sp, "/setblock 2 200 -2 minecraft:stone");
        cmd(sp, "/give @a minecraft:diamond_pickaxe");
        ctx.waitTicks(5);
        ctx.runOnClient(c -> {
            on(dev.novautils.modules.player.PacketMine.class);
            c.gameMode.startDestroyBlock(new net.minecraft.core.BlockPos(2, 200, -2), net.minecraft.core.Direction.UP);
            c.player.setXRot(-60);
        });
        ctx.waitTicks(4);
        shot(ctx, "25_packet_mine");
        ctx.waitTicks(40);
        check(blockAt(ctx, 2, 200, -2).contains("air"), "PacketMine broke the block while looking away");
        ctx.runOnClient(c -> allOff());
        cmd(sp, "/clear @a");

        // AutoSmelt: opening a furnace fills it with raw iron and coal
        cmd(sp, "/setblock 1 200 1 minecraft:furnace");
        cmd(sp, "/give @a minecraft:raw_iron 8");
        cmd(sp, "/give @a minecraft:coal 4");
        cmd(sp, "/give @a minecraft:oak_log 16");
        ctx.waitTicks(5);
        ctx.runOnClient(c -> {
            on(dev.novautils.modules.world.AutoSmelt.class);
            var fp = new net.minecraft.core.BlockPos(1, 200, 1);
            c.gameMode.useItemOn(c.player, net.minecraft.world.InteractionHand.MAIN_HAND,
                new net.minecraft.world.phys.BlockHitResult(net.minecraft.world.phys.Vec3.atCenterOf(fp), net.minecraft.core.Direction.UP, fp, false));
        });
        ctx.waitTicks(30);
        String[] furnace = ctx.computeOnClient(c -> c.player.containerMenu instanceof net.minecraft.world.inventory.AbstractFurnaceMenu m
            ? new String[]{m.getSlot(0).getItem().toString(), m.getSlot(1).getItem().toString()} : new String[]{"no furnace", ""});
        System.out.println("[novautils-test] AutoSmelt: input=" + furnace[0] + " fuel=" + furnace[1]);
        check(furnace[0].contains("raw_iron") && furnace[1].contains("coal"), "AutoSmelt filled input and fuel (logs left alone)");
        ctx.runOnClient(c -> { c.player.closeContainer(); allOff(); });
        cmd(sp, "/clear @a");
        cmd(sp, "/setblock 1 200 1 minecraft:air");

        // BoatFly: in a boat, hold jump -> it climbs
        cmd(sp, "/summon minecraft:oak_boat 0 200 -3");
        ctx.waitTicks(5);
        cmd(sp, "/ride @p mount @e[type=minecraft:oak_boat,limit=1,sort=nearest]");
        ctx.waitTicks(10);
        double boatY0 = ctx.computeOnClient(c -> c.player.getVehicle() == null ? -1 : c.player.getVehicle().getY());
        check(boatY0 > 0, "riding the test boat");
        ctx.runOnClient(c -> on(dev.novautils.modules.movement.BoatFly.class));
        ctx.getInput().holdKey(o -> o.keyJump);
        ctx.waitTicks(20);
        ctx.getInput().releaseKey(o -> o.keyJump);
        double boatY1 = ctx.computeOnClient(c -> c.player.getVehicle() == null ? -1 : c.player.getVehicle().getY());
        System.out.println("[novautils-test] BoatFly: boat y " + boatY0 + " -> " + boatY1);
        check(boatY1 > boatY0 + 2, "BoatFly lifts the boat");
        ctx.runOnClient(c -> allOff());
        cmd(sp, "/ride @p dismount");
        cmd(sp, "/kill @e[type=minecraft:oak_boat]");
        cmd(sp, "/tp @a 0 200 0 -90 10");

        // Minimap + HitsToKill: a husk in front, screenshot
        cmd(sp, "/summon minecraft:husk 3 200 0 {NoAI:1b,PersistenceRequired:1b}");
        cmd(sp, "/kill @e[type=minecraft:item]");
        cmd(sp, "/item replace entity @a weapon.mainhand with minecraft:iron_sword");
        ctx.waitTicks(10);
        ctx.runOnClient(c -> {
            on(dev.novautils.modules.render.Minimap.class);
            on(dev.novautils.modules.render.HitsToKill.class);
            on(dev.novautils.modules.render.BreakIndicator.class);
            c.player.setYRot(-90);
            c.player.setXRot(5);
        });
        ctx.waitTicks(40);
        shot(ctx, "26_minimap_hits");
        double swordDmg = ctx.computeOnClient(c -> ModuleManager.get().get(dev.novautils.modules.render.HitsToKill.class).damage());
        System.out.println("[novautils-test] HitsToKill: iron sword damage " + swordDmg);
        check(Math.abs(swordDmg - 6.0) < 0.01, "HitsToKill counts the sword damage (6)");
        ctx.runOnClient(c -> allOff());
        cmd(sp, "/kill @e[type=minecraft:husk]");
        cmd(sp, "/clear @a");
        cmd(sp, "/gamemode creative @a");
        ctx.waitTicks(10);
    }

    private static String screenName(ClientGameTestContext ctx) {
        return ctx.computeOnClient(c -> c.gui.screen() == null ? "none" : c.gui.screen().getClass().getName());
    }

    /**
     * Only when JEI is in testmods: with the modules a real player keeps on, open the inventory,
     * hover an item, press R for its recipes, click around inside the recipe screen, then leave
     * it with ESC. The recipe screen must open and must close again.
     */
    private static void jeiCheck(ClientGameTestContext ctx, TestSingleplayerContext sp) {
        if (!FabricLoader.getInstance().isModLoaded("jei")) return;
        cmd(sp, "/gamemode survival @a");
        cmd(sp, "/give @a minecraft:oak_planks 16");
        ctx.runOnClient(c -> {
            allOff();
            for (var t : java.util.List.of(dev.novautils.modules.render.Fullbright.class, Esp.class,
                dev.novautils.modules.render.Nametags.class, dev.novautils.modules.render.Trajectories.class,
                dev.novautils.modules.render.ViewModel.class, dev.novautils.modules.movement.NoFall.class,
                dev.novautils.modules.movement.NoSlow.class, dev.novautils.modules.combat.AutoFight.class,
                dev.novautils.modules.combat.Criticals.class, ActiveModules.class, Notifications.class)) on(t);
        });
        ctx.waitTicks(40);
        for (int i = 0; i < 5 && screenName(ctx).equals("none"); i++) {
            ctx.getInput().pressKey(InputConstants.KEY_E);
            ctx.waitTicks(10);
        }
        if (screenName(ctx).equals("none")) {
            System.out.println("[novautils-test] jei: E did not open the inventory, opening it directly");
            ctx.runOnClient(c -> c.gui.setScreen(new net.minecraft.client.gui.screens.inventory.InventoryScreen(c.player)));
            ctx.waitTicks(10);
        }
        System.out.println("[novautils-test] jei: after E -> " + screenName(ctx));
        int[] slot = ctx.computeOnClient(c -> {
            if (!(c.gui.screen() instanceof net.minecraft.client.gui.screens.inventory.AbstractContainerScreen<?> s)) return null;
            int left, top;
            try {
                var lf = net.minecraft.client.gui.screens.inventory.AbstractContainerScreen.class.getDeclaredField("leftPos");
                var tf = net.minecraft.client.gui.screens.inventory.AbstractContainerScreen.class.getDeclaredField("topPos");
                lf.setAccessible(true);
                tf.setAccessible(true);
                left = lf.getInt(s);
                top = tf.getInt(s);
            } catch (ReflectiveOperationException e) {
                throw new AssertionError(e);
            }
            for (var sl : s.getMenu().slots) {
                if (sl.getItem().is(net.minecraft.world.item.Items.OAK_PLANKS)) return new int[]{left + sl.x + 8, top + sl.y + 8};
            }
            return null;
        });
        check(slot != null, "jei: planks visible in the inventory");
        cursor(ctx, slot[0], slot[1]);
        ctx.waitTicks(3);
        ctx.getInput().pressKey(InputConstants.KEY_R);
        ctx.waitTicks(20);
        String recipes = screenName(ctx);
        System.out.println("[novautils-test] jei: after R -> " + recipes);
        shot(ctx, "22_jei_recipes");
        check(recipes.contains("jei"), "jei: R opened the recipe screen");
        // click around the recipe screen like a player browsing
        int[] mid = ctx.computeOnClient(c -> new int[]{c.getWindow().getGuiScaledWidth() / 2, c.getWindow().getGuiScaledHeight() / 2});
        for (int[] d : new int[][]{{0, 0}, {-40, -20}, {40, 20}, {0, -60}}) {
            click(ctx, new int[]{mid[0] + d[0], mid[1] + d[1]}, 0);
            ctx.waitTicks(5);
            System.out.println("[novautils-test] jei: after click " + d[0] + "," + d[1] + " -> " + screenName(ctx));
        }
        ctx.getInput().pressKey(InputConstants.KEY_BACKSPACE);
        ctx.waitTicks(5);
        System.out.println("[novautils-test] jei: after backspace -> " + screenName(ctx));
        // the modpack's options.txt had JEI's "close recipes" key unbound: reproduce that first
        ctx.runOnClient(c -> {
            for (var k : c.options.keyMappings) {
                if (k.getName().equals("key.jei.closeRecipeGui")) k.setKey(InputConstants.UNKNOWN);
            }
            net.minecraft.client.KeyMapping.resetMapping();
        });
        ctx.getInput().pressKey(InputConstants.KEY_ESCAPE);
        ctx.waitTicks(10);
        System.out.println("[novautils-test] jei: close key unbound, after ESC -> " + screenName(ctx));
        if (!screenName(ctx).contains("jei")) {
            ctx.getInput().pressKey(InputConstants.KEY_R);
            ctx.waitTicks(10);
        }
        ctx.runOnClient(c -> {
            for (var k : c.options.keyMappings) {
                if (k.getName().equals("key.jei.closeRecipeGui")) k.setKey(k.getDefaultKey());
            }
            net.minecraft.client.KeyMapping.resetMapping();
        });
        for (int i = 0; i < 4 && screenName(ctx).contains("jei"); i++) {
            ctx.getInput().pressKey(InputConstants.KEY_ESCAPE);
            ctx.waitTicks(10);
            System.out.println("[novautils-test] jei: after ESC -> " + screenName(ctx));
        }
        shot(ctx, "23_jei_closed");
        check(!screenName(ctx).contains("jei"), "jei: ESC leaves the recipe screen");
        ctx.runOnClient(c -> { c.gui.setScreen(null); allOff(); });
        ctx.waitTicks(5);
    }

    @Override
    public void runTest(ClientGameTestContext context) {
        // silent test runs: master volume 0 (only this throwaway test instance, not your game)
        context.runOnClient(c -> c.options.getSoundSourceOptionInstance(net.minecraft.sounds.SoundSource.MASTER).set(0.0));
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
            sp.getClientLevel().waitForChunksRender();
            context.runOnClient(client -> allOff());
            context.waitTicks(20);
            jeiCheck(context, sp);

            // --- Xray: defaults (copper and coal are off by default -> 6 of the 8 ores) ---
            context.runOnClient(client -> on(Xray.class));
            context.waitTicks(60);
            shot(context, "01_xray_defaults");

            // --- Xray: switch gold and redstone off -> should vanish immediately ---
            context.runOnClient(client -> {
                ((Setting<Boolean>) setting(Xray.class, "gold")).set(false);
                ((Setting<Boolean>) setting(Xray.class, "redstone")).set(false);
            });
            context.waitTicks(5);
            shot(context, "02_xray_gold_redstone_off");

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
            shot(context, "03_menu_xray_toggles");

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
            shot(context, "04_hud_default");

            // --- Move two HUD elements: HudInfo to the middle, ActiveModules bottom-left ---
            context.runOnClient(client -> {
                int w = client.getWindow().getGuiScaledWidth(), h = client.getWindow().getGuiScaledHeight();
                var info = ModuleManager.get().get(HudInfo.class);
                info.moveTo(w / 2 - info.hudWidth() / 2, h / 2 - 40);
                var list = ModuleManager.get().get(ActiveModules.class);
                list.moveTo(4, h - list.hudHeight() - 60);
            });
            context.waitTicks(10);
            shot(context, "05_hud_moved");

            // --- HUD editor open on top ---
            context.runOnClient(client -> client.gui.setScreen(new HudEditorScreen(new ClickGuiScreen())));
            context.waitTicks(10);
            shot(context, "06_hud_editor");

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
            shot(context, "07_storage_esp");

            menuInteraction(context);
            featureChecks(context, sp);
            newFeatureChecks(context, sp);
            toolChecks(context, sp);

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
