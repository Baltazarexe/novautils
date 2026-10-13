package dev.novautils;

import com.mojang.blaze3d.platform.InputConstants;
import dev.novautils.gui.screens.ClickGuiScreen;
import dev.novautils.modules.Module;
import dev.novautils.modules.ModuleManager;
import dev.novautils.settings.Setting;
import dev.novautils.utils.PlatformCompat;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.Difficulty;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.inventory.AbstractFurnaceMenu;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.LevelSettings;
import net.minecraft.world.level.WorldDataConfiguration;
import net.minecraft.world.level.levelgen.WorldOptions;
import net.minecraft.world.level.levelgen.presets.WorldPresets;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.function.BooleanSupplier;

/**
 * Dev-only in-game self test for the Forge 1.20.1 build (1.20.1 Forge has no client game-test
 * framework). Runs only with -Dnovautils.selftest (gradle runSelftest): makes a fresh flat world,
 * drives the modules like a player would, checks the results, takes screenshots, writes
 * selftest-result.txt and closes the game.
 */
public final class SelfTest {
    public static final boolean ENABLED = System.getProperty("novautils.selftest") != null;

    private record Step(int delay, Runnable action) {}

    private static final List<Step> steps = new ArrayList<>();
    private static final List<String> results = new ArrayList<>();
    private static int index, waited, fails;
    private static boolean started, worldRequested;
    private static int inWorld;
    private static final Minecraft mc = Minecraft.getInstance();

    private SelfTest() {}

    private static void step(int waitTicks, Runnable action) {
        steps.add(new Step(waitTicks, action));
    }

    private static void check(boolean ok, String what) {
        String line = (ok ? "ok: " : "FAIL: ") + what;
        if (!ok) fails++;
        results.add(line);
        NovaUtils.LOG.info("[novautils-selftest] {}", line);
    }

    private static void cmd(String command) {
        var server = mc.getSingleplayerServer();
        server.execute(() -> server.getCommands().performPrefixedCommand(server.createCommandSourceStack().withSuppressedOutput(), command));
    }

    private static void shot(String name) {
        Screenshot.grab(mc.gameDirectory, "selftest_" + name + ".png", mc.getMainRenderTarget(), c -> {});
    }

    private static <T extends Module> T mod(Class<T> type) {
        return ModuleManager.get().get(type);
    }

    private static void on(Class<? extends Module> type) {
        mod(type).setEnabled(true);
    }

    private static void allOff() {
        for (Module m : ModuleManager.get().getModules()) {
            if (!(m instanceof dev.novautils.modules.misc.ChestTracker)) m.setEnabled(false);
        }
    }

    @SuppressWarnings("unchecked")
    private static <T> void set(Class<? extends Module> type, String name, T value) {
        for (Setting<?> s : mod(type).getSettings()) {
            if (s.name.equals(name)) {
                ((Setting<T>) s).set(value);
                return;
            }
        }
        throw new IllegalArgumentException(name);
    }

    private static String block(int x, int y, int z) {
        return mc.level.getBlockState(new BlockPos(x, y, z)).toString();
    }

    private static void use(BlockPos pos) {
        mc.gameMode.useItemOn(mc.player, InteractionHand.MAIN_HAND,
            new BlockHitResult(Vec3.atCenterOf(pos), Direction.UP, pos, false));
    }

    /** NovaUtilsForge's client tick, end phase. */
    public static void tick() {
        if (!ENABLED) return;
        if (!worldRequested) {
            if (!(mc.screen instanceof TitleScreen)) return;
            worldRequested = true;
            createWorld();
            return;
        }
        if (!started) {
            mc.options.pauseOnLostFocus = false;
            if (mc.screen instanceof net.minecraft.client.gui.screens.PauseScreen) mc.setScreen(null);
            if (mc.player == null || mc.level == null || ++inWorld < 60) return;
            started = true;
            plan();
        }
        if (mc.screen instanceof net.minecraft.client.gui.screens.PauseScreen) mc.setScreen(null);
        if (index >= steps.size()) return;
        Step s = steps.get(index);
        if (waited++ < s.delay()) return;
        waited = 0;
        index++;
        try {
            s.action().run();
        } catch (Throwable t) {
            check(false, "step " + index + " threw " + t);
            NovaUtils.LOG.error("[novautils-selftest] step failed", t);
        }
    }

    private static void createWorld() {
        try {
            Path dir = mc.gameDirectory.toPath().resolve("saves").resolve("novatest");
            if (Files.exists(dir)) {
                try (var walk = Files.walk(dir)) {
                    walk.sorted(java.util.Comparator.reverseOrder()).forEach(p -> p.toFile().delete());
                }
            }
        } catch (IOException ignored) {
        }
        GameRules rules = new GameRules();
        rules.getRule(GameRules.RULE_DAYLIGHT).set(false, null);
        rules.getRule(GameRules.RULE_DOMOBSPAWNING).set(false, null);
        rules.getRule(GameRules.RULE_WEATHER_CYCLE).set(false, null);
        LevelSettings settings = new LevelSettings("novatest", GameType.CREATIVE, false, Difficulty.EASY, true, rules, WorldDataConfiguration.DEFAULT);
        mc.createWorldOpenFlows().createFreshLevel("novatest", settings, new WorldOptions(12345L, false, false),
            access -> access.registryOrThrow(Registries.WORLD_PRESET).getHolderOrThrow(WorldPresets.FLAT).value().createWorldDimensions());
    }

    private static void plan() {
        step(0, () -> {
            mc.options.getSoundSourceOptionInstance(net.minecraft.sounds.SoundSource.MASTER).set(0.0);
            mc.getTutorial().setStep(net.minecraft.client.tutorial.TutorialSteps.NONE);
            allOff();
            cmd("/time set noon");
            cmd("/fill -6 99 -6 14 99 6 minecraft:stone");
            cmd("/fill -6 100 -6 14 106 6 minecraft:air");
            cmd("/tp @a 0 100 0 -90 0");
            cmd("/fill 3 100 -3 5 104 3 minecraft:stone");
            cmd("/setblock 4 102 -2 minecraft:diamond_ore");
            cmd("/setblock 4 102 0 minecraft:gold_ore");
            cmd("/setblock 4 101 2 minecraft:iron_ore");
            cmd("/setblock 4 100 0 minecraft:emerald_ore");
        });

        // --- menu and keybinds -------------------------------------------------------------
        step(40, () -> mc.setScreen(new ClickGuiScreen()));
        step(15, () -> {
            check(mc.screen instanceof ClickGuiScreen, "menu opens");
            shot("01_menu");
        });
        step(5, () -> {
            mc.setScreen(null);
            mod(dev.novautils.modules.movement.AutoSprint.class).setKeyBind(InputConstants.KEY_N);
            mc.keyboardHandler.keyPress(mc.getWindow().getWindow(), InputConstants.KEY_N, 0, InputConstants.PRESS, 0);
            mc.keyboardHandler.keyPress(mc.getWindow().getWindow(), InputConstants.KEY_N, 0, InputConstants.RELEASE, 0);
        });
        step(3, () -> {
            check(mod(dev.novautils.modules.movement.AutoSprint.class).isEnabled(), "keybind toggles a module");
            mod(dev.novautils.modules.movement.AutoSprint.class).setKeyBind(-1);
            allOff();
        });

        // --- xray / fullbright / esp -------------------------------------------------------
        step(5, () -> on(dev.novautils.modules.render.Xray.class));
        step(60, () -> {
            check(dev.novautils.modules.render.Xray.hides(net.minecraft.world.level.block.Blocks.STONE.defaultBlockState()), "xray hides stone");
            check(!dev.novautils.modules.render.Xray.hides(net.minecraft.world.level.block.Blocks.DIAMOND_ORE.defaultBlockState()), "xray keeps diamond ore");
            shot("02_xray");
        });
        step(5, () -> {
            allOff();
            cmd("/summon minecraft:zombie 8 100 0 {NoAI:1b,PersistenceRequired:1b}");
            cmd("/setblock 7 100 3 minecraft:chest");
            cmd("/setblock 7 101 3 minecraft:ender_chest");
            on(dev.novautils.modules.render.Fullbright.class);
            on(dev.novautils.modules.render.Esp.class);
            on(dev.novautils.modules.render.StorageEsp.class);
            on(dev.novautils.modules.render.Tracers.class);
            on(dev.novautils.modules.render.Nametags.class);
            set(dev.novautils.modules.render.Nametags.class, "mobs", true);
            on(dev.novautils.modules.misc.ActiveModules.class);
            on(dev.novautils.modules.misc.HudInfo.class);
            on(dev.novautils.modules.misc.ArmorHud.class);
            on(dev.novautils.modules.render.Minimap.class);
            mc.player.setYRot(-90);
            mc.player.setXRot(0);
        });
        step(40, () -> {
            check(mc.level.getEntitiesOfClass(net.minecraft.world.entity.monster.Zombie.class, mc.player.getBoundingBox().inflate(20)).size() == 1, "zombie for esp is there");
            shot("03_esp_hud_minimap");
        });

        // --- zoom / freecam / flight -------------------------------------------------------
        step(5, () -> {
            allOff();
            set(dev.novautils.modules.render.Zoom.class, "hold", false);
            on(dev.novautils.modules.render.Zoom.class);
        });
        step(30, () -> {
            check(PlatformCompat.lastFov < 40, "zoom narrows the fov (" + Math.round(PlatformCompat.lastFov) + ")");
            allOff();
        });
        final double[] freecamStart = new double[3];
        step(5, () -> {
            freecamStart[0] = mc.player.getX();
            freecamStart[1] = mc.player.getZ();
            on(dev.novautils.modules.render.Freecam.class);
            mc.options.keyUp.setDown(true);
        });
        step(20, () -> {
            mc.options.keyUp.setDown(false);
            var cam = PlatformCompat.camera(mc).getPosition();
            double bodyMoved = Math.abs(mc.player.getX() - freecamStart[0]) + Math.abs(mc.player.getZ() - freecamStart[1]);
            double camMoved = Math.abs(cam.x - freecamStart[0]) + Math.abs(cam.z - freecamStart[1]);
            check(bodyMoved < 0.2 && camMoved > 1, "freecam moves the camera, not the body (body " + String.format("%.2f", bodyMoved) + ", camera " + String.format("%.2f", camMoved) + ")");
            allOff();
        });
        final double[] flightY = new double[1];
        step(10, () -> {
            cmd("/gamemode survival @a");
            cmd("/tp @a 0 100 0 -90 0");
        });
        step(10, () -> {
            flightY[0] = mc.player.getY();
            on(dev.novautils.modules.movement.Flight.class);
            mc.options.keyJump.setDown(true);
        });
        step(15, () -> {
            mc.options.keyJump.setDown(false);
            check(mc.player.getY() > flightY[0] + 3, "flight goes up (" + String.format("%.1f", mc.player.getY() - flightY[0]) + ")");
            allOff();
            cmd("/tp @a 0 100 0 -90 0");
        });

        // --- shulker peek ------------------------------------------------------------------
        step(10, () -> {
            cmd("/clear @a");
            cmd("/give @a minecraft:purple_shulker_box{BlockEntityTag:{Items:[{Slot:0b,id:\"minecraft:diamond\",Count:5b},{Slot:13b,id:\"minecraft:golden_apple\",Count:3b}]}}");
        });
        step(10, () -> {
            int diamonds = 0;
            for (var s : dev.novautils.modules.render.ShulkerPeek.contents(mc.player.getInventory().getItem(0))) {
                if (s.is(Items.DIAMOND)) diamonds = s.getCount();
            }
            check(diamonds == 5, "shulker peek reads the box (" + diamonds + " diamonds)");
            on(dev.novautils.modules.render.ShulkerPeek.class);
            mc.setScreen(new InventoryScreen(mc.player));
        });
        step(5, () -> {
            var s = (AbstractContainerScreen<?>) mc.screen;
            var slot = s.getMenu().getSlot(36);
            double scale = mc.getWindow().getGuiScale();
            org.lwjgl.glfw.GLFW.glfwSetCursorPos(mc.getWindow().getWindow(),
                (s.getGuiLeft() + slot.x + 8) * scale, (s.getGuiTop() + slot.y + 8) * scale);
        });
        step(10, () -> shot("04_shulker_peek"));
        step(2, () -> {
            mc.setScreen(null);
            allOff();
            cmd("/clear @a");
        });

        // --- auto farm ---------------------------------------------------------------------
        step(5, () -> {
            cmd("/fill -3 99 2 -2 99 2 minecraft:farmland");
            cmd("/fill -3 100 2 -2 100 2 minecraft:wheat[age=7]");
            cmd("/give @a minecraft:wheat_seeds 8");
        });
        step(10, () -> on(dev.novautils.modules.world.AutoFarm.class));
        step(30, () -> {
            check(block(-3, 100, 2).contains("wheat") && block(-3, 100, 2).contains("age=0"), "autofarm harvests and replants (" + block(-3, 100, 2) + ")");
            allOff();
            cmd("/clear @a");
            cmd("/kill @e[type=minecraft:item]");
        });

        // --- packet mine -------------------------------------------------------------------
        step(5, () -> {
            cmd("/setblock 2 100 -2 minecraft:stone");
            cmd("/give @a minecraft:diamond_pickaxe");
        });
        step(10, () -> {
            on(dev.novautils.modules.player.PacketMine.class);
            mc.gameMode.startDestroyBlock(new BlockPos(2, 100, -2), Direction.UP);
            mc.player.setXRot(-60);
        });
        step(40, () -> {
            check(block(2, 100, -2).contains("air"), "packetmine breaks the block while looking away");
            allOff();
            cmd("/clear @a");
            cmd("/kill @e[type=minecraft:item]");
        });

        // --- auto smelt --------------------------------------------------------------------
        step(5, () -> {
            cmd("/setblock 1 100 1 minecraft:furnace");
            cmd("/give @a minecraft:raw_iron 8");
            cmd("/give @a minecraft:coal 4");
            cmd("/give @a minecraft:oak_log 16");
        });
        step(10, () -> {
            on(dev.novautils.modules.world.AutoSmelt.class);
            use(new BlockPos(1, 100, 1));
        });
        step(30, () -> {
            String in = "none", fuel = "none";
            if (mc.player.containerMenu instanceof AbstractFurnaceMenu m) {
                in = m.getSlot(0).getItem().toString();
                fuel = m.getSlot(1).getItem().toString();
            }
            check(in.contains("raw_iron") && fuel.contains("coal"), "autosmelt fills input and fuel (" + in + " / " + fuel + ")");
            mc.player.closeContainer();
            allOff();
            cmd("/clear @a");
            cmd("/setblock 1 100 1 minecraft:air");
        });

        // --- boat fly ----------------------------------------------------------------------
        final double[] boatY = new double[1];
        step(5, () -> cmd("/summon minecraft:boat 0 100 -3"));
        step(5, () -> cmd("/ride @p mount @e[type=minecraft:boat,limit=1,sort=nearest]"));
        step(10, () -> {
            boatY[0] = mc.player.getVehicle() == null ? -1 : mc.player.getVehicle().getY();
            check(boatY[0] > 0, "riding the test boat");
            on(dev.novautils.modules.movement.BoatFly.class);
            mc.options.keyJump.setDown(true);
        });
        step(20, () -> {
            mc.options.keyJump.setDown(false);
            double y = mc.player.getVehicle() == null ? -1 : mc.player.getVehicle().getY();
            check(y > boatY[0] + 2, "boatfly lifts the boat (" + String.format("%.1f", y - boatY[0]) + ")");
            allOff();
            cmd("/ride @p dismount");
            cmd("/kill @e[type=minecraft:boat]");
            cmd("/tp @a 0 100 0 -90 5");
        });

        // --- hits to kill ------------------------------------------------------------------
        step(10, () -> {
            cmd("/kill @e[type=minecraft:item]");
            cmd("/item replace entity @a weapon.mainhand with minecraft:iron_sword");
            cmd("/summon minecraft:husk 3 100 0 {NoAI:1b,PersistenceRequired:1b}");
        });
        step(10, () -> {
            on(dev.novautils.modules.render.HitsToKill.class);
            on(dev.novautils.modules.render.Minimap.class);
            double dmg = mod(dev.novautils.modules.render.HitsToKill.class).damage();
            check(Math.abs(dmg - 6.0) < 0.01, "hitstokill counts the sword damage (" + dmg + ")");
            mc.player.setYRot(-90);
            mc.player.setXRot(5);
        });
        step(20, () -> shot("05_hits_minimap"));

        // --- chat commands, profiles, favourites ------------------------------------------
        step(5, () -> {
            allOff();
            cmd("/kill @e[type=!minecraft:player]");
            cmd("/clear @a");
            dev.novautils.commands.Commands.handle(".t Fullbright");
            dev.novautils.commands.Commands.handle(".set Zoom zoom 8");
            dev.novautils.commands.Commands.handle(".bind AutoSprint n");
            check(mod(dev.novautils.modules.render.Fullbright.class).isEnabled()
                && mod(dev.novautils.modules.movement.AutoSprint.class).getKeyBind() == InputConstants.KEY_N, "chat commands toggle and bind");
            dev.novautils.commands.Commands.handle(".profile save nova_test");
            allOff();
            mod(dev.novautils.modules.movement.AutoSprint.class).setKeyBind(-1);
            dev.novautils.commands.Commands.handle(".profile load nova_test");
            check(mod(dev.novautils.modules.render.Fullbright.class).isEnabled()
                && mod(dev.novautils.modules.movement.AutoSprint.class).getKeyBind() == InputConstants.KEY_N, "profile save/load");
            dev.novautils.commands.Commands.handle(".profile del nova_test");
            mc.player.connection.sendChat(".t Fullbright");
            check(!mod(dev.novautils.modules.render.Fullbright.class).isEnabled(), "typing a . command in chat runs it");
            mod(dev.novautils.modules.movement.AutoSprint.class).setKeyBind(-1);
            mod(dev.novautils.modules.render.Xray.class).setFavorite(true);
            ModuleManager.get().saveConfig();
            mod(dev.novautils.modules.render.Xray.class).setFavorite(false);
            dev.novautils.config.Config.get().load(ModuleManager.get().getModules());
            check(mod(dev.novautils.modules.render.Xray.class).isFavorite(), "favourites are saved");
            mod(dev.novautils.modules.render.Xray.class).setFavorite(false);
            allOff();
        });

        // --- menu: switch click, classic menu ----------------------------------------------
        step(5, () -> mc.setScreen(new ClickGuiScreen()));
        step(5, () -> {
            var s = (ClickGuiScreen) mc.screen;
            int[] c = s.categoryCenter(dev.novautils.modules.Category.RENDER);
            s.mouseClicked(c[0], c[1], 0);
        });
        step(3, () -> {
            var s = (ClickGuiScreen) mc.screen;
            int[] sw = s.switchCenter(mod(dev.novautils.modules.render.Fullbright.class));
            if (sw != null) s.mouseClicked(sw[0], sw[1], 0);
            check(mod(dev.novautils.modules.render.Fullbright.class).isEnabled(), "clicking a module's switch turns it on");
            int[] st = s.styleButtonCenter();
            s.mouseClicked(st[0], st[1], 0);
        });
        step(5, () -> {
            check(mc.screen instanceof dev.novautils.gui.screens.ClassicGuiScreen, "classic menu opens");
            var s = (dev.novautils.gui.screens.ClassicGuiScreen) mc.screen;
            int[] row = s.rowCenter(mod(dev.novautils.modules.render.Zoom.class), null);
            if (row != null) s.mouseClicked(row[0], row[1], 0);
            check(mod(dev.novautils.modules.render.Zoom.class).isEnabled(), "left-click in the classic menu toggles a module");
        });
        step(5, () -> shot("06_classic_menu"));
        step(2, () -> {
            mc.screen.mouseClicked(252, 15, 0);
            check(mc.screen instanceof ClickGuiScreen && !dev.novautils.gui.GuiState.get().isClassic(), "Nova menu button goes back");
            mc.setScreen(null);
            allOff();
        });

        // --- inventory tools -----------------------------------------------------------------
        step(5, () -> {
            cmd("/gamemode survival @a");
            cmd("/item replace entity @a container.9 with minecraft:stone 5");
            cmd("/item replace entity @a container.10 with minecraft:apple 3");
            cmd("/item replace entity @a container.11 with minecraft:stone 7");
            cmd("/item replace entity @a container.14 with minecraft:bone 2");
            cmd("/item replace entity @a container.20 with minecraft:apple 1");
        });
        step(5, () -> on(dev.novautils.modules.player.InventorySort.class));
        step(10, () -> {
            StringBuilder b = new StringBuilder();
            for (int i = 9; i < 15; i++) b.append(net.minecraft.core.registries.BuiltInRegistries.ITEM.getKey(mc.player.getInventory().getItem(i).getItem()).getPath()).append(',');
            check(b.toString().startsWith("apple,apple,bone,stone,stone,air"), "InventorySort sorts (" + b + ")");
            cmd("/clear @a");
            cmd("/item replace entity @a container.12 with minecraft:dirt 16");
        });
        step(5, () -> on(dev.novautils.modules.player.AutoDrop.class));
        step(10, () -> {
            int dirt = 0;
            for (int i = 0; i < 36; i++) if (mc.player.getInventory().getItem(i).is(Items.DIRT)) dirt++;
            check(dirt == 0, "AutoDrop throws junk away");
            allOff();
            cmd("/kill @e[type=minecraft:item]");
            cmd("/clear @a");
            cmd("/setblock 1 100 2 minecraft:crafting_table");
            cmd("/give @a minecraft:iron_ingot 18");
        });
        step(10, () -> {
            on(dev.novautils.modules.world.AutoCompact.class);
            use(new BlockPos(1, 100, 2));
        });
        step(30, () -> {
            int blocks = 0;
            for (int i = 0; i < 36; i++) if (mc.player.getInventory().getItem(i).is(Items.IRON_BLOCK)) blocks += mc.player.getInventory().getItem(i).getCount();
            check(blocks == 2, "AutoCompact crafts blocks (" + blocks + ")");
            mc.player.closeContainer();
            allOff();
            cmd("/clear @a");
            cmd("/setblock 1 100 2 minecraft:air");
            cmd("/setblock -2 100 2 minecraft:chest{Items:[{Slot:0b,id:\"minecraft:diamond\",Count:7b}]}");
        });
        step(10, () -> {
            on(dev.novautils.modules.misc.ChestTracker.class);
            use(new BlockPos(-2, 100, 2));
        });
        step(10, () -> mc.player.closeContainer());
        step(3, () -> {
            String found = mod(dev.novautils.modules.misc.ChestTracker.class).find("diamond").toString();
            check(found.contains("7x at -2 100 2"), "ChestTracker finds the chest (" + found + ")");
            cmd("/setblock -2 100 2 minecraft:air");
        });

        // --- AutoTunnel / AutoBuild ---------------------------------------------------------
        step(5, () -> {
            cmd("/fill 2 100 -1 9 101 1 minecraft:stone");
            cmd("/give @a minecraft:diamond_pickaxe");
            cmd("/tp @a 0 100 0 -90 0");
        });
        step(10, () -> {
            set(dev.novautils.modules.world.AutoTunnel.class, "max-length", 6);
            set(dev.novautils.modules.world.AutoTunnel.class, "torches", false);
            on(dev.novautils.modules.world.AutoTunnel.class);
        });
        step(200, () -> {
            StringBuilder b = new StringBuilder();
            for (int x = 2; x <= 5; x++) for (int y = 100; y <= 101; y++) b.append(mc.level.getBlockState(new BlockPos(x, y, 0)).isAir() ? 'o' : '#');
            check(b.toString().equals("oooooooo") && mc.player.getX() > 3, "AutoTunnel digs and walks (" + b + ")");
            allOff();
            cmd("/fill -6 100 -6 14 106 6 minecraft:air");
            cmd("/clear @a");
            cmd("/kill @e[type=minecraft:item]");
            cmd("/tp @a 0 100 0 -90 0");
            cmd("/give @a minecraft:cobblestone 32");
        });
        step(10, () -> {
            set(dev.novautils.modules.world.AutoBuild.class, "shape", dev.novautils.modules.world.AutoBuild.Shape.WALL);
            set(dev.novautils.modules.world.AutoBuild.class, "size", 3);
            set(dev.novautils.modules.world.AutoBuild.class, "height", 2);
            on(dev.novautils.modules.world.AutoBuild.class);
        });
        step(40, () -> {
            int n = 0;
            for (int z = -1; z <= 1; z++) for (int y = 100; y <= 101; y++) if (mc.level.getBlockState(new BlockPos(2, y, z)).is(net.minecraft.world.level.block.Blocks.COBBLESTONE)) n++;
            check(n == 6, "AutoBuild builds the wall (" + n + "/6)");
            allOff();
            cmd("/fill -6 100 -6 14 106 6 minecraft:air");
            cmd("/clear @a");
            cmd("/tp @a 0 115 0 -90 60");
        });
        step(3, () -> {
            on(dev.novautils.modules.render.FallPredict.class);
            on(dev.novautils.modules.render.ChunkBorders.class);
        });
        step(3, () -> {
            var landing = mod(dev.novautils.modules.render.FallPredict.class).landing();
            check(landing != null && Math.abs(landing.y - 100) < 0.6, "FallPredict finds the floor (" + landing + ")");
            check(mod(dev.novautils.modules.render.ChunkBorders.class).seed() != null, "ChunkBorders knows the seed");
        });
        step(40, () -> {
            allOff();
            cmd("/gamemode creative @a");
            cmd("/tp @a 0 100 0 -90 0");
            cmd("/summon minecraft:zombie 4 100 0 {NoAI:1b,PersistenceRequired:1b,Health:12f}");
            cmd("/summon minecraft:pig 4 100 3 {NoAI:1b,PersistenceRequired:1b}");
            on(dev.novautils.modules.render.HealthBars.class);
        });
        step(20, () -> {
            shot("07_health_bars");
            allOff();
        });

        // --- config ------------------------------------------------------------------------
        step(5, () -> {
            allOff();
            on(dev.novautils.modules.misc.Notifier.class);
            ModuleManager.get().saveConfig();
            try {
                String json = Files.readString(mc.gameDirectory.toPath().resolve("config").resolve("novautils.json"));
                int at = json.indexOf("\"Notifier\"");
                check(at >= 0 && json.substring(at, Math.min(json.length(), at + 120)).contains("\"enabled\": true"), "config saves");
            } catch (IOException e) {
                check(false, "config saves (" + e + ")");
            }
            allOff();
        });

        step(5, () -> {
            results.add("DONE fails=" + fails + " checks=" + results.size());
            NovaUtils.LOG.info("[novautils-selftest] DONE fails={}", fails);
            try {
                Files.write(mc.gameDirectory.toPath().resolve("selftest-result.txt"), results);
            } catch (IOException ignored) {
            }
            mc.stop();
        });
    }
}
