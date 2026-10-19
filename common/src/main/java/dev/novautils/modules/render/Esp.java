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
    public enum Mode { CLEAN, CORNERS, BOX_2D, BOX_3D, WIREFRAME, CHAMS, GLOW }

    /** The plain white texture SOLID chams are drawn with (tinted to the colour). */
    public static final net.minecraft.resources.Identifier WHITE = net.minecraft.resources.Identifier.fromNamespaceAndPath("novautils", "textures/white.png");
    private static Esp instance;

    private enum Kind { PLAYER, FRIEND, HOSTILE, PASSIVE }

    // Each look is its own switch, so they can be combined (CLEAN box + textured chams, glow + 3D box...)
    private final Setting<Boolean> showClean = look("clean-box", "Tidy 2D box with the name above and an animated health bar.", true);
    private final Setting<Boolean> showCorners = look("corners", "Only the corners of a box, flat on screen.", false);
    private final Setting<Boolean> show2d = look("box-2d", "A thin rectangle, flat on screen.", false);
    private final Setting<Boolean> show3d = look("box-3d", "A soft filled box in the world.", false);
    private final Setting<Boolean> showWire = look("wireframe", "Just the edges of the 3D box.", false);
    private final Setting<Boolean> showChams = look("chams", "The model itself through walls in one flat colour.", false);
    private final Setting<Boolean> showChamsTex = look("chams-textured", "The model itself through walls with its own skin, tinted. Can be on together with the others.", false);
    private final Setting<Boolean> showGlow = look("glow", "Soft glowing outline around the model.", false);

    private Setting<Boolean> look(String name, String description, boolean on) {
        return register(Setting.builder(Boolean.class, name).description(description).defaultValue(on).build());
    }

    /** Tests and the self-test: show just one look. */
    public void showOnly(Mode m) {
        showClean.set(m == Mode.CLEAN);
        showCorners.set(m == Mode.CORNERS);
        show2d.set(m == Mode.BOX_2D);
        show3d.set(m == Mode.BOX_3D);
        showWire.set(m == Mode.WIREFRAME);
        showChams.set(m == Mode.CHAMS);
        showChamsTex.set(false);
        showGlow.set(m == Mode.GLOW);
    }

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

    private final Setting<Double> chamsOpacity = register(Setting.builder(Double.class, "chams-opacity")
        .description("How solid the chams look.").defaultValue(0.55).range(0.15, 1.0)
        .visible(this::chamsOn).build());

    private final Setting<Boolean> rainbow = register(Setting.builder(Boolean.class, "rainbow")
        .description("Cycle the colour through the rainbow (every style).").defaultValue(false).build());

    private final Setting<Boolean> healthColor = register(Setting.builder(Boolean.class, "color-by-health")
        .description("Colour by health: green when full, yellow, then red when nearly dead.").defaultValue(false)
        .visible(() -> !rainbow.get()).build());

    private final Setting<Boolean> twoTone = register(Setting.builder(Boolean.class, "two-tone")
        .description("A different colour while the entity is behind a wall.").defaultValue(false).build());

    private final Setting<Integer> behindColor = register(Setting.builder(Integer.class, "behind-wall-color")
        .description("Colour used behind walls (two-tone).").defaultValue(0xFFB48CFF).asColor().visible(twoTone::get).build());

    private final Setting<Boolean> fade = register(Setting.builder(Boolean.class, "distance-fade")
        .description("Far away entities are drawn fainter, close ones stronger.").defaultValue(true).build());

    private final Setting<Boolean> pulse = register(Setting.builder(Boolean.class, "pulse")
        .description("Gently breathe the opacity.").defaultValue(false).build());

    private final Setting<Boolean> arrows = register(Setting.builder(Boolean.class, "offscreen-arrows")
        .description("Arrows around the crosshair pointing at highlighted entities you can't see on screen.").defaultValue(true).build());

    private final Setting<Integer> arrowRadius = register(Setting.builder(Integer.class, "arrow-radius")
        .description("How far from the crosshair the arrows sit.").defaultValue(60).range(25, 200).visible(arrows::get).build());

    private final Setting<Integer> thickness = register(Setting.builder(Integer.class, "thickness")
        .description("Line thickness.").defaultValue(1).range(1, 3)
        .visible(() -> showCorners.get() || show2d.get() || showWire.get()).build());

    private final Setting<Boolean> fillOn = register(Setting.builder(Boolean.class, "fill")
        .description("Tint the inside of the boxes.").defaultValue(true)
        .visible(() -> showClean.get() || showCorners.get() || show2d.get() || show3d.get()).build());

    private final Setting<Double> fillAmount = register(Setting.builder(Double.class, "fill-amount")
        .description("How strong the tint inside the boxes is.").defaultValue(0.15).range(0.05, 0.8)
        .visible(() -> fillOn.get() && (showClean.get() || showCorners.get() || show2d.get() || show3d.get())).build());

    private final Setting<Boolean> healthBar = register(Setting.builder(Boolean.class, "health-bar")
        .description("Health bar beside the box (flat styles).").defaultValue(true)
        .visible(() -> flat()).build());

    private final Setting<Boolean> info = register(Setting.builder(Boolean.class, "name-distance")
        .description("Name and distance under the box (flat styles).").defaultValue(true)
        .visible(() -> flat()).build());

    private final Setting<Integer> range = register(Setting.builder(Integer.class, "range")
        .description("Only entities this close.").defaultValue(128).range(8, 512).build());

    private final Map<Kind, PlayerTeam> teams = new HashMap<>();
    private final Map<Integer, Entity> glowing = new HashMap<>();
    /** Line of sight per entity id, refreshed every tick (two-tone). */
    private final Map<Integer, Boolean> visible = new HashMap<>();
    private Scoreboard boundTo;

    public Esp() {
        super("Esp", "Highlights entities through walls: corners, 2D/3D box, wireframe, chams or glow.", Category.RENDER);
        instance = this;
    }

    /** Shaderpacks replace entity rendering, so CHAMS shows as GLOW while one is on. */
    private boolean glowing() {
        return showGlow.get() || (chamsOn() && dev.novautils.utils.ShaderCompat.shadersActive());
    }

    /** ChamsMixin: the chams colour for this entity (ARGB), or 0 for none. */
    public static int chamsColor(Entity e) {
        Esp esp = instance;
        if (esp == null || !esp.isEnabled() || !esp.chamsOn() || dev.novautils.utils.ShaderCompat.shadersActive()) return 0;
        if (mc.player == null) return 0;
        Kind kind = esp.kindOf(e);
        if (kind == null) return 0;
        int a = Math.max(26, Math.min(255, (int) (esp.chamsOpacity.get() * 255 * esp.alphaFor(e))));
        return a << 24 | esp.rgbFor(e, kind);
    }

    /** EspGlowColorMixin: the exact outline colour for GLOW (RGB), or 0 to leave the game's. */
    public static int glowColor(Entity e) {
        Esp esp = instance;
        if (esp == null || !esp.isEnabled() || !esp.glowing() || mc.player == null) return 0;
        Kind kind = esp.kindOf(e);
        return kind == null ? 0 : 0xFF000000 | esp.rgbFor(e, kind);
    }

    /** RGB for an entity: behind-wall colour, rainbow, health or its kind's colour. */
    private int rgbFor(Entity e, Kind kind) {
        if (twoTone.get() && !visible.getOrDefault(e.getId(), true)) return behindColor.get() & 0xFFFFFF;
        if (rainbow.get()) {
            float hue = (System.currentTimeMillis() % 3600) / 3600f + e.getId() * 0.07f;
            return java.awt.Color.HSBtoRGB(hue, 0.65f, 1f) & 0xFFFFFF;
        }
        if (healthColor.get() && e instanceof LivingEntity l) return healthRgb(l.getHealth() / Math.max(1f, l.getMaxHealth()));
        return colorOf(kind) & 0xFFFFFF;
    }

    /** Opacity multiplier from distance fade and pulse, 0.2..1. */
    private float alphaFor(Entity e) {
        float a = 1f;
        if (fade.get()) {
            double d = mc.player.distanceTo(e);
            a *= (float) Math.max(0.3, 1 - Math.max(0, d - 12) / Math.max(1, range.get() - 12));
        }
        if (pulse.get()) a *= 0.6f + 0.4f * (float) (0.5 + 0.5 * Math.sin(System.currentTimeMillis() / 260.0 + e.getId()));
        return Math.max(0.2f, a);
    }

    private static int healthRgb(float f) {
        f = Math.max(0f, Math.min(1f, f));
        return (int) (255 * Math.min(1f, 2f * (1f - f))) << 16 | (int) (220 * Math.min(1f, 2f * f)) << 8 | 0x40;
    }

    public static boolean chamsTextured() {
        return instance != null && instance.showChamsTex.get();
    }

    private boolean chamsOn() {
        return showChams.get() || showChamsTex.get();
    }

    private boolean flat() {
        return showClean.get() || showCorners.get() || show2d.get();
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
        visible.clear();
        if (twoTone.get()) {
            for (Entity e : mc.level.entitiesForRendering()) {
                if (kindOf(e) != null) visible.put(e.getId(), mc.player.hasLineOfSight(e));
            }
        }
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
        if (!show3d.get() && !showWire.get()) return;
        float partial = mc.getDeltaTracker().getGameTimeDeltaPartialTick(false);
        for (Entity e : mc.level.entitiesForRendering()) {
            Kind kind = kindOf(e);
            if (kind == null) continue;
            int c = rgbFor(e, kind);
            float fa = alphaFor(e);
            AABB box = boxAt(e, partial);
            if (show3d.get()) {
                int a = (int) ((40 + 215 * (fillOn.get() ? fillAmount.get() : 0.0)) * fa);
                int color = (Math.min(255, a) << 24) | c;
                RenderUtils.world(context, (pose, buffer) -> RenderUtils.drawBox(pose, buffer, box, color));
            }
            if (showWire.get()) {
                double w = 0.008 * thickness.get();
                int color = Math.min(255, (int) (255 * fa)) << 24 | c;
                RenderUtils.world(context, (pose, buffer) -> RenderUtils.drawEdges(pose, buffer, box, color, w));
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
        g.fill(x1 - 1, y1 - 1, x2 + 1, y2 + 1, Theme.alpha(0xA0000000, ((color >>> 24) / 255f)));
        g.fill(x1, y1, x2, y2, color);
    }

    /** A filled triangle, drawn as one row of pixels at a time. */
    private static void triangle(GuiGraphicsExtractor g, double ax, double ay, double bx, double by, double cx, double cy, int color) {
        int top = (int) Math.floor(Math.min(ay, Math.min(by, cy))), bottom = (int) Math.ceil(Math.max(ay, Math.max(by, cy)));
        double[][] edges = {{ax, ay, bx, by}, {bx, by, cx, cy}, {cx, cy, ax, ay}};
        for (int y = top; y < bottom; y++) {
            double sy = y + 0.5, lo = Double.MAX_VALUE, hi = -Double.MAX_VALUE;
            for (double[] ed : edges) {
                if ((sy < ed[1]) == (sy < ed[3])) continue;
                double x = ed[0] + (sy - ed[1]) * (ed[2] - ed[0]) / (ed[3] - ed[1]);
                lo = Math.min(lo, x);
                hi = Math.max(hi, x);
            }
            if (hi > lo) g.fill((int) Math.round(lo), y, (int) Math.round(hi), y + 1, color);
        }
    }

    /** An arrow around the crosshair pointing towards an entity that is off screen. */
    private void arrow(GuiGraphicsExtractor g, Entity e, float partial, int color) {
        Vec3 p = e.getPosition(partial);
        double dx = p.x - mc.player.getX(), dz = p.z - mc.player.getZ();
        double rel = Math.toRadians(net.minecraft.util.Mth.wrapDegrees(Math.toDegrees(Math.atan2(dz, dx)) - 90 - mc.player.getYRot()));
        double sx = Math.sin(rel), sy = -Math.cos(rel);
        double cx = g.guiWidth() / 2.0 + sx * arrowRadius.get(), cy = g.guiHeight() / 2.0 + sy * arrowRadius.get();
        double px = -sy, py = sx;
        // dark outline first, then the colour
        triangle(g, cx + sx * 7.5, cy + sy * 7.5, cx - sx * 4.5 + px * 6, cy - sy * 4.5 + py * 6, cx - sx * 4.5 - px * 6, cy - sy * 4.5 - py * 6,
            Theme.alpha(0xB0000000, ((color >>> 24) / 255f)));
        triangle(g, cx + sx * 6, cy + sy * 6, cx - sx * 3.5 + px * 4.5, cy - sy * 3.5 + py * 4.5, cx - sx * 3.5 - px * 4.5, cy - sy * 3.5 - py * 4.5, color);
    }

    @Override
    public void onRenderHud(GuiGraphicsExtractor g, DeltaTracker delta) {
        if ((!flat() && !arrows.get()) || mc.level == null || mc.player == null || !ScreenSpace.begin()) return;
        float partial = delta.getGameTimeDeltaPartialTick(false);
        int t = thickness.get();
        for (Entity e : mc.level.entitiesForRendering()) {
            Kind kind = kindOf(e);
            if (kind == null) continue;
            int c = Math.min(255, (int) (255 * alphaFor(e))) << 24 | rgbFor(e, kind);
            if (arrows.get()) {
                double[] s = ScreenSpace.project(boxAt(e, partial).getCenter());
                if (s == null || s[0] < 0 || s[1] < 0 || s[0] > g.guiWidth() || s[1] > g.guiHeight()) {
                    arrow(g, e, partial, c);
                    continue;
                }
            }
            if (!flat()) continue;
            int[] r = screenRect(boxAt(e, partial));
            if (r == null || r[2] < 0 || r[3] < 0 || r[0] > g.guiWidth() || r[1] > g.guiHeight()) continue;
            int x1 = r[0], y1 = r[1], x2 = Math.max(r[2], x1 + 3), y2 = Math.max(r[3], y1 + 3);

            if (showClean.get()) drawClean(g, e, x1, y1, x2, y2, c);
            if (!show2d.get() && !showCorners.get()) continue;
            if (fillOn.get() && !showClean.get()) g.fill(x1, y1, x2, y2, Theme.alpha(c, fillAmount.get().floatValue() * 0.6f));
            if (show2d.get()) {
                line(g, x1, y1, x2, y1 + t, c);
                line(g, x1, y2 - t, x2, y2, c);
                line(g, x1, y1, x1 + t, y2, c);
                line(g, x2 - t, y1, x2, y2, c);
            }
            if (showCorners.get()) {
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
            // CLEAN already drew its health bar and name
            if (showClean.get()) continue;

            int below = y2 + 3;
            if (healthBar.get() && e instanceof LivingEntity l) {
                float f = Math.max(0f, Math.min(1f, l.getHealth() / l.getMaxHealth()));
                int bx = x1 - 5, h = y2 - y1;
                int filled = Math.round(h * f);
                g.fill(bx - 1, y1 - 1, bx + 3, y2 + 1, 0xA0000000);
                g.fill(bx, y2 - filled, bx + 2, y2, 0xFF000000 | healthRgb(f));
                int armor = l.getArmorValue();
                if (armor > 0) {
                    int w = Math.round((x2 - x1) * Math.min(1f, armor / 20f));
                    g.fill(x1 - 1, y2 + 2, x2 + 1, y2 + 5, 0xA0000000);
                    g.fill(x1, y2 + 3, x1 + w, y2 + 4, 0xFF5AA0FF);
                    below = y2 + 7;
                }
            }
            if (info.get()) {
                String text = e.getName().getString() + " " + Math.round(mc.player.distanceTo(e)) + "m";
                int tw = mc.font.width(text);
                g.text(mc.font, text, (x1 + x2) / 2 - tw / 2, below, 0xFFFFFFFF, true);
            }
        }
    }
    /** A 1px frame around the rectangle (inclusive edges). */
    private static void frame(GuiGraphicsExtractor g, int x1, int y1, int x2, int y2, int color) {
        g.fill(x1, y1, x2, y1 + 1, color);
        g.fill(x1, y2 - 1, x2, y2, color);
        g.fill(x1, y1 + 1, x1 + 1, y2 - 1, color);
        g.fill(x2 - 1, y1 + 1, x2, y2 - 1, color);
    }

    /**
     * CLEAN: a thin coloured box between a dark outer and inner rim, a soft fill that fades
     * towards the feet, an animated health bar on the left (with the hit "ghost") and the name in
     * a small dark pill above, distance under it.
     */
    private void drawClean(GuiGraphicsExtractor g, Entity e, int x1, int y1, int x2, int y2, int c) {
        float a = (c >>> 24) / 255f;
        if (fillOn.get()) g.fillGradient(x1, y1, x2, y2, Theme.alpha(c, Math.min(1f, fillAmount.get().floatValue() * 1.4f)), Theme.alpha(c, 0.02f));
        int dark = Theme.alpha(0xD0000000, a);
        frame(g, x1 - 1, y1 - 1, x2 + 1, y2 + 1, dark);
        frame(g, x1 + 1, y1 + 1, x2 - 1, y2 - 1, dark);
        frame(g, x1, y1, x2, y2, c);

        if (healthBar.get() && e instanceof LivingEntity l) {
            float[] hp = dev.novautils.utils.HealthDisplay.animated(l);
            float now = Math.max(0f, Math.min(1f, l.getHealth() / Math.max(1f, l.getMaxHealth())));
            int bx = x1 - 6, h = y2 - y1;
            g.fill(bx - 1, y1 - 1, bx + 3, y2 + 1, dark);
            g.fill(bx, y1, bx + 2, y2, Theme.alpha(0xFF1C1E25, a));
            int fillH = Math.round(h * hp[0]), ghost = Math.round(h * hp[1]);
            if (ghost > fillH) g.fill(bx, y2 - ghost, bx + 2, y2 - fillH, Theme.alpha(0xFFF2F2F2, 0.85f * a));
            int hc = dev.novautils.utils.HealthDisplay.color(now);
            if (fillH > 0) g.fillGradient(bx, y2 - fillH, bx + 2, y2, Theme.alpha(Theme.mix(hc, 0xFFFFFFFF, 0.3f), a), Theme.alpha(hc, a));
        }

        if (info.get()) {
            String name = e.getName().getString();
            int tw = mc.font.width(name), cx = (x1 + x2) / 2;
            int px = cx - tw / 2 - 4, py = y1 - 14;
            Theme.rounded(g, px, py, tw + 8, 11, 3, Theme.alpha(0xC00D0E12, a));
            g.fill(px + 2, py + 10, px + tw + 6, py + 11, Theme.alpha(c, 0.9f));
            g.text(mc.font, name, px + 4, py + 2, Theme.alpha(0xFFFFFFFF, a), false);
            String d = Math.round(mc.player.distanceTo(e)) + "m";
            g.text(mc.font, d, cx - mc.font.width(d) / 2, y2 + 3, Theme.alpha(0xFFB4B8C4, a), true);
        }
    }
}
