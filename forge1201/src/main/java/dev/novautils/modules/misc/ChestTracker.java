package dev.novautils.modules.misc;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;
import dev.novautils.NovaUtils;
import dev.novautils.gui.Toasts;
import dev.novautils.modules.Category;
import dev.novautils.modules.Module;
import dev.novautils.render.RenderContext;
import dev.novautils.settings.Setting;
import dev.novautils.utils.RenderUtils;
import dev.novautils.utils.Storage;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.Container;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Remembers what was in every chest, barrel, shulker box (placed) and other container you open,
 * per server and dimension, and what's in your ender chest. {@code .find <item>} lists where you
 * left something and outlines those containers for a while; ShulkerPeek shows the ender chest
 * contents when you hover an ender chest item. Nothing is sent anywhere - it's what you saw.
 */
public class ChestTracker extends Module {
    private final Setting<Integer> highlightSeconds = register(Setting.builder(Integer.class, "highlight-seconds")
        .description("How long .find outlines the containers it found.").defaultValue(30).range(5, 300).build());

    private final Setting<Integer> color = register(Setting.builder(Integer.class, "color")
        .description("Outline colour for .find results.").defaultValue(0x70FFD84A).asColor().build());

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    /** item id -> count, per "dimension|x,y,z"; plus "echest" for the ender chest. */
    private Map<String, Map<String, Integer>> data = new LinkedHashMap<>();
    private String loadedKey;
    private String openKey;
    private AbstractContainerMenu openMenu;
    private boolean dirty;

    private final List<BlockPos> highlights = new ArrayList<>();
    private long highlightUntil;

    public ChestTracker() {
        super("ChestTracker", "Remembers chest contents; .find <item> shows where it is.", Category.MISC);
        // on by default: it only remembers what you see, and is useless if it missed the chests
        setEnabled(true);
    }

    // --- storage ---------------------------------------------------------------------------

    private String serverKey() {
        if (mc.getCurrentServer() != null) return mc.getCurrentServer().ip.replaceAll("[^A-Za-z0-9._-]", "_");
        if (mc.getSingleplayerServer() != null) {
            return "sp_" + mc.getSingleplayerServer().getWorldData().getLevelName().replaceAll("[^A-Za-z0-9._-]", "_");
        }
        return "unknown";
    }

    private Path file(String key) {
        return Storage.configDir().resolve("novautils_chests").resolve(key + ".json");
    }

    private void ensureLoaded() {
        String key = serverKey();
        if (key.equals(loadedKey)) return;
        save();
        loadedKey = key;
        data = new LinkedHashMap<>();
        Path f = file(key);
        if (!Files.exists(f)) return;
        try (Reader r = Files.newBufferedReader(f, StandardCharsets.UTF_8)) {
            Map<String, Map<String, Integer>> read = GSON.fromJson(r, new TypeToken<LinkedHashMap<String, LinkedHashMap<String, Integer>>>() {}.getType());
            if (read != null) data = read;
        } catch (IOException | RuntimeException e) {
            NovaUtils.LOG.error("Failed to load chest data", e);
        }
    }

    private void save() {
        if (!dirty || loadedKey == null) return;
        dirty = false;
        try {
            Files.createDirectories(file(loadedKey).getParent());
            try (Writer w = Files.newBufferedWriter(file(loadedKey), StandardCharsets.UTF_8)) {
                GSON.toJson(data, w);
            }
        } catch (IOException e) {
            NovaUtils.LOG.error("Failed to save chest data", e);
        }
    }

    private String dim() {
        return mc.level.dimension().location().toString();
    }

    private String posKey(BlockPos p) {
        return dim() + "|" + p.getX() + "," + p.getY() + "," + p.getZ();
    }

    private static BlockPos parsePos(String key) {
        String[] xyz = key.substring(key.indexOf('|') + 1).split(",");
        return new BlockPos(Integer.parseInt(xyz[0]), Integer.parseInt(xyz[1]), Integer.parseInt(xyz[2]));
    }

    // --- tracking --------------------------------------------------------------------------

    @Override
    public void onDisable() {
        save();
        openMenu = null;
    }

    @Override
    public void onTick() {
        if (mc.player == null || mc.level == null) return;
        ensureLoaded();
        AbstractContainerMenu menu = mc.player.containerMenu;

        if (menu == mc.player.inventoryMenu) {
            if (openMenu != null) save();
            openMenu = null;
            openKey = null;
            return;
        }
        if (menu != openMenu) {
            openMenu = menu;
            openKey = null;
            // the container was opened by the click on the block we're looking at
            if (mc.hitResult instanceof BlockHitResult hit && hit.getType() == HitResult.Type.BLOCK) {
                BlockState state = mc.level.getBlockState(hit.getBlockPos());
                if (state.is(Blocks.ENDER_CHEST)) openKey = "echest";
                else if (state.getBlock() instanceof BaseEntityBlock) openKey = posKey(hit.getBlockPos());
            }
        }
        if (openKey == null) return;

        // snapshot the container's own slots (not your inventory) every tick it's open
        Map<String, Integer> items = new LinkedHashMap<>();
        Container playerInv = mc.player.getInventory();
        for (Slot slot : menu.slots) {
            if (slot.container == playerInv) continue;
            ItemStack s = slot.getItem();
            if (s.isEmpty()) continue;
            items.merge(BuiltInRegistries.ITEM.getKey(s.getItem()).toString(), s.getCount(), Integer::sum);
        }
        if (!items.equals(data.get(openKey))) {
            data.put(openKey, items);
            dirty = true;
        }
    }

    // --- queries ---------------------------------------------------------------------------

    /** Ender chest contents as last seen, or null if never opened on this server. */
    public List<ItemStack> enderChest() {
        if (mc.level == null) return null;
        ensureLoaded();
        Map<String, Integer> e = data.get("echest");
        if (e == null) return null;
        List<ItemStack> out = new ArrayList<>();
        e.forEach((id, count) -> BuiltInRegistries.ITEM.getOptional(new ResourceLocation(id)).ifPresent(item -> {
            int left = count;
            while (left > 0) {
                ItemStack s = new ItemStack(item, Math.min(left, item.getMaxStackSize()));
                left -= s.getCount();
                out.add(s);
            }
        }));
        return out;
    }

    /** {@code .find}: lines to print, nearest first, and outlines them in the world. */
    public List<String> find(String query) {
        List<String> lines = new ArrayList<>();
        if (mc.level == null) return lines;
        ensureLoaded();
        String q = query.toLowerCase().replace(' ', '_');
        String here = dim() + "|";
        Map<BlockPos, Integer> hits = new HashMap<>();
        int inEchest = 0;
        for (var entry : data.entrySet()) {
            int n = 0;
            for (var item : entry.getValue().entrySet()) {
                String id = item.getKey();
                String path = id.substring(id.indexOf(':') + 1);
                if (path.contains(q)) n += item.getValue();
            }
            if (n == 0) continue;
            if (entry.getKey().equals("echest")) inEchest = n;
            else if (entry.getKey().startsWith(here)) hits.put(parsePos(entry.getKey()), n);
        }
        List<BlockPos> sorted = new ArrayList<>(hits.keySet());
        sorted.sort((a, b) -> Double.compare(a.distSqr(mc.player.blockPosition()), b.distSqr(mc.player.blockPosition())));
        for (BlockPos p : sorted) {
            lines.add(hits.get(p) + "x at " + p.getX() + " " + p.getY() + " " + p.getZ()
                + " (" + Math.round(Math.sqrt(p.distSqr(mc.player.blockPosition()))) + "m)");
        }
        if (inEchest > 0) lines.add(inEchest + "x in your ender chest");
        highlights.clear();
        highlights.addAll(sorted);
        highlightUntil = System.currentTimeMillis() + highlightSeconds.get() * 1000L;
        return lines;
    }

    public int tracked() {
        ensureLoaded();
        return data.size();
    }

    public void forgetAll() {
        ensureLoaded();
        data.clear();
        dirty = true;
        save();
        Toasts.push("Chest memory cleared", 0xFF8A8A9A);
    }

    @Override
    public void onRenderWorld(RenderContext context) {
        if (highlights.isEmpty() || System.currentTimeMillis() > highlightUntil) return;
        int c = color.get();
        for (BlockPos p : highlights) {
            AABB box = new AABB(p);
            RenderUtils.world(context, (pose, buffer) -> RenderUtils.drawBox(pose, buffer, box, c));
        }
        for (int i = 0; i < Math.min(3, highlights.size()); i++) {
            BlockPos p = highlights.get(i);
            RenderUtils.label(context, net.minecraft.world.phys.Vec3.atCenterOf(p).add(0, 0.9, 0),
                Component.literal("#" + (i + 1)).withStyle(s_ -> s_.withColor(c & 0xFFFFFF)), 1f, true);
        }
    }
}
