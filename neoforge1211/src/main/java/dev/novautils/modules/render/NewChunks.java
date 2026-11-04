package dev.novautils.modules.render;

import dev.novautils.gui.Theme;
import dev.novautils.gui.UiFont;
import dev.novautils.modules.Category;
import dev.novautils.modules.Module;
import dev.novautils.render.RenderContext;
import dev.novautils.settings.Setting;
import dev.novautils.utils.RenderUtils;
import net.minecraft.client.gui.GuiGraphics;
import io.netty.buffer.Unpooled;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientboundBlockUpdatePacket;
import net.minecraft.network.protocol.game.ClientboundSectionBlocksUpdatePacket;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.Biomes;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.chunk.LevelChunkSection;
import net.minecraft.world.level.chunk.PalettedContainer;
import net.minecraft.world.phys.AABB;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Tells freshly generated chunks from ones someone already loaded - how people follow trails to
 * bases on 2b2t. Two clues, so it works in the Overworld, the Nether and the End:
 *
 * <ul>
 *   <li><b>Biome palette</b> (every dimension): a chunk the server has just generated still carries
 *   a leftover "plains" entry in each section's biome palette - the value the palette starts with
 *   before the real biomes are filled in. Once the server has saved the chunk and loaded it again,
 *   the palette is rebuilt from the biomes really there and the leftover is gone. Leftover = NEW
 *   (red), none = OLD (green).</li>
 *   <li><b>Liquids</b> (where the palette can't tell, e.g. a plains-only chunk): when a chunk is
 *   generated its water and lava start flowing right away, so the server streams "liquid changed"
 *   updates for it: NEW. A chunk that arrives already containing flowing liquid: OLD.</li>
 * </ul>
 *
 * A line of old chunks through new terrain is somebody's path.
 *
 * <p>Each region is drawn as one shape: a soft fill plus a low wall along its outer edge only, so a
 * trail reads as a band from any angle instead of a grid of faint squares. A small top-down map on
 * the HUD shows the same around you.
 */
public class NewChunks extends Module {
    public enum Corner { TOP_LEFT, TOP_RIGHT, BOTTOM_LEFT, BOTTOM_RIGHT }

    private final Setting<Integer> newColor = register(Setting.builder(Integer.class, "new-color")
        .description("Colour of never-loaded chunks.").defaultValue(0xFFFF4040).asColor().build());

    private final Setting<Boolean> showOld = register(Setting.builder(Boolean.class, "show-old")
        .description("Also mark chunks someone loaded before (the trail).").defaultValue(true).build());

    private final Setting<Integer> oldColor = register(Setting.builder(Integer.class, "old-color")
        .description("Colour of previously loaded chunks.").defaultValue(0xFF35E07A).asColor()
        .visible(() -> showOld.get()).build());

    private final Setting<Double> fillOpacity = register(Setting.builder(Double.class, "fill-opacity")
        .description("How strong the tint over a region is (0 = only its border).").defaultValue(0.22).range(0.0, 0.8).build());

    private final Setting<Boolean> borders = register(Setting.builder(Boolean.class, "borders")
        .description("A low wall along the edge of each region, visible from any angle.").defaultValue(true).build());

    private final Setting<Double> borderHeight = register(Setting.builder(Double.class, "border-height")
        .description("How tall the border wall is.").defaultValue(0.6).range(0.05, 4.0).visible(borders::get).build());

    private final Setting<Boolean> merge = register(Setting.builder(Boolean.class, "merge-regions")
        .description("Only outline where a region ends, so touching chunks form one shape (off: every chunk outlined).").defaultValue(true)
        .visible(borders::get).build());

    private final Setting<Integer> yOffset = register(Setting.builder(Integer.class, "height")
        .description("Draw the markers this many blocks below your feet.").defaultValue(2).range(-64, 64).build());

    private final Setting<Boolean> map = register(Setting.builder(Boolean.class, "chunk-map")
        .description("A small top-down map of new and old chunks on the HUD, you in the middle.").defaultValue(true).build());

    private final Setting<Corner> mapCorner = register(Setting.builder(Corner.class, "map-corner")
        .description("Where the chunk map sits.").defaultValue(Corner.BOTTOM_RIGHT).visible(map::get).build());

    private final Setting<Integer> mapSize = register(Setting.builder(Integer.class, "map-size")
        .description("Chunk map size on screen.").defaultValue(96).range(48, 200).visible(map::get).build());

    private final Set<Long> newChunks = new HashSet<>();
    private final Set<Long> oldChunks = new HashSet<>();
    private final Set<Long> seen = new HashSet<>();
    private Object lastLevel;

    public NewChunks() {
        super("NewChunks", "Shows new vs already-loaded chunks (find trails), in the world and on a chunk map.", Category.RENDER);
    }

    @Override
    public void onDisable() {
        newChunks.clear();
        oldChunks.clear();
        seen.clear();
    }

    private static boolean flowing(BlockState s) {
        var f = s.getFluidState();
        return !f.isEmpty() && !f.isSource();
    }

    private void liquidUpdate(BlockPos pos, BlockState state) {
        if (!flowing(state)) return;
        long key = ChunkPos.asLong(pos);
        if (!oldChunks.contains(key)) newChunks.add(key);
    }

    @Override
    public boolean onReceivePacket(Packet<?> packet) {
        if (packet instanceof ClientboundBlockUpdatePacket p) {
            liquidUpdate(p.getPos(), p.getBlockState());
        } else if (packet instanceof ClientboundSectionBlocksUpdatePacket p) {
            p.runUpdates(this::liquidUpdate);
        }
        return false;
    }

    /**
     * 1 = new, 2 = old, 0 = can't tell, from the biome palettes (see the class comment). A section
     * whose palette lists more biomes than its cells use still has the generation leftover.
     */
    static int paletteVerdict(LevelChunk chunk) {
        int fresh = 0, reloaded = 0;
        for (LevelChunkSection s : chunk.getSections()) {
            if (s == null || !(s.getBiomes() instanceof PalettedContainer<?> pc)) continue;
            int size = paletteSize(pc);
            if (size <= 0) continue;
            Set<Holder<Biome>> used = new HashSet<>();
            boolean plains = false;
            for (int x = 0; x < 4; x++) for (int y = 0; y < 4; y++) for (int z = 0; z < 4; z++) {
                Holder<Biome> b = s.getNoiseBiome(x, y, z);
                used.add(b);
                if (b.is(Biomes.PLAINS)) plains = true;
            }
            if (size > used.size()) fresh++;
            else if (!plains) reloaded++; // a real plains section looks the same either way
        }
        return fresh > 0 ? 1 : reloaded > 0 ? 2 : 0;
    }

    /** Entries in a biome palette, read the way the server sent it; -1 when it has no palette of its own. */
    private static int paletteSize(PalettedContainer<?> pc) {
        FriendlyByteBuf buf = new FriendlyByteBuf(Unpooled.buffer());
        try {
            pc.write(buf);
            int bits = buf.readByte();
            if (bits == 0) return 1;
            if (bits > 3) return -1; // global palette: nothing left over to see
            return buf.readVarInt();
        } catch (Throwable t) {
            return -1;
        } finally {
            buf.release();
        }
    }

    /** A chunk we see for the first time: the biome palette decides; otherwise flowing liquid already in it means it's been loaded before. */
    private void firstSight(LevelChunk chunk, long key) {
        int verdict = paletteVerdict(chunk);
        if (verdict == 1) {
            newChunks.add(key);
            return;
        }
        if (verdict == 2) {
            newChunks.remove(key);
            oldChunks.add(key);
            return;
        }
        if (newChunks.contains(key)) return;
        for (LevelChunkSection s : chunk.getSections()) {
            if (s == null || s.hasOnlyAir()) continue;
            if (s.maybeHas(NewChunks::flowing)) {
                for (int y = 0; y < 16; y++) for (int z = 0; z < 16; z++) for (int x = 0; x < 16; x++) {
                    if (flowing(s.getBlockState(x, y, z))) {
                        oldChunks.add(key);
                        return;
                    }
                }
            }
        }
    }

    @Override
    public void onTick() {
        if (mc.level == null || mc.player == null) return;
        if (mc.level != lastLevel) {
            lastLevel = mc.level;
            onDisable();
        }
        int r = mc.options.getEffectiveRenderDistance();
        ChunkPos c = mc.player.chunkPosition();
        int budget = 24;
        for (int dx = -r; dx <= r && budget > 0; dx++) {
            for (int dz = -r; dz <= r && budget > 0; dz++) {
                int cx = c.x + dx, cz = c.z + dz;
                long key = ChunkPos.asLong(cx, cz);
                if (seen.contains(key) || !mc.level.hasChunk(cx, cz)) continue;
                LevelChunk chunk = mc.level.getChunk(cx, cz);
                if (chunk instanceof net.minecraft.world.level.chunk.EmptyLevelChunk) continue; // a placeholder until the real data arrives
                seen.add(key);
                firstSight(chunk, key);
                budget--;
            }
        }
    }

    /** 1 = new, 2 = old (when shown), 0 = neither. */
    private int kindOf(int cx, int cz) {
        long key = ChunkPos.asLong(cx, cz);
        if (newChunks.contains(key)) return 1;
        if (showOld.get() && oldChunks.contains(key)) return 2;
        return 0;
    }

    @Override
    public void onRenderWorld(RenderContext context) {
        if (mc.player == null || (newChunks.isEmpty() && oldChunks.isEmpty())) return;
        double y = Math.floor(mc.player.getY()) - yOffset.get();
        int r = mc.options.getEffectiveRenderDistance() + 1;
        ChunkPos me = mc.player.chunkPosition();
        List<int[]> cells = new ArrayList<>();
        for (int dx = -r; dx <= r; dx++) {
            for (int dz = -r; dz <= r; dz++) {
                int k = kindOf(me.x + dx, me.z + dz);
                if (k != 0) cells.add(new int[]{me.x + dx, me.z + dz, k});
            }
        }
        if (cells.isEmpty()) return;
        float fill = fillOpacity.get().floatValue();
        double h = borderHeight.get();
        boolean walls = borders.get(), merged = merge.get();
        int nc = newColor.get(), oc = oldColor.get();
        RenderUtils.world(context, (pose, buffer) -> {
            for (int[] c : cells) {
                int color = c[2] == 1 ? nc : oc;
                double x = c[0] * 16, z = c[1] * 16;
                if (fill > 0.01f) RenderUtils.drawFilledBox(pose, buffer, new AABB(x, y, z, x + 16, y + 0.02, z + 16), Theme.alpha(color | 0xFF000000, fill));
                if (!walls) continue;
                int edge = Theme.alpha(color | 0xFF000000, 0.85f);
                double t = 0.12;
                // a wall only where the neighbouring chunk isn't the same kind (or on every side, unmerged)
                if (!merged || kindOf(c[0], c[1] - 1) != c[2]) RenderUtils.drawFilledBox(pose, buffer, new AABB(x, y, z, x + 16, y + h, z + t), edge);
                if (!merged || kindOf(c[0], c[1] + 1) != c[2]) RenderUtils.drawFilledBox(pose, buffer, new AABB(x, y, z + 16 - t, x + 16, y + h, z + 16), edge);
                if (!merged || kindOf(c[0] - 1, c[1]) != c[2]) RenderUtils.drawFilledBox(pose, buffer, new AABB(x, y, z, x + t, y + h, z + 16), edge);
                if (!merged || kindOf(c[0] + 1, c[1]) != c[2]) RenderUtils.drawFilledBox(pose, buffer, new AABB(x + 16 - t, y, z, x + 16, y + h, z + 16), edge);
            }
        });
    }

    @Override
    public void onRenderHud(GuiGraphics g, float delta) {
        if (!map.get() || mc.player == null || mc.level == null || dev.novautils.utils.PlatformCompat.currentScreen(mc) != null) return;
        int size = mapSize.get(), pad = 6, head = 13, foot = 13;
        int w = size + 8, h = size + head + foot + 6;
        int px = switch (mapCorner.get()) { case TOP_LEFT, BOTTOM_LEFT -> pad; default -> g.guiWidth() - w - pad; };
        int py = switch (mapCorner.get()) { case TOP_LEFT, TOP_RIGHT -> pad; default -> g.guiHeight() - h - pad; };

        // panel: thin light rim, dark glass inside
        Theme.rounded(g, px, py, w, h, 6, 0x24FFFFFF);
        Theme.rounded(g, px + 1, py + 1, w - 2, h - 2, 5, 0xD80D0E12);
        UiFont.text(g, "Chunks", px + 6, py + 4, Theme.TEXT, false);
        ChunkPos me = mc.player.chunkPosition();
        String where = me.x + ", " + me.z;
        UiFont.text(g, where, px + w - 6 - UiFont.width(where), py + 4, Theme.TEXT_OFF, false);

        int x0 = px + 4, y0 = py + head + 2;
        Theme.rounded(g, x0, y0, size, size, 3, 0xFF08090C);

        // the map scrolls smoothly with you: you are always the exact centre
        int r = Math.min(mc.options.getEffectiveRenderDistance(), 16);
        float cell = size / (float) (2 * r + 1);
        double fx = mc.player.getX() / 16.0, fz = mc.player.getZ() / 16.0;
        float cxm = x0 + size / 2f, cym = y0 + size / 2f;
        int newC = newColor.get() | 0xFF000000, oldC = oldColor.get() | 0xFF000000;
        int gap = cell >= 5 ? 1 : 0;
        g.enableScissor(x0, y0, x0 + size, y0 + size);
        for (int dx = -r - 1; dx <= r + 1; dx++) {
            for (int dz = -r - 1; dz <= r + 1; dz++) {
                int ccx = me.x + dx, ccz = me.z + dz;
                int ax = Math.round(cxm + (float) ((ccx - fx) * cell)), ay = Math.round(cym + (float) ((ccz - fz) * cell));
                int bx = Math.round(cxm + (float) ((ccx + 1 - fx) * cell)), by = Math.round(cym + (float) ((ccz + 1 - fz) * cell));
                int k = kindOf(ccx, ccz);
                if (k == 0) {
                    int c = mc.level.hasChunk(ccx, ccz) ? 0x1CFFFFFF : 0x08FFFFFF;
                    g.fill(ax, ay, Math.max(ax + 1, bx - gap), Math.max(ay + 1, by - gap), c);
                    continue;
                }
                int col = k == 1 ? newC : oldC;
                // regions read as one shape: a soft fill, and a bright edge only where the region ends
                g.fill(ax, ay, bx, by, Theme.alpha(col, 0.42f));
                int edge = Theme.alpha(col, 0.95f);
                if (kindOf(ccx, ccz - 1) != k) g.fill(ax, ay, bx, ay + 1, edge);
                if (kindOf(ccx, ccz + 1) != k) g.fill(ax, by - 1, bx, by, edge);
                if (kindOf(ccx - 1, ccz) != k) g.fill(ax, ay, ax + 1, by, edge);
                if (kindOf(ccx + 1, ccz) != k) g.fill(bx - 1, ay, bx, by, edge);
            }
        }
        g.disableScissor();

        // north marker
        UiFont.text(g, "N", Math.round(cxm) - UiFont.width("N") / 2, y0 + 2, 0xB0FFFFFF, false);

        // you: an arrow pointing where you look (north is up)
        double yaw = Math.toRadians(mc.player.getYRot());
        float sx = (float) -Math.sin(yaw), sy = (float) Math.cos(yaw); // forward on the map
        float ox = -sy, oy = sx;                                       // sideways
        float tipX = cxm + sx * 6, tipY = cym + sy * 6;
        float lX = cxm - sx * 4 + ox * 4, lY = cym - sy * 4 + oy * 4;
        float rX = cxm - sx * 4 - ox * 4, rY = cym - sy * 4 - oy * 4;
        float nX = cxm - sx * 2, nY = cym - sy * 2;
        triangle(g, tipX, tipY, lX, lY, nX, nY, 0xFFFFFFFF);
        triangle(g, tipX, tipY, nX, nY, rX, rY, 0xFFD8DCE6);

        // legend
        int ly = py + h - foot + 1;
        int lx = px + 6;
        Theme.rounded(g, lx, ly + 2, 5, 5, 2, newC);
        String nl = "New " + count(newChunks, me, r);
        UiFont.text(g, nl, lx + 8, ly, Theme.TEXT_DIM, false);
        lx += 8 + UiFont.width(nl) + 8;
        if (showOld.get()) {
            Theme.rounded(g, lx, ly + 2, 5, 5, 2, oldC);
            UiFont.text(g, "Old " + count(oldChunks, me, r), lx + 8, ly, Theme.TEXT_DIM, false);
        }
    }

    /** A filled triangle, one pixel row at a time (for the little arrow on the map). */
    private static void triangle(GuiGraphics g, float ax, float ay, float bx, float by, float cx, float cy, int color) {
        int top = (int) Math.floor(Math.min(ay, Math.min(by, cy))), bottom = (int) Math.ceil(Math.max(ay, Math.max(by, cy)));
        float[][] e = {{ax, ay, bx, by}, {bx, by, cx, cy}, {cx, cy, ax, ay}};
        for (int y = top; y < bottom; y++) {
            float yc = y + 0.5f, lo = Float.MAX_VALUE, hi = -Float.MAX_VALUE;
            for (float[] s : e) {
                if ((yc < s[1]) == (yc < s[3])) continue;
                float x = s[0] + (yc - s[1]) / (s[3] - s[1]) * (s[2] - s[0]);
                lo = Math.min(lo, x);
                hi = Math.max(hi, x);
            }
            if (hi >= lo) g.fill(Math.round(lo), y, Math.max(Math.round(lo) + 1, Math.round(hi)), y + 1, color);
        }
    }

    private static int count(Set<Long> set, ChunkPos me, int r) {
        int n = 0;
        for (long key : set) if (Math.abs(ChunkPos.getX(key) - me.x) <= r && Math.abs(ChunkPos.getZ(key) - me.z) <= r) n++;
        return n;
    }

    public int newCount() {
        return newChunks.size();
    }

    public int oldCount() {
        return oldChunks.size();
    }
}
