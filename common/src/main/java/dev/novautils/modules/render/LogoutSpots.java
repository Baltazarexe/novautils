package dev.novautils.modules.render;

import dev.novautils.modules.Category;
import dev.novautils.modules.Module;
import dev.novautils.render.RenderContext;
import dev.novautils.settings.Setting;
import dev.novautils.utils.EspRender;
import dev.novautils.utils.RenderUtils;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Marks where a player you could see logged out: their hitbox, name, health and how long ago.
 * The mark disappears when they log back in. Tells you in chat as well.
 */
public class LogoutSpots extends Module {
    private record Spot(String name, Vec3 pos, float health, long time, String dimension) {}

    private final Setting<Integer> color = register(Setting.builder(Integer.class, "color")
        .description("Box colour.").defaultValue(0x805080FF).asColor().build());

    private final Setting<Boolean> chat = register(Setting.builder(Boolean.class, "chat-message")
        .description("Say in chat when someone logs out near you.").defaultValue(true).build());

    private final Map<UUID, Vec3> lastPos = new HashMap<>();
    private final Map<UUID, String> names = new HashMap<>();
    private final Map<UUID, Float> healths = new HashMap<>();
    private final Map<UUID, Spot> spots = new HashMap<>();

    public LogoutSpots() {
        super("LogoutSpots", "Marks where players logged out.", Category.RENDER);
    }

    @Override
    public void onDisable() {
        lastPos.clear();
        names.clear();
        healths.clear();
        spots.clear();
    }

    @Override
    public void onTick() {
        if (mc.level == null || mc.player == null || mc.getConnection() == null) return;

        Set<UUID> visible = new HashSet<>();
        for (Player p : mc.level.players()) {
            if (p == mc.player) continue;
            visible.add(p.getUUID());
            lastPos.put(p.getUUID(), p.position());
            names.put(p.getUUID(), p.getName().getString());
            healths.put(p.getUUID(), p.getHealth() + p.getAbsorptionAmount());
        }

        // back online: forget their old spot
        spots.keySet().removeIf(id -> mc.getConnection().getPlayerInfo(id) != null);

        for (UUID id : new HashSet<>(lastPos.keySet())) {
            if (visible.contains(id)) continue;
            // gone from view: a logout only if they also left the server's player list
            if (mc.getConnection().getPlayerInfo(id) == null) {
                String name = names.getOrDefault(id, "?");
                Vec3 pos = lastPos.get(id);
                spots.put(id, new Spot(name, pos, healths.getOrDefault(id, 20f), System.currentTimeMillis(),
                    mc.level.dimension().identifier().toString()));
                if (chat.get()) {
                    mc.player.sendSystemMessage(Component.literal("[NovaUtils] " + name + " logged out at "
                        + (int) pos.x + ", " + (int) pos.y + ", " + (int) pos.z));
                }
            }
            lastPos.remove(id);
        }
    }

    private static String ago(long time) {
        long s = (System.currentTimeMillis() - time) / 1000;
        if (s < 60) return s + "s";
        if (s < 3600) return (s / 60) + "m";
        return (s / 3600) + "h";
    }

    @Override
    public void onRenderWorld(RenderContext context) {
        if (spots.isEmpty() || mc.level == null) return;
        Vec3 cam = context.cameraPos();
        String dim = mc.level.dimension().identifier().toString();
        int c = color.get();

        for (Spot s : spots.values()) {
            if (!s.dimension().equals(dim)) continue;
            AABB box = new AABB(s.pos().x - 0.3, s.pos().y, s.pos().z - 0.3, s.pos().x + 0.3, s.pos().y + 1.8, s.pos().z + 0.3);
            context.poseStack().pushPose();
            context.poseStack().translate(-cam.x, -cam.y, -cam.z);
            context.submitNodeCollector().submitCustomGeometry(context.poseStack(), EspRender.filledNoDepth(),
                (pose, buffer) -> RenderUtils.drawBox(pose, buffer, box, c));
            context.poseStack().popPose();

            Component label = Component.literal(s.name()).withColor(0xFFFFFF)
                .append(Component.literal(String.format(" %.0f", s.health())).withColor(0xFF5555))
                .append(Component.literal(" logged " + ago(s.time()) + " ago").withColor(0xAAAAAA));
            RenderUtils.label(context, s.pos().add(0, 2.2, 0), label, 1f, true);
        }
    }
}
