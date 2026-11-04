package dev.novautils.modules.render;

import dev.novautils.gui.Theme;
import dev.novautils.modules.Category;
import dev.novautils.modules.Module;
import dev.novautils.settings.Setting;
import dev.novautils.utils.ScreenSpace;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;

/**
 * Lines from the crosshair to nearby entities so you always know where they are. Drawn on the HUD
 * layer as smooth lines of a fixed width: they never break up into dots far away, nothing in the
 * world (ice, water, glass, your hand) covers them, and they work the same with a shaderpack.
 */
public class Tracers extends Module {
    public enum Target { BODY, HEAD, FEET }

    private final Setting<Boolean> players = register(Setting.builder(Boolean.class, "players")
        .description("Trace to players.").defaultValue(true).build());
    private final Setting<Integer> playerColor = register(Setting.builder(Integer.class, "players-color")
        .description("Colour for players.").defaultValue(0xFFFF5A5A).asColor().visible(players::get).build());
    private final Setting<Integer> friendColor = register(Setting.builder(Integer.class, "friends-color")
        .description("Colour for friends.").defaultValue(0xFF55E6FF).asColor().visible(players::get).build());
    private final Setting<Boolean> hostiles = register(Setting.builder(Boolean.class, "hostile-mobs")
        .description("Trace to hostile mobs.").defaultValue(true).build());
    private final Setting<Integer> hostileColor = register(Setting.builder(Integer.class, "hostile-mobs-color")
        .description("Colour for hostile mobs.").defaultValue(0xFFFFA040).asColor().visible(hostiles::get).build());
    private final Setting<Boolean> passives = register(Setting.builder(Boolean.class, "passive-mobs")
        .description("Trace to other mobs.").defaultValue(false).build());
    private final Setting<Integer> passiveColor = register(Setting.builder(Integer.class, "passive-mobs-color")
        .description("Colour for other mobs.").defaultValue(0xFF66E07A).asColor().visible(passives::get).build());

    private final Setting<Target> target = register(Setting.builder(Target.class, "aim-at")
        .description("Which part of the entity the line goes to.").defaultValue(Target.BODY).build());
    private final Setting<Double> width = register(Setting.builder(Double.class, "width")
        .description("Line width.").defaultValue(1.0).range(0.5, 3.0).build());
    private final Setting<Boolean> outline = register(Setting.builder(Boolean.class, "outline")
        .description("A thin dark edge around the line so it reads on snow, ice and sky.").defaultValue(true).build());
    private final Setting<Boolean> fade = register(Setting.builder(Boolean.class, "distance-fade")
        .description("Far entities get a fainter line.").defaultValue(true).build());
    private final Setting<Boolean> dot = register(Setting.builder(Boolean.class, "end-dot")
        .description("A small dot where the line ends.").defaultValue(true).build());
    private final Setting<Double> range = register(Setting.builder(Double.class, "range")
        .description("Max distance to trace.").defaultValue(96.0).range(8.0, 256.0).build());

    public Tracers() {
        super("Tracers", "Lines from your crosshair to nearby entities.", Category.RENDER);
    }

    private int colorOf(Entity e) {
        if (e instanceof Player) {
            if (!players.get()) return 0;
            return dev.novautils.utils.Friends.isFriend(e) ? friendColor.get() : playerColor.get();
        }
        if (e instanceof Monster) return hostiles.get() ? hostileColor.get() : 0;
        return passives.get() ? passiveColor.get() : 0;
    }

    @Override
    public void onRenderHud(GuiGraphics g, float delta) {
        if (mc.level == null || mc.player == null || !ScreenSpace.begin()) return;
        float partial = delta;
        double rangeSq = range.get() * range.get();
        double cx = g.guiWidth() / 2.0, cy = g.guiHeight() / 2.0;
        float w = width.get().floatValue();

        for (Entity e : mc.level.entitiesForRendering()) {
            if (e == mc.player || !(e instanceof LivingEntity living) || !living.isAlive()) continue;
            double distSq = mc.player.distanceToSqr(e);
            if (distSq > rangeSq) continue;
            int color = colorOf(e);
            if (color == 0) continue;

            Vec3 base = e.getPosition(partial);
            double h = e.getBbHeight();
            Vec3 point = switch (target.get()) {
                case HEAD -> base.add(0, h * 0.9, 0);
                case FEET -> base.add(0, 0.05, 0);
                default -> base.add(0, h / 2, 0);
            };
            double[] s = ScreenSpace.projectOrEdge(point);
            float alpha = fade.get() ? (float) Math.max(0.35, 1 - Math.sqrt(distSq) / range.get() * 0.65) : 1f;
            int c = Theme.alpha(color | 0xFF000000, alpha);

            if (outline.get()) line(g, cx, cy, s[0], s[1], w + 1f, Theme.alpha(0xB0000000, alpha));
            line(g, cx, cy, s[0], s[1], w, c);
            if (dot.get() && s[0] >= 0 && s[1] >= 0 && s[0] <= g.guiWidth() && s[1] <= g.guiHeight()) {
                int r = Math.round(w + 1.5f);
                int x = (int) Math.round(s[0]), y = (int) Math.round(s[1]);
                if (outline.get()) Theme.rounded(g, x - r - 1, y - r - 1, 2 * r + 2, 2 * r + 2, r + 1, Theme.alpha(0xB0000000, alpha));
                Theme.rounded(g, x - r, y - r, 2 * r, 2 * r, r, c);
            }
        }
    }

    /** A straight line of the given width: one thin rectangle turned towards the end point. */
    private static void line(GuiGraphics g, double x1, double y1, double x2, double y2, float w, int color) {
        double dx = x2 - x1, dy = y2 - y1;
        float len = (float) Math.sqrt(dx * dx + dy * dy);
        if (len < 0.5f) return;
        g.pose().pushPose();
        g.pose().translate((float) x1, (float) y1, 0f);
        g.pose().mulPose(com.mojang.math.Axis.ZP.rotation((float) Math.atan2(dy, dx)));
        g.pose().translate(0f, -w / 2f, 0f);
        g.pose().scale(len / 1000f, w / 10f, 1f);
        g.fill(0, 0, 1000, 10, color);
        g.pose().popPose();
    }
}
