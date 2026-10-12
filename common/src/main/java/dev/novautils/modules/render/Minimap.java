package dev.novautils.modules.render;

import dev.novautils.gui.Theme;
import dev.novautils.modules.Category;
import dev.novautils.modules.HudModule;
import dev.novautils.settings.Setting;
import dev.novautils.utils.Friends;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.material.MapColor;

import java.util.HashMap;
import java.util.Map;

/**
 * A small north-up map in a corner: terrain coloured like a vanilla map (with the same hill
 * shading), you in the middle with your facing, and dots for players, mobs, items and waypoints.
 * Terrain is sampled a slice per tick and cached per block column, so it costs almost nothing.
 * In the Nether (or anywhere with a roof) it maps the floor around your height instead of the roof.
 */
public class Minimap extends HudModule {
    private final Setting<Integer> size = register(Setting.builder(Integer.class, "size")
        .description("Size on screen in pixels.").defaultValue(96).range(48, 200).build());

    private final Setting<Integer> radius = register(Setting.builder(Integer.class, "radius")
        .description("How many blocks from you to the edge.").defaultValue(48).range(16, 128).build());

    private final Setting<Boolean> terrain = register(Setting.builder(Boolean.class, "terrain")
        .description("Draw the ground (off = radar only).").defaultValue(true).build());

    private final Setting<Boolean> players = register(Setting.builder(Boolean.class, "players")
        .description("White dots for players, cyan for friends.").defaultValue(true).build());

    private final Setting<Boolean> mobs = register(Setting.builder(Boolean.class, "mobs")
        .description("Red dots for hostile mobs, green for the rest.").defaultValue(true).build());

    private final Setting<Boolean> items = register(Setting.builder(Boolean.class, "items")
        .description("Yellow dots for dropped items.").defaultValue(false).build());

    private final Setting<Boolean> coords = register(Setting.builder(Boolean.class, "coords")
        .description("Your X Z under the map.").defaultValue(true).build());

    private final Map<Long, Integer> colors = new HashMap<>();
    private int scanRow;
    private Object lastLevel;

    public Minimap() {
        super("Minimap", "A small map with you, mobs, players and waypoints.", Category.RENDER);
    }

    @Override
    protected int defaultX(int screenW, int w) {
        return screenW - w - 4;
    }

    @Override
    public void onDisable() {
        colors.clear();
    }

    private int surfaceY(int x, int z) {
        var level = mc.level;
        if (!level.dimensionType().hasCeiling()) return level.getHeight(Heightmap.Types.WORLD_SURFACE, x, z) - 1;
        // under a roof: first solid block going down from just above our head
        int top = (int) mc.player.getY() + 2;
        BlockPos.MutableBlockPos p = new BlockPos.MutableBlockPos();
        for (int y = top; y > top - 24; y--) {
            p.set(x, y, z);
            if (!level.getBlockState(p).isAir()) return y;
        }
        return Integer.MIN_VALUE;
    }

    private int sample(int x, int z) {
        if (!mc.level.hasChunk(x >> 4, z >> 4)) return 0;
        int y = surfaceY(x, z);
        if (y == Integer.MIN_VALUE) return 0xFF0C0C10;
        BlockPos pos = new BlockPos(x, y, z);
        BlockState state = mc.level.getBlockState(pos);
        MapColor mapColor = state.getMapColor(mc.level, pos);
        if (mapColor == MapColor.NONE) return 0xFF0C0C10;
        int north = surfaceY(x, z - 1);
        MapColor.Brightness b = north == Integer.MIN_VALUE || y == north ? MapColor.Brightness.NORMAL
            : y > north ? MapColor.Brightness.HIGH : MapColor.Brightness.LOW;
        return mapColor.calculateARGBColor(b);
    }

    @Override
    public void onTick() {
        if (mc.player == null || mc.level == null || !terrain.get()) return;
        if (mc.level != lastLevel) {
            lastLevel = mc.level;
            colors.clear();
        }
        int r = radius.get();
        int px = mc.player.getBlockX(), pz = mc.player.getBlockZ();
        // refresh a few rows per tick, the whole map about once a second
        int rowsPerTick = Math.max(2, (2 * r + 1) / 16);
        for (int n = 0; n < rowsPerTick; n++) {
            int dz = scanRow - r;
            for (int dx = -r; dx <= r; dx++) {
                int x = px + dx, z = pz + dz;
                colors.put(BlockPos.asLong(x, 0, z), sample(x, z));
            }
            scanRow = (scanRow + 1) % (2 * r + 1);
        }
        if (colors.size() > (2 * r + 1) * (2 * r + 1) * 3) {
            colors.keySet().removeIf(k -> Math.abs(BlockPos.getX(k) - px) > r * 1.5 || Math.abs(BlockPos.getZ(k) - pz) > r * 1.5);
        }
    }

    private void dot(GuiGraphicsExtractor g, int x, int y, int s, double wx, double wz, int r, int color, int half) {
        double dx = wx - mc.player.getX(), dz = wz - mc.player.getZ();
        if (Math.abs(dx) > r || Math.abs(dz) > r) return;
        int cx = x + s / 2 + (int) Math.round(dx * s / (2.0 * r));
        int cy = y + s / 2 + (int) Math.round(dz * s / (2.0 * r));
        g.fill(cx - half, cy - half, cx + half + 1, cy + half + 1, 0xFF000000);
        g.fill(cx - half + 1, cy - half + 1, cx + half, cy + half, color);
    }

    @Override
    protected int[] render(GuiGraphicsExtractor g, int x, int y, boolean right) {
        int s = size.get(), r = radius.get();
        int cells = 2 * r + 1;
        Theme.softShadow(g, x, y, s, s, 6, 0.5f);
        Theme.rounded(g, x - 2, y - 2, s + 4, s + 4, 6, 0xE0101116);
        g.fill(x, y, x + s, y + s, 0xFF1A1C22);

        int px = mc.player.getBlockX(), pz = mc.player.getBlockZ();
        if (terrain.get() && mc.level != null) {
            for (int j = 0; j < cells; j++) {
                int y0 = y + j * s / cells, y1 = y + (j + 1) * s / cells;
                if (y1 <= y0) continue;
                int runStart = 0, runColor = 0;
                for (int i = 0; i <= cells; i++) {
                    int c = i == cells ? 1 : colors.getOrDefault(BlockPos.asLong(px - r + i, 0, pz - r + j), 0);
                    if (i == 0) {
                        runColor = c;
                        continue;
                    }
                    if (c != runColor || i == cells) {
                        if (runColor != 0) g.fill(x + runStart * s / cells, y0, x + i * s / cells, y1, runColor);
                        runStart = i;
                        runColor = c;
                    }
                }
            }
        }

        if (mc.level != null) {
            for (Entity e : mc.level.entitiesForRendering()) {
                if (e == mc.player) continue;
                if (e instanceof Player p) {
                    if (players.get()) dot(g, x, y, s, e.getX(), e.getZ(), r, Friends.isFriend(p) ? 0xFF55FFFF : 0xFFFFFFFF, 2);
                } else if (e instanceof LivingEntity) {
                    if (mobs.get()) dot(g, x, y, s, e.getX(), e.getZ(), r, e instanceof Enemy ? 0xFFFF5555 : 0xFF66DD77, 1);
                } else if (e instanceof ItemEntity && items.get()) {
                    dot(g, x, y, s, e.getX(), e.getZ(), r, 0xFFFFD84A, 1);
                }
            }
            var wps = dev.novautils.modules.ModuleManager.get().get(Waypoints.class);
            if (wps.isEnabled()) {
                for (double[] w : wps.positionsHere()) dot(g, x, y, s, w[0], w[2], r, Theme.ACCENT2, 2);
            }
        }

        // you: a dot plus a short line toward where you're facing
        int cx = x + s / 2, cy = y + s / 2;
        double yaw = Math.toRadians(mc.player.getYRot());
        for (int i = 2; i <= 6; i++) {
            int lx = cx + (int) Math.round(-Math.sin(yaw) * i), ly = cy + (int) Math.round(Math.cos(yaw) * i);
            g.fill(lx, ly, lx + 1, ly + 1, 0xFFFFFFFF);
        }
        g.fill(cx - 2, cy - 2, cx + 3, cy + 3, 0xFF000000);
        g.fill(cx - 1, cy - 1, cx + 2, cy + 2, Theme.ACCENT);
        g.text(mc.font, "N", cx - mc.font.width("N") / 2, y + 2, 0xC0FFFFFF, true);

        int h = s;
        if (coords.get()) {
            String t = px + ", " + pz;
            g.text(mc.font, t, x + s / 2 - mc.font.width(t) / 2, y + s + 4, Theme.TEXT_DIM, true);
            h += 4 + mc.font.lineHeight;
        }
        return new int[]{s, h};
    }
}
