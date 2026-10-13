package dev.novautils.modules.render;

import dev.novautils.gui.Theme;
import dev.novautils.modules.Category;
import dev.novautils.modules.Module;
import dev.novautils.render.RenderContext;
import dev.novautils.settings.Setting;
import dev.novautils.utils.Friends;
import dev.novautils.utils.PlatformCompat;
import dev.novautils.utils.RenderUtils;
import dev.novautils.utils.ScreenSpace;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.scores.PlayerTeam;
import net.minecraft.world.scores.Scoreboard;

import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

/**
 * Entity ESP, through walls, in several styles:
 * <ul>
 *   <li>CORNERS - only the corners of a box around the entity, drawn flat on the screen. The
 *       cleanest look.</li>
 *   <li>BOX_2D - a thin rectangle on the screen.</li>
 *   <li>BOX_3D - a soft filled box with brighter edges, in the world.</li>
 *   <li>WIREFRAME - just the edges of the 3D box.</li>
 *   <li>CHAMS - the mob's own model, see-through, in a solid colour or with its texture,
 *       optionally cycling through the rainbow.</li>
 *   <li>GLOW - the game's own glow outline around the model (coloured with a client-side
 *       scoreboard team, nothing is sent to the server).</li>
 * </ul>
 * The flat styles can add a health bar at the side and the name and distance underneath. Every
 * style keeps working with a shaderpack on.
 */
public class Esp extends Module {
    public enum Mode { CORNERS, BOX_2D, BOX_3D, WIREFRAME, CHAMS, GLOW }
    public enum ChamsStyle { SOLID, TEXTURED }

    /** The plain white texture SOLID chams are drawn with (tinted to the colour). */
    public static final net.minecraft.resources.Identifier WHITE = net.minecraft.resources.Identifier.fromNamespaceAndPath("novautils", "textures/white.png");
    private static Esp instance;

    private enum Kind { PLAYER, FRIEND, HOSTILE, PASSIVE }

    private final Setting<Mode> mode = register(Setting.builder(Mode.class, "mode")
        .description("CORNERS / BOX_2D: flat on screen. BOX_3D / WIREFRAME: in the world. CHAMS: the model through walls. GLOW: game outline.")
        .defaultValue(Mode.CORNERS).build());

    private final Setting<Boolean> players = register(Setting.builder(Boolean.class, "players")
        .description("Highlight other players.").defaultValue(true).build());

    private final Setting<Integer> playerColor = register(Setting.builder(Integer.class, "players-color")
        .description("Colour for players.").defaultValue(0xFFFF5A5A).asColor().visible(players::get).build());

    private final Setting<Integer> friendColor = register(Setting.builder(Integer.class, "friends-color")
        .description("Colour for players on your friends list.").defaultValue(0xFF55E6FF).asColor().visible(players::get).build());

    private final Setting<Boolean> hostiles = register(Setting.builder(Boolean.class, "hostile-mobs")
        .description("Highlight hostile mobs.").defaultValue(true).build());

    private final Setting<Integer> hostileColor = register(Setting.builder(Integer.class, "hostile-mobs-color")
        .description("Colour for hostile mobs.").defaultValue(0xFFFFA040).asColor().visible(hostiles::get).build());

    private final Setting<Boolean> passives = register(Setting.builder(Boolean.class, "passive-mobs")
        .description("Highlight animals and neutral mobs.").defaultValue(false).build());

    private final Setting<Integer> passiveColor = register(Setting.builder(Integer.class, "passive-mobs-color")
        .description("Colour for animals and neutral mobs.").defaultValue(0xFF66E07A).asColor().visible(passives::get).build());

    private final Setting<ChamsStyle> chamsStyle = register(Setting.builder(ChamsStyle.class, "chams-style")
        .description("SOLID: one flat colour. TEXTURED: the mob's own skin, tinted.").defaultValue(ChamsStyle.SOLID)
        .visible(() -> mode.get() == Mode.CHAMS).build());

    private final Setting<Double> chamsOpacity = register(Setting.builder(Double.class, "chams-opacity")
        .description("How solid the chams look.").defaultValue(0.55).range(0.15, 1.0)
        .visible(() -> mode.get() == Mode.CHAMS).build());

    private final Setting<Boolean> rainbow = register(Setting.builder(Boolean.class, "rainbow")
        .description("Cycle the chams colour through the rainbow.").defaultValue(false)
        .visible(() -> mode.get() == Mode.CHAMS).build());

    private final Setting<Integer> thickness = register(Setting.builder(Integer.class, "thickness")
        .description("Line thickness.").defaultValue(1).range(1, 3)
        .visible(() -> mode.get() == Mode.CORNERS || mode.get() == Mode.BOX_2D || mode.get() == Mode.WIREFRAME).build());

    private final Setting<Double> fill = register(Setting.builder(Double.class, "fill")
        .description("How see-through the inside is (0 = no fill).").defaultValue(0.15).range(0.0, 0.8)
        .visible(() -> mode.get() == Mode.CORNERS || mode.get() == Mode.BOX_2D || mode.get() == Mode.BOX_3D).build());

    private final Setting<Boolean> healthBar = register(Setting.builder(Boolean.class, "health-bar")
        .description("Health bar beside the box (flat styles).").defaultValue(true)
        .visible(() -> flat()).build());

    private final Setting<Boolean> info = register(Setting.builder(Boolean.class, "name-distance")
        .description("Name and distance under the box (flat styles).").defaultValue(false)
        .visible(() -> flat()).build());

    private final Setting<Integer> range = register(Setting.builder(Integer.class, "range")
        .description("Only entities this close.").defaultValue(128).range(8, 512).build());

    private final Map<Kind, PlayerTeam> teams = new HashMap<>();
    private final Map<Integer, Entity> glowing = new HashMap<>();
    private Scoreboard boundTo;

    public Esp() {
        super("Esp", "Highlights entities through walls: corners, 2D/3D box, wireframe or glow.", Category.RENDER);
        instance = this;
    }

    /** Shaderpacks replace entity rendering, so CHAMS shows as GLOW while one is on. */
    private boolean glowing() {
        return mode.get() == Mode.GLOW || (mode.get() == Mode.CHAMS && dev.novautils.utils.ShaderCompat.shadersActive());
    }

    /** ChamsMixin: the chams colour for this entity (ARGB), or 0 for none. */
    public static int chamsColor(Entity e) {
        Esp esp = instance;
        if (esp == null || !esp.isEnabled() || esp.mode.get() != Mode.CHAMS || dev.novautils.utils.ShaderCompat.shadersActive()) return 0;
        if (mc.player == null) return 0;
        Kind kind = esp.kindOf(e);
        if (kind == null) return 0;
        int rgb;
        if (esp.rainbow.get()) {
            float hue = (System.currentTimeMillis() % 3600) / 3600f + e.getId() * 0.07f;
            rgb = java.awt.Color.HSBtoRGB(hue, 0.65f, 1f) & 0xFFFFFF;
        } else {
            rgb = esp.colorOf(kind) & 0xFFFFFF;
        }
        int a = Math.max(26, Math.min(255, (int) (esp.chamsOpacity.get() * 255)));
        return a << 24 | rgb;
    }

    public static boolean chamsTextured() {
        return instance != null && instance.chamsStyle.get() == ChamsStyle.TEXTURED;
    }

    private boolean flat() {
        return mode.get() == Mode.CORNERS || mode.get() == Mode.BOX_2D;
    }

    private Kind kindOf(Entity e) {
        if (e == mc.player || !(e instanceof LivingEntity living) || !living.isAlive() || e instanceof ArmorStand) return null;
        if (mc.player.distanceTo(e) > range.get()) return null;
        if (e instanceof Player) {
            if (!players.get()) return null;
            return Friends.isFriend(e) ? Kind.FRIEND : Kind.PLAYER;
        }
        if (e instanceof Enemy) return hostiles.get() ? Kind.HOSTILE : null;
        return passives.get() ? Kind.PASSIVE : null;
    }

    private int colorOf(Kind kind) {
        return switch (kind) {
            case PLAYER -> playerColor.get();
            case FRIEND -> friendColor.get();
            case HOSTILE -> hostileColor.get();
            case PASSIVE -> passiveColor.get();
        };
    }

    // ------------------------------------------------------------------ GLOW

    private PlayerTeam team(Kind kind) {
        Scoreboard board = mc.level.getScoreboard();
        if (board != boundTo) {
            teams.clear();
            glowing.clear();
            boundTo = board;
        }
        return teams.computeIfAbsent(kind, k -> {
            String name = "novautils_" + k.name().toLowerCase(Locale.ROOT);
            PlayerTeam team = board.getPlayerTeam(name);
            if (team == null) team = board.addPlayerTeam(name);
            switch (k) {
                case PLAYER -> PlatformCompat.setPlayerColor(team);
                case HOSTILE -> PlatformCompat.setHostileColor(team);
                case PASSIVE, FRIEND -> PlatformCompat.setPassiveColor(team);
            }
            return team;
        });
    }

    /** Vanilla reads the synced shared flag 6 (glowing) on the client, so set that one. */
    private static void glow(Entity e, boolean on) {
        ((dev.novautils.mixin.EntityAccessor) e).novautils$setSharedFlag(6, on);
    }

    private void clearGlow() {
        if (mc.level != null) {
            Scoreboard board = mc.level.getScoreboard();
            for (Entity e : glowing.values()) {
                glow(e, false);
                if (board == boundTo) board.removePlayerFromTeam(e.getScoreboardName());
            }
        }
        glowing.clear();
    }

    @Override
    public void onDisable() {
        clearGlow();
    }

    @Override
    public void onTick() {
        if (mc.level == null || mc.player == null) return;
        if (!glowing()) {
            if (!glowing.isEmpty()) clearGlow();
            return;
        }
        Scoreboard board = mc.level.getScoreboard();
        Map<Integer, Entity> seen = new HashMap<>();
        for (Entity e : mc.level.entitiesForRendering()) {
            Kind kind = kindOf(e);
            if (kind == null) continue;
            PlayerTeam team = team(kind);
            seen.put(e.getId(), e);
            glow(e, true);
            if (board.getPlayersTeam(e.getScoreboardName()) != team) board.addPlayerToTeam(e.getScoreboardName(), team);
            glowing.put(e.getId(), e);
        }
        glowing.values().removeIf(e -> {
            if (seen.containsKey(e.getId())) return false;
            glow(e, false);
            board.removePlayerFromTeam(e.getScoreboardName());
            return true;
        });
    }

    // ------------------------------------------------------------------ 3D styles

    private AABB boxAt(Entity e, float partial) {
        return e.getBoundingBox().move(e.getPosition(partial).subtract(e.position()));
    }

    @Override
    public void onRenderWorld(RenderContext context) {
        if (mc.level == null || mc.player == null) return;
        Mode m = mode.get();
        if (m != Mode.BOX_3D && m != Mode.WIREFRAME) return;
        float partial = mc.getDeltaTracker().getGameTimeDeltaPartialTick(false);
        for (Entity e : mc.level.entitiesForRendering()) {
            Kind kind = kindOf(e);
            if (kind == null) continue;
            int c = colorOf(kind);
            AABB box = boxAt(e, partial);
            if (m == Mode.BOX_3D) {
                int a = (int) (40 + 215 * fill.get());
                int color = (Math.min(255, a) << 24) | (c & 0xFFFFFF);
                RenderUtils.world(context, (pose, buffer) -> RenderUtils.drawBox(pose, buffer, box, color));
            } else {
                double w = 0.008 * thickness.get();
                RenderUtils.world(context, (pose, buffer) -> RenderUtils.drawEdges(pose, buffer, box, c | 0xFF000000, w));
            }
        }
    }

    // ------------------------------------------------------------------ flat (screen) styles

    /** Screen rectangle {x1, y1, x2, y2} around the entity's box, or null when it's behind you. */
    private static int[] screenRect(AABB a) {
        double minX = Double.MAX_VALUE, minY = Double.MAX_VALUE, maxX = -Double.MAX_VALUE, maxY = -Double.MAX_VALUE;
        for (int i = 0; i < 8; i++) {
            double[] s = ScreenSpace.project(new Vec3((i & 1) == 0 ? a.minX : a.maxX, (i & 2) == 0 ? a.minY : a.maxY, (i & 4) == 0 ? a.minZ : a.maxZ));
            if (s == null) return null;
            minX = Math.min(minX, s[0]);
            maxX = Math.max(maxX, s[0]);
            minY = Math.min(minY, s[1]);
            maxY = Math.max(maxY, s[1]);
        }
        return new int[]{(int) Math.floor(minX), (int) Math.floor(minY), (int) Math.ceil(maxX), (int) Math.ceil(maxY)};
    }

    /** A line with a dark 1px border so it reads on any background. */
    private static void line(GuiGraphicsExtractor g, int x1, int y1, int x2, int y2, int color) {
        g.fill(x1 - 1, y1 - 1, x2 + 1, y2 + 1, 0xA0000000);
        g.fill(x1, y1, x2, y2, color);
    }

    @Override
    public void onRenderHud(GuiGraphicsExtractor g, DeltaTracker delta) {
        if (!flat() || mc.level == null || mc.player == null || !ScreenSpace.begin()) return;
        float partial = delta.getGameTimeDeltaPartialTick(false);
        int t = thickness.get();
        for (Entity e : mc.level.entitiesForRendering()) {
            Kind kind = kindOf(e);
            if (kind == null) continue;
            int[] r = screenRect(boxAt(e, partial));
            if (r == null || r[2] < 0 || r[3] < 0 || r[0] > g.guiWidth() || r[1] > g.guiHeight()) continue;
            int x1 = r[0], y1 = r[1], x2 = Math.max(r[2], x1 + 3), y2 = Math.max(r[3], y1 + 3);
            int c = colorOf(kind) | 0xFF000000;

            if (fill.get() > 0) g.fill(x1, y1, x2, y2, Theme.alpha(c, fill.get().floatValue() * 0.6f));
            if (mode.get() == Mode.BOX_2D) {
                line(g, x1, y1, x2, y1 + t, c);
                line(g, x1, y2 - t, x2, y2, c);
                line(g, x1, y1, x1 + t, y2, c);
                line(g, x2 - t, y1, x2, y2, c);
            } else {
                int lx = Math.max(2, (x2 - x1) / 4), ly = Math.max(2, (y2 - y1) / 5);
                line(g, x1, y1, x1 + lx, y1 + t, c);
                line(g, x1, y1, x1 + t, y1 + ly, c);
                line(g, x2 - lx, y1, x2, y1 + t, c);
                line(g, x2 - t, y1, x2, y1 + ly, c);
                line(g, x1, y2 - t, x1 + lx, y2, c);
                line(g, x1, y2 - ly, x1 + t, y2, c);
                line(g, x2 - lx, y2 - t, x2, y2, c);
                line(g, x2 - t, y2 - ly, x2, y2, c);
            }

            if (healthBar.get() && e instanceof LivingEntity l) {
                float f = Math.max(0f, Math.min(1f, l.getHealth() / l.getMaxHealth()));
                int bx = x1 - 4, h = y2 - y1;
                int filled = Math.round(h * f);
                int hc = 0xFF000000 | (int) (255 * Math.min(1f, 2f * (1f - f))) << 16 | (int) (220 * Math.min(1f, 2f * f)) << 8 | 0x40;
                g.fill(bx - 1, y1 - 1, bx + 2, y2 + 1, 0xA0000000);
                g.fill(bx, y2 - filled, bx + 1, y2, hc);
            }
            if (info.get()) {
                String text = e.getName().getString() + " " + Math.round(mc.player.distanceTo(e)) + "m";
                int tw = mc.font.width(text);
                g.text(mc.font, text, (x1 + x2) / 2 - tw / 2, y2 + 3, 0xFFFFFFFF, true);
            }
        }
    }
}
