package dev.novautils.modules.render;

import dev.novautils.modules.Category;
import dev.novautils.modules.Module;
import dev.novautils.modules.ModuleManager;
import dev.novautils.settings.Setting;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.player.Player;

/**
 * A custom crosshair in place of the vanilla one (CrosshairMixin hides it): cross, dot, circle,
 * T or cross with a dot, any size, gap, thickness and colour, a dark outline so it reads on any
 * background, another colour while a creature is under it, and an optional attack-cooldown bar.
 */
public class Crosshair extends Module {
    public enum Style { CROSS, CROSS_DOT, DOT, CIRCLE, T }

    private final Setting<Style> style = register(Setting.builder(Style.class, "style")
        .description("CROSS, CROSS_DOT, DOT, CIRCLE or T.").defaultValue(Style.CROSS).build());
    private final Setting<Integer> size = register(Setting.builder(Integer.class, "size")
        .description("Length of each arm (or the circle radius).").defaultValue(5).range(1, 20).build());
    private final Setting<Integer> gap = register(Setting.builder(Integer.class, "gap")
        .description("Empty space in the middle.").defaultValue(2).range(0, 12).build());
    private final Setting<Integer> thickness = register(Setting.builder(Integer.class, "thickness")
        .description("Line thickness.").defaultValue(1).range(1, 4).build());
    private final Setting<Integer> color = register(Setting.builder(Integer.class, "color")
        .description("Crosshair colour.").defaultValue(0xFFFFFFFF).asColor().build());
    private final Setting<Boolean> outline = register(Setting.builder(Boolean.class, "outline")
        .description("Thin dark edge so it shows on bright backgrounds.").defaultValue(true).build());
    private final Setting<Boolean> targetColor = register(Setting.builder(Boolean.class, "target-color")
        .description("Change colour while a creature is under the crosshair.").defaultValue(true).build());
    private final Setting<Integer> hostileColor = register(Setting.builder(Integer.class, "hostile-color")
        .description("Colour on hostile mobs and players.").defaultValue(0xFFFF5A5A).asColor().visible(targetColor::get).build());
    private final Setting<Integer> friendlyColor = register(Setting.builder(Integer.class, "friendly-color")
        .description("Colour on animals and other creatures.").defaultValue(0xFF66E07A).asColor().visible(targetColor::get).build());
    private final Setting<Boolean> dynamicGap = register(Setting.builder(Boolean.class, "dynamic-gap")
        .description("The gap opens a little while you move.").defaultValue(false).build());
    private final Setting<Boolean> cooldown = register(Setting.builder(Boolean.class, "attack-cooldown")
        .description("Small bar under the crosshair while your attack recharges.").defaultValue(true).build());

    private float spread;

    public Crosshair() {
        super("Crosshair", "Custom crosshair: style, size, gap, colour, target colour.", Category.RENDER);
    }

    /** CrosshairMixin: hide the vanilla crosshair while this is on. */
    public static boolean replacesVanilla() {
        return dev.novautils.NovaUtils.modulesReady && ModuleManager.get().isActive(Crosshair.class);
    }

    private int currentColor() {
        if (targetColor.get() && mc.crosshairPickEntity instanceof LivingEntity e && e.isAlive()) {
            return e instanceof Enemy || e instanceof Player ? hostileColor.get() : friendlyColor.get();
        }
        return color.get();
    }

    /** A rectangle with an optional 1px dark rim. */
    private void bar(GuiGraphics g, int x1, int y1, int x2, int y2, int c) {
        if (outline.get()) g.fill(x1 - 1, y1 - 1, x2 + 1, y2 + 1, 0xA0000000 & (c | 0x00FFFFFF));
        g.fill(x1, y1, x2, y2, c);
    }

    @Override
    public void onRenderHud(GuiGraphics g, float delta) {
        if (mc.player == null || !mc.options.getCameraType().isFirstPerson()) return;
        int cx = g.guiWidth() / 2, cy = g.guiHeight() / 2;
        int c = currentColor();
        int t = thickness.get(), len = size.get();

        float target = 0;
        if (dynamicGap.get()) {
            var v = mc.player.getDeltaMovement();
            target = (float) Math.min(6, Math.sqrt(v.x * v.x + v.z * v.z) * 14) + (mc.player.onGround() ? 0 : 2);
        }
        spread += (target - spread) * 0.3f;
        int gp = gap.get() + Math.round(spread);
        int lo = -(t / 2), hi = lo + t; // thickness centred on the middle pixel

        switch (style.get()) {
            case DOT -> bar(g, cx + lo, cy + lo, cx + hi + (t == 1 ? 1 : 0), cy + hi + (t == 1 ? 1 : 0), c);
            case CIRCLE -> {
                int r = Math.max(2, len + gp);
                for (int a = 0; a < 360; a += 4) {
                    double rad = Math.toRadians(a);
                    int px = cx + (int) Math.round(Math.cos(rad) * r), py = cy + (int) Math.round(Math.sin(rad) * r);
                    if (outline.get()) g.fill(px - 1, py - 1, px + t + 1, py + t + 1, 0x60000000);
                }
                for (int a = 0; a < 360; a += 2) {
                    double rad = Math.toRadians(a);
                    int px = cx + (int) Math.round(Math.cos(rad) * r), py = cy + (int) Math.round(Math.sin(rad) * r);
                    g.fill(px, py, px + t, py + t, c);
                }
                g.fill(cx, cy, cx + 1, cy + 1, c);
            }
            default -> {
                // left, right, bottom and (except T) top arms
                bar(g, cx - gp - len, cy + lo, cx - gp, cy + hi, c);
                bar(g, cx + gp + 1, cy + lo, cx + gp + 1 + len, cy + hi, c);
                bar(g, cx + lo, cy + gp + 1, cx + hi, cy + gp + 1 + len, c);
                if (style.get() != Style.T) bar(g, cx + lo, cy - gp - len, cx + hi, cy - gp, c);
                if (style.get() == Style.CROSS_DOT) bar(g, cx, cy, cx + 1, cy + 1, c);
            }
        }

        if (cooldown.get()) {
            float s = mc.player.getAttackStrengthScale(0f);
            if (s < 1f) {
                int w = 16, by = cy + Math.max(len + gp, 4) + 5;
                g.fill(cx - w / 2 - 1, by - 1, cx + w / 2 + 1, by + 2, 0x90000000);
                g.fill(cx - w / 2, by, cx - w / 2 + Math.round(w * s), by + 1, c);
            }
        }
    }
}
