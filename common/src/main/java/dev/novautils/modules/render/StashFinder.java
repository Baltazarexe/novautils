package dev.novautils.modules.render;

import dev.novautils.gui.Theme;
import dev.novautils.gui.Toasts;
import dev.novautils.modules.Category;
import dev.novautils.modules.Module;
import dev.novautils.render.RenderContext;
import dev.novautils.settings.Setting;
import dev.novautils.utils.RenderUtils;
import dev.novautils.utils.Storage;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BarrelBlockEntity;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.ChestBlockEntity;
import net.minecraft.world.level.block.entity.EnderChestBlockEntity;
import net.minecraft.world.level.block.entity.ShulkerBoxBlockEntity;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Stashes: chunks holding a lot of storage. Every loaded chunk's chests, barrels, shulker boxes and
 * ender chests are counted; a chunk past the limit is announced (toast, sound), written to
 * config/novautils_stashes.csv and marked with its storage outlined and a label of what's there.
 */
public class StashFinder extends Module {
    private final Setting<Integer> minStorage = register(Setting.builder(Integer.class, "min-storage")
        .description("How many storage blocks one chunk needs to count as a stash (a shulker box counts as 3).").defaultValue(6).range(2, 64).build());
    private final Setting<Boolean> ignoreEnderChests = register(Setting.builder(Boolean.class, "ignore-ender-chests")
        .description("Leave ender chests out (their items aren't stored there).").defaultValue(true).build());
    private final Setting<Boolean> outlines = register(Setting.builder(Boolean.class, "outlines")
        .description("Outline the storage blocks of a stash.").defaultValue(true).build());
    private final Setting<Boolean> labels = register(Setting.builder(Boolean.class, "labels")
        .description("A label over each stash with what it holds.").defaultValue(true).build());
    private final Setting<Integer> color = register(Setting.builder(Integer.class, "color")
        .description("Outline colour.").defaultValue(0xFFFFC94A).asColor().build());
    private final Setting<Boolean> alert = register(Setting.builder(Boolean.class, "alert")
        .description("Toast and a sound for every new stash.").defaultValue(true).build());
    private final Setting<Boolean> log = register(Setting.builder(Boolean.class, "log-to-file")
        .description("Write stashes to config/novautils_stashes.csv.").defaultValue(true).build());

    private record Stash(long chunk, List<BlockPos> storage, int chests, int barrels, int shulkers, int enders, Vec3 center) {}

    private final Map<Long, Stash> stashes = new HashMap<>();
    private final Set<Long> announced = new HashSet<>();
    private Level lastLevel;
    private int scanIn;

    public StashFinder() {
        super("StashFinder", "Finds chunks full of chests and shulker boxes, with a toast and a log.", Category.RENDER);
    }

    @Override
    public void onDisable() {
        stashes.clear();
    }

    @Override
    public void onTick() {
        if (mc.level == null || mc.player == null) return;
        if (mc.level != lastLevel) {
            lastLevel = mc.level;
            stashes.clear();
            announced.clear();
        }
        if (--scanIn > 0) return;
        scanIn = 20;
        int r = mc.options.getEffectiveRenderDistance();
        ChunkPos me = mc.player.chunkPosition();
        Set<Long> seen = new HashSet<>();
        for (int dx = -r; dx <= r; dx++) {
            for (int dz = -r; dz <= r; dz++) {
                int cx = me.x() + dx, cz = me.z() + dz;
                if (!mc.level.hasChunk(cx, cz)) continue;
                List<BlockPos> storage = new ArrayList<>();
                int chests = 0, barrels = 0, shulkers = 0, enders = 0;
                double sx = 0, sy = 0, sz = 0;
                for (BlockEntity be : mc.level.getChunk(cx, cz).getBlockEntities().values()) {
                    if (be instanceof ShulkerBoxBlockEntity) shulkers++;
                    else if (be instanceof BarrelBlockEntity) barrels++;
                    else if (be instanceof EnderChestBlockEntity) {
                        if (ignoreEnderChests.get()) continue;
                        enders++;
                    } else if (be instanceof ChestBlockEntity) chests++;
                    else continue;
                    BlockPos p = be.getBlockPos();
                    storage.add(p);
                    sx += p.getX() + 0.5;
                    sy += p.getY() + 0.5;
                    sz += p.getZ() + 0.5;
                }
                int score = chests + barrels + enders + shulkers * 3;
                if (score < minStorage.get()) continue;
                long key = ChunkPos.pack(cx, cz);
                seen.add(key);
                int n = storage.size();
                Stash s = new Stash(key, storage, chests, barrels, shulkers, enders, new Vec3(sx / n, sy / n + 1.5, sz / n));
                stashes.put(key, s);
                if (announced.add(key)) announce(s);
            }
        }
        stashes.keySet().retainAll(seen);
    }

    private static String summary(Stash s) {
        List<String> parts = new ArrayList<>();
        if (s.chests() > 0) parts.add(s.chests() + " chests");
        if (s.barrels() > 0) parts.add(s.barrels() + " barrels");
        if (s.shulkers() > 0) parts.add(s.shulkers() + " shulkers");
        if (s.enders() > 0) parts.add(s.enders() + " ender chests");
        return String.join(", ", parts);
    }

    private void announce(Stash s) {
        BlockPos p = BlockPos.containing(s.center());
        if (alert.get()) {
            Toasts.push("Stash at " + p.getX() + " " + p.getY() + " " + p.getZ() + ": " + summary(s), color.get() | 0xFF000000);
            mc.getSoundManager().play(net.minecraft.client.resources.sounds.SimpleSoundInstance.forUI(net.minecraft.sounds.SoundEvents.NOTE_BLOCK_BELL.value(), 1.4f));
        }
        if (!log.get()) return;
        Path file = Storage.configDir().resolve("novautils_stashes.csv");
        String line = java.time.LocalDateTime.now().withNano(0) + "," + mc.level.dimension().identifier() + "," + p.getX() + "," + p.getY() + "," + p.getZ()
            + "," + s.chests() + "," + s.barrels() + "," + s.shulkers() + "," + s.enders() + System.lineSeparator();
        try {
            boolean fresh = !Files.exists(file);
            Files.createDirectories(file.getParent());
            if (fresh) Files.writeString(file, "time,dimension,x,y,z,chests,barrels,shulkers,ender_chests" + System.lineSeparator(), StandardCharsets.UTF_8);
            Files.writeString(file, line, StandardCharsets.UTF_8, StandardOpenOption.APPEND);
        } catch (IOException e) {
            dev.novautils.NovaUtils.LOG.warn("StashFinder: could not write {}", file, e);
        }
    }

    /** Stashes around you (in-game test). */
    public int stashCount() {
        return stashes.size();
    }

    @Override
    public void onRenderWorld(RenderContext context) {
        if (stashes.isEmpty()) return;
        int c = color.get();
        List<Stash> list = new ArrayList<>(stashes.values());
        if (outlines.get()) {
            RenderUtils.world(context, (pose, buffer) -> {
                for (Stash s : list) for (BlockPos p : s.storage()) RenderUtils.drawEdges(pose, buffer, new AABB(p).deflate(0.02), (c & 0xFFFFFF) | 0xD0000000, 0.015);
            });
        }
        if (labels.get()) {
            for (Stash s : list) {
                int dist = (int) Math.round(mc.player.position().distanceTo(s.center()));
                RenderUtils.label(context, s.center(), Component.literal("Stash: " + summary(s) + "  " + dist + "m").withStyle(net.minecraft.network.chat.Style.EMPTY.withColor(net.minecraft.network.chat.TextColor.fromRgb(Theme.mix(c, 0xFFFFFFFF, 0.3f) & 0xFFFFFF))), 1f, true);
            }
        }
    }
}
