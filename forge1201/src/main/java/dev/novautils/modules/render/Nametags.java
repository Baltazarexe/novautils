package dev.novautils.modules.render;

import dev.novautils.modules.Category;
import dev.novautils.modules.Module;
import dev.novautils.render.RenderContext;
import dev.novautils.settings.Setting;
import dev.novautils.utils.RenderUtils;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientboundEntityEventPacket;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Bigger, more useful name tags that stay readable at any distance and show through walls:
 * health (coloured), ping, distance, how many totems a player has popped, and what they hold.
 * Replaces the vanilla tag while on (NametagMixin hides it).
 */
public class Nametags extends Module {
    private final Setting<Boolean> players = register(Setting.builder(Boolean.class, "players")
        .description("Tags on players.").defaultValue(true).build());
    private final Setting<Boolean> mobs = register(Setting.builder(Boolean.class, "mobs")
        .description("Tags on mobs.").defaultValue(false).build());
    private final Setting<Boolean> items = register(Setting.builder(Boolean.class, "items")
        .description("Tags on dropped items (name and count).").defaultValue(false).build());

    private final Setting<Boolean> health = register(Setting.builder(Boolean.class, "health")
        .description("Show health (absorption included).").defaultValue(true).build());
    private final Setting<Boolean> ping = register(Setting.builder(Boolean.class, "ping")
        .description("Show player ping.").defaultValue(true).build());
    private final Setting<Boolean> distance = register(Setting.builder(Boolean.class, "distance")
        .description("Show distance in blocks.").defaultValue(true).build());
    private final Setting<Boolean> pops = register(Setting.builder(Boolean.class, "totem-pops")
        .description("Show how many totems a player has popped.").defaultValue(true).build());
    private final Setting<Boolean> heldItem = register(Setting.builder(Boolean.class, "held-item")
        .description("Second line with the item in their main hand.").defaultValue(false).build());

    private final Setting<Double> scale = register(Setting.builder(Double.class, "scale")
        .description("Tag size.").defaultValue(1.0).range(0.5, 3.0).build());
    private final Setting<Boolean> throughWalls = register(Setting.builder(Boolean.class, "through-walls")
        .description("Draw tags through blocks.").defaultValue(true).build());
    private final Setting<Integer> maxDistance = register(Setting.builder(Integer.class, "max-distance")
        .description("Don't tag things further than this.").defaultValue(128).range(16, 512).build());

    private final Map<UUID, Integer> popCounts = new HashMap<>();

    public Nametags() {
        super("Nametags", "Better name tags: health, ping, distance, totem pops.", Category.RENDER);
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
        // a player that died starts from zero again
        for (Player p : mc.level.players()) if (p.isDeadOrDying()) popCounts.remove(p.getUUID());
    }

    private static int healthColor(float hp, float max) {
        float f = hp / Math.max(1f, max);
        if (f > 0.66f) return 0x55FF55;
        if (f > 0.33f) return 0xFFFF55;
        return 0xFF5555;
    }

    private MutableComponent tagFor(Entity e) {
        MutableComponent c = Component.empty();
        if (e instanceof ItemEntity item) {
            var stack = item.getItem();
            c.append(stack.getHoverName().copy());
            if (stack.getCount() > 1) c.append(Component.literal(" x" + stack.getCount()).withStyle(s_ -> s_.withColor(0xAAAAAA)));
            return c;
        }

        if (dev.novautils.utils.Friends.isFriend(e)) c.append(Component.literal(e.getName().getString()).withStyle(s_ -> s_.withColor(0x55FFFF)));
        else c.append(e.getDisplayName().copy());
        if (e instanceof LivingEntity living && health.get()) {
            float hp = living.getHealth() + living.getAbsorptionAmount();
            c.append(Component.literal(String.format(" %.1f", hp)).withStyle(s_ -> s_.withColor(healthColor(hp, living.getMaxHealth()))));
        }
        if (e instanceof Player p) {
            if (ping.get() && mc.getConnection() != null) {
                var info = mc.getConnection().getPlayerInfo(p.getUUID());
                if (info != null) c.append(Component.literal(" " + info.getLatency() + "ms").withStyle(s_ -> s_.withColor(0xAAAAAA)));
            }
            int popped = popCounts.getOrDefault(p.getUUID(), 0);
            if (pops.get() && popped > 0) c.append(Component.literal(" -" + popped).withStyle(s_ -> s_.withColor(0xFFAA00)));
        }
        if (distance.get()) {
            c.append(Component.literal(" " + Math.round(mc.player.distanceTo(e)) + "m").withStyle(s_ -> s_.withColor(0x7F7F7F)));
        }
        return c;
    }

    private boolean wanted(Entity e) {
        if (e == mc.player || e.isInvisible()) return false;
        if (e instanceof Player) return players.get();
        if (e instanceof ItemEntity) return items.get();
        return e instanceof LivingEntity && mobs.get();
    }

    @Override
    public void onRenderWorld(RenderContext context) {
        if (mc.level == null || mc.player == null) return;
        float partial = mc.getFrameTime();
        double maxSq = maxDistance.get() * (double) maxDistance.get();
        float s = scale.get().floatValue();

        for (Entity e : mc.level.entitiesForRendering()) {
            if (!wanted(e) || mc.player.distanceToSqr(e) > maxSq) continue;
            Vec3 top = e.getPosition(partial).add(0, e.getBbHeight() + 0.5, 0);
            if (heldItem.get() && e instanceof LivingEntity living && !living.getMainHandItem().isEmpty()) {
                Component held = living.getMainHandItem().getHoverName().copy().withStyle(s_ -> s_.withColor(0xC0C0FF));
                RenderUtils.label(context, top, held, s * 0.85f, throughWalls.get());
                top = top.add(0, 0.28 * Math.max(1, mc.player.distanceTo(e) / 8) * s, 0);
            }
            RenderUtils.label(context, top, tagFor(e), s, throughWalls.get());
        }
    }
}
