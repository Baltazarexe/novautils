package dev.novautils.modules.render;

import dev.novautils.gui.Theme;
import dev.novautils.modules.Category;
import dev.novautils.modules.Module;
import dev.novautils.settings.Setting;
import dev.novautils.utils.ScreenSpace;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * A small health bar floating over mobs and/or players, green to red, with the hearts left. Drawn
 * on the HUD at each entity's projected position, so it also works with shaderpacks.
 */
public class HealthBars extends Module {
    public enum Target { MOBS, PLAYERS, BOTH }

    private final Setting<Target> target = register(Setting.builder(Target.class, "show-on")
        .description("Mobs, players, or both.").defaultValue(Target.MOBS).build());

    private final Setting<Boolean> hostileOnly = register(Setting.builder(Boolean.class, "hostile-only")
        .description("Only hostile mobs (zombies, skeletons...), not animals.").defaultValue(false)
        .visible(() -> target.get() != Target.PLAYERS).build());

    private final Setting<Boolean> damagedOnly = register(Setting.builder(Boolean.class, "damaged-only")
        .description("Hide the bar while the entity has full health.").defaultValue(false).build());

    private final Setting<Boolean> numbers = register(Setting.builder(Boolean.class, "numbers")
        .description("Write the health over the bar.").defaultValue(true).build());

    private final Setting<Integer> range = register(Setting.builder(Integer.class, "range")
        .description("Only entities this close.").defaultValue(32).range(4, 128).build());

    private final Setting<Double> scale = register(Setting.builder(Double.class, "size")
        .description("Bar size.").defaultValue(1.0).range(0.5, 2.0).build());

    public HealthBars() {
        super("HealthBars", "Health bars over mobs and/or players.", Category.RENDER);
    }

    private boolean wanted(Entity e) {
        if (!(e instanceof LivingEntity l) || e == mc.player || !e.isAlive() || e instanceof ArmorStand || e.isInvisible()) return false;
        boolean player = e instanceof Player;
        if (player && target.get() == Target.MOBS) return false;
        if (!player && target.get() == Target.PLAYERS) return false;
        if (!player && hostileOnly.get() && !(e instanceof Enemy)) return false;
        if (damagedOnly.get() && l.getHealth() >= l.getMaxHealth()) return false;
        return mc.player.distanceTo(e) <= range.get();
    }

    private static int color(float f) {
        int r = (int) (255 * Math.min(1f, 2f * (1f - f)));
        int g = (int) (220 * Math.min(1f, 2f * f));
        return 0xFF000000 | r << 16 | g << 8 | 0x40;
    }

    @Override
    public void onRenderHud(GuiGraphics g, float delta) {
        if (mc.player == null || mc.level == null || !ScreenSpace.begin()) return;
        float partial = delta;
        List<LivingEntity> list = new ArrayList<>();
        for (Entity e : mc.level.entitiesForRendering()) if (wanted(e)) list.add((LivingEntity) e);
        // far ones first so near bars draw on top
        list.sort(Comparator.comparingDouble(e -> -mc.player.distanceToSqr(e)));

        for (LivingEntity e : list) {
            Vec3 top = e.getPosition(partial).add(0, e.getBbHeight() + 0.45, 0);
            double[] p = ScreenSpace.project(top);
            if (p == null) continue;
            double dist = mc.player.distanceTo(e);
            float size = (float) (scale.get() * Math.max(0.55, Math.min(1.0, 8.0 / Math.max(1.0, dist))));
            int w = Math.round(40 * size), h = Math.max(2, Math.round(4 * size));
            int x = (int) p[0] - w / 2, y = (int) p[1];

            float hp = e.getHealth() + e.getAbsorptionAmount();
            float f = Math.max(0f, Math.min(1f, e.getHealth() / e.getMaxHealth()));
            Theme.rounded(g, x - 1, y - 1, w + 2, h + 2, 1, 0xB0000000);
            if (f > 0) g.fill(x, y, x + Math.max(1, Math.round(w * f)), y + h, color(f));
            if (e.getAbsorptionAmount() > 0) g.fill(x, y, x + Math.min(w, Math.round(w * e.getAbsorptionAmount() / e.getMaxHealth())), y + 1, 0xFFFFD84A);
            if (numbers.get() && size > 0.6f) {
                String t = String.format(java.util.Locale.ROOT, "%.1f", hp);
                g.drawString(mc.font, t, (int) p[0] - mc.font.width(t) / 2, y - 10, 0xFFFFFFFF, true);
            }
        }
    }
}
