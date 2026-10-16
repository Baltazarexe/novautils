package dev.novautils.modules.render;

import dev.novautils.gui.Theme;
import dev.novautils.modules.Category;
import dev.novautils.modules.Module;
import dev.novautils.settings.Setting;
import dev.novautils.utils.Friends;
import dev.novautils.utils.HealthDisplay;
import dev.novautils.utils.ScreenSpace;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientboundEntityEventPacket;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

/**
 * Name tags drawn on the HUD at each entity's position, in three looks:
 * <ul>
 *   <li>CARD - a rounded dark card with a coloured edge, the name, ping/distance on the right and
 *       an animated health bar underneath.</li>
 *   <li>CLASSIC - one line of text on a dark strip (name, health, ping, distance).</li>
 *   <li>MINIMAL - just the text with a shadow.</li>
 * </ul>
 * Replaces the vanilla tag while on (NametagMixin hides it). Works the same on every version and
 * with shaderpacks, since nothing is drawn in the world.
 */
public class Nametags extends Module {
    public enum Style { CARD, CLASSIC, MINIMAL }
    public enum Health { BAR_AND_NUMBER, NUMBER, BAR, OFF }
    public enum NameColor { TEAM, RELATION, WHITE }

    private final Setting<Style> style = register(Setting.builder(Style.class, "style")
        .description("CARD: rounded card with a health bar. CLASSIC: one line on a dark strip. MINIMAL: text only.")
        .defaultValue(Style.CARD).build());

    private final Setting<Boolean> players = register(Setting.builder(Boolean.class, "players")
        .description("Tags on players.").defaultValue(true).build());
    private final Setting<Boolean> mobs = register(Setting.builder(Boolean.class, "mobs")
        .description("Tags on mobs.").defaultValue(false).build());
    private final Setting<Boolean> items = register(Setting.builder(Boolean.class, "items")
        .description("Tags on dropped items (name and count).").defaultValue(false).build());

    private final Setting<Health> health = register(Setting.builder(Health.class, "health")
        .description("Health as a bar, a number, both, or not at all.").defaultValue(Health.BAR_AND_NUMBER).build());
    private final Setting<NameColor> nameColor = register(Setting.builder(NameColor.class, "name-color")
        .description("TEAM: the colour the server gives. RELATION: friends cyan, players red, mobs by type. WHITE.")
        .defaultValue(NameColor.TEAM).build());
    private final Setting<Boolean> ping = register(Setting.builder(Boolean.class, "ping")
        .description("Player ping, green / yellow / red.").defaultValue(true).build());
    private final Setting<Boolean> distance = register(Setting.builder(Boolean.class, "distance")
        .description("Distance in blocks.").defaultValue(true).build());
    private final Setting<Boolean> pops = register(Setting.builder(Boolean.class, "totem-pops")
        .description("How many totems a player has popped.").defaultValue(true).build());
    private final Setting<Boolean> heldItem = register(Setting.builder(Boolean.class, "held-item")
        .description("Small icon of the item in their hand.").defaultValue(true).build());

    private final Setting<Double> background = register(Setting.builder(Double.class, "background")
        .description("How dark the card / strip is.").defaultValue(0.75).range(0.0, 1.0)
        .visible(() -> style.get() != Style.MINIMAL).build());
    private final Setting<Double> scale = register(Setting.builder(Double.class, "scale")
        .description("Tag size.").defaultValue(1.0).range(0.5, 2.0).build());
    private final Setting<Boolean> shrink = register(Setting.builder(Boolean.class, "shrink-with-distance")
        .description("Smaller tags for far entities, like real name tags.").defaultValue(true).build());
    private final Setting<Boolean> throughWalls = register(Setting.builder(Boolean.class, "through-walls")
        .description("Also tag entities you can't see.").defaultValue(true).build());
    private final Setting<Integer> maxDistance = register(Setting.builder(Integer.class, "max-distance")
        .description("Don't tag things further than this.").defaultValue(128).range(16, 512).build());

    private final Map<UUID, Integer> popCounts = new HashMap<>();
    /** Line of sight per entity, worked out once per tick instead of every frame. */
    private final Map<Integer, Boolean> visible = new HashMap<>();

    public Nametags() {
        super("Nametags", "Better name tags: card style, animated health bar, ping, distance, totem pops.", Category.RENDER);
    }

    /** Whether vanilla's own tag should be hidden for this entity (NametagMixin). */
    public boolean replaces(Entity e) {
        if (e instanceof Player) return players.get();
        return e instanceof LivingEntity && mobs.get();
    }

    @Override
    public void onDisable() {
        popCounts.clear();
    }

    @Override
    public boolean onReceivePacket(Packet<?> packet) {
        // entity event 35 = totem of undying used
        if (packet instanceof ClientboundEntityEventPacket ev && ev.getEventId() == 35 && mc.level != null) {
            Entity e = ev.getEntity(mc.level);
            if (e instanceof Player p) popCounts.merge(p.getUUID(), 1, Integer::sum);
        }
        return false;
    }

    @Override
    public void onTick() {
        if (mc.level == null) return;
        visible.clear();
        // a player that died starts from zero again
        for (Player p : mc.level.players()) if (p.isDeadOrDying()) popCounts.remove(p.getUUID());
    }

    /** HealthBars skips entities whose tag already shows their health. */
    public boolean showsHealth(Entity e) {
        return isEnabled() && e instanceof LivingEntity && health.get() != Health.OFF && mc.player != null && wanted(e);
    }

    private boolean wanted(Entity e) {
        if (e == mc.player || e.isInvisible() || e instanceof ArmorStand) return false;
        if (mc.player.distanceTo(e) > maxDistance.get()) return false;
        if (!throughWalls.get() && !visible.computeIfAbsent(e.getId(), id -> mc.player.hasLineOfSight(e))) return false;
        if (e instanceof Player) return players.get();
        if (e instanceof ItemEntity) return items.get();
        return e instanceof LivingEntity && mobs.get();
    }

    private int relationColor(Entity e) {
        if (e instanceof Player p) return Friends.isFriend(p) ? 0xFF55E6FF : 0xFFFF6B6B;
        if (e instanceof Enemy) return 0xFFFFA040;
        return 0xFF66E07A;
    }

    private Component name(Entity e) {
        return switch (nameColor.get()) {
            case TEAM -> Friends.isFriend(e) ? Component.literal(e.getName().getString()).withColor(0x55E6FF) : e.getDisplayName();
            case RELATION -> Component.literal(e.getName().getString()).withColor(relationColor(e) & 0xFFFFFF);
            case WHITE -> Component.literal(e.getName().getString());
        };
    }

    private static int pingColor(int ms) {
        return ms < 80 ? 0xFF66E07A : ms < 160 ? 0xFFFFD050 : 0xFFFF5A5A;
    }

    /** Small grey info: "42ms 7m" (ping coloured), plus totem pops in orange. */
    private List<Object[]> info(Entity e) {
        List<Object[]> out = new ArrayList<>();
        if (e instanceof Player p) {
            int popped = popCounts.getOrDefault(p.getUUID(), 0);
            if (pops.get() && popped > 0) out.add(new Object[]{"-" + popped, 0xFFFFAA00});
            if (ping.get() && mc.getConnection() != null) {
                var pi = mc.getConnection().getPlayerInfo(p.getUUID());
                if (pi != null) out.add(new Object[]{pi.getLatency() + "ms", pingColor(pi.getLatency())});
            }
        }
        if (distance.get()) out.add(new Object[]{Math.round(mc.player.distanceTo(e)) + "m", 0xFF9196A4});
        return out;
    }

    private static int width(List<Object[]> parts) {
        int w = 0;
        for (Object[] p : parts) w += mc.font.width((String) p[0]) + 4;
        return Math.max(0, w - 4);
    }

    private static void drawInfo(GuiGraphicsExtractor g, List<Object[]> parts, int x, int y, boolean shadow) {
        for (Object[] p : parts) {
            g.text(mc.font, (String) p[0], x, y, (int) p[1], shadow);
            x += mc.font.width((String) p[0]) + 4;
        }
    }

    @Override
    public void onRenderHud(GuiGraphicsExtractor g, DeltaTracker delta) {
        if (mc.level == null || mc.player == null || !ScreenSpace.begin()) return;
        float partial = delta.getGameTimeDeltaPartialTick(false);
        List<Entity> list = new ArrayList<>();
        for (Entity e : mc.level.entitiesForRendering()) if (wanted(e)) list.add(e);
        // far first, so near tags end up on top
        list.sort(Comparator.comparingDouble(e -> -mc.player.distanceToSqr(e)));

        for (Entity e : list) {
            Vec3 top = e.getPosition(partial).add(0, e.getBbHeight() + 0.35, 0);
            double[] p = ScreenSpace.project(top);
            if (p == null || p[0] < -150 || p[1] < -60 || p[0] > g.guiWidth() + 150 || p[1] > g.guiHeight() + 60) continue;
            float dist = mc.player.distanceTo(e);
            float s = (float) (scale.get() * (shrink.get() ? Math.max(0.5, Math.min(1.0, 9.0 / Math.max(1.0, dist))) : 1.0));

            g.pose().pushMatrix();
            g.pose().translate((float) p[0], (float) p[1]);
            g.pose().scale(s, s);
            if (e instanceof ItemEntity item) drawItem(g, item);
            else switch (style.get()) {
                case CARD -> drawCard(g, e);
                case CLASSIC -> drawClassic(g, e);
                case MINIMAL -> drawMinimal(g, e);
            }
            g.pose().popMatrix();
        }
    }

    private void drawItem(GuiGraphicsExtractor g, ItemEntity item) {
        ItemStack stack = item.getItem();
        String t = stack.getHoverName().getString() + (stack.getCount() > 1 ? " x" + stack.getCount() : "");
        int w = mc.font.width(t);
        if (style.get() != Style.MINIMAL) Theme.rounded(g, -w / 2 - 3, -11, w + 6, 11, 3, Theme.alpha(0xFF101216, background.get().floatValue()));
        g.text(mc.font, t, -w / 2, -9, 0xFFE8EAF0, true);
    }

    private String hpText(LivingEntity l) {
        return String.format(Locale.ROOT, "%.1f", l.getHealth() + l.getAbsorptionAmount());
    }

    /**
     * Card: a translucent rounded box with a hairline border. Name centred on top, a smaller
     * second line (health, ping, distance) under it, a thin rounded health bar at the bottom, and
     * the held item just outside on the left. Anchored with its bottom edge at 0.
     */
    private void drawCard(GuiGraphicsExtractor g, Entity e) {
        LivingEntity l = e instanceof LivingEntity le ? le : null;
        Component n = name(e);
        List<Object[]> parts = info(e);
        boolean bar = l != null && (health.get() == Health.BAR || health.get() == Health.BAR_AND_NUMBER);
        boolean num = l != null && (health.get() == Health.NUMBER || health.get() == Health.BAR_AND_NUMBER);
        if (num) parts.add(0, new Object[]{"❤ " + hpText(l), HealthDisplay.color(l.getHealth() / Math.max(1f, l.getMaxHealth()))});
        ItemStack held = heldItem.get() && l != null ? l.getMainHandItem() : ItemStack.EMPTY;

        final float small = 0.75f;
        int nameW = mc.font.width(n);
        int infoW = Math.round(width(parts) * small);
        int w = Math.max(56, Math.max(nameW, infoW) + 14);
        int h = 4 + 9 + (infoW > 0 ? 8 : 0) + (bar ? 5 : 2);
        int x = -w / 2, y = -h;

        float bg = background.get().floatValue();
        Theme.softShadow(g, x, y, w, h, 4, 0.3f * bg);
        Theme.rounded(g, x, y, w, h, 4, Theme.alpha(0xFF0D0E12, bg * 0.85f));
        // hairline border, and a short accent under the name in the relation colour
        Theme.border(g, x, y, w, h, Theme.alpha(0xFFFFFFFF, 0.08f + 0.06f * bg));
        int accent = relationColor(e);
        g.fill(-Math.min(nameW, w - 14) / 2, y + 13, Math.min(nameW, w - 14) / 2, y + 14, Theme.alpha(accent, 0.55f));

        g.text(mc.font, n, -nameW / 2, y + 3, 0xFFFFFFFF, false);

        if (infoW > 0) {
            g.pose().pushMatrix();
            g.pose().translate(-infoW / 2f, y + 15.5f);
            g.pose().scale(small, small);
            drawInfo(g, parts, 0, 0, false);
            g.pose().popMatrix();
        }
        if (bar) HealthDisplay.bar(g, x + 4, y + h - 4, w - 8, 2, l, false, 1f);

        if (!held.isEmpty()) {
            g.pose().pushMatrix();
            g.pose().translate(x - 15, y + h / 2f - 6.5f);
            g.pose().scale(0.8f, 0.8f);
            g.item(held, 0, 0);
            g.pose().popMatrix();
        }
    }

    /** Classic: one line on a strip, with a thin bar under it. */
    private void drawClassic(GuiGraphicsExtractor g, Entity e) {
        LivingEntity l = e instanceof LivingEntity le ? le : null;
        Component n = name(e);
        List<Object[]> parts = info(e);
        boolean num = l != null && health.get() != Health.BAR && health.get() != Health.OFF;
        boolean bar = l != null && (health.get() == Health.BAR || health.get() == Health.BAR_AND_NUMBER);
        if (num) parts.add(0, new Object[]{hpText(l), HealthDisplay.color(l.getHealth() / Math.max(1f, l.getMaxHealth()))});
        int nameW = mc.font.width(n), infoW = width(parts);
        int w = nameW + (infoW > 0 ? infoW + 4 : 0) + 6;
        int x = -w / 2, y = -12;
        g.fill(x, y, x + w, y + 11, Theme.alpha(0xFF000000, background.get().floatValue() * 0.75f));
        g.text(mc.font, n, x + 3, y + 2, 0xFFFFFFFF, true);
        if (infoW > 0) drawInfo(g, parts, x + 3 + nameW + 4, y + 2, true);
        if (bar) HealthDisplay.bar(g, x, y + 11, w, 1, l, false, 1f);
    }

    private void drawMinimal(GuiGraphicsExtractor g, Entity e) {
        LivingEntity l = e instanceof LivingEntity le ? le : null;
        Component n = name(e);
        List<Object[]> parts = info(e);
        if (l != null && health.get() != Health.OFF) parts.add(0, new Object[]{hpText(l), HealthDisplay.color(l.getHealth() / Math.max(1f, l.getMaxHealth()))});
        int nameW = mc.font.width(n), infoW = width(parts);
        int w = nameW + (infoW > 0 ? infoW + 4 : 0);
        g.text(mc.font, n, -w / 2, -10, 0xFFFFFFFF, true);
        if (infoW > 0) drawInfo(g, parts, -w / 2 + nameW + 4, -10, true);
    }
}
