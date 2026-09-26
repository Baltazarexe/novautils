package dev.novautils.modules.render;

import dev.novautils.modules.Category;
import dev.novautils.modules.Module;
import dev.novautils.utils.RenderUtils;
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderContext;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.util.ARGB;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/** Remembers where players were standing when they logged out near you, and marks the spot. */
public class LogoutSpots extends Module {
    private record Spot(String name, Vec3 pos) {}

    private final Map<UUID, Vec3> lastPos = new HashMap<>();
    private final Map<UUID, String> names = new HashMap<>();
    private final List<Spot> spots = new ArrayList<>();

    public LogoutSpots() {
        super("LogoutSpots", "Marks where players logged out.", Category.RENDER);
    }

    @Override
    public void onDisable() {
        lastPos.clear();
        names.clear();
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
        }

        for (UUID id : new ArrayList<>(lastPos.keySet())) {
            if (visible.contains(id)) continue;
            // Gone from view: only a logout if they are also gone from the server's player list.
            if (mc.getConnection().getPlayerInfo(id) == null) {
                String name = names.getOrDefault(id, "?");
                spots.add(new Spot(name, lastPos.get(id)));
                mc.player.sendSystemMessage(Component.literal("[NovaUtils] " + name + " logged out at "
                    + (int) lastPos.get(id).x + ", " + (int) lastPos.get(id).y + ", " + (int) lastPos.get(id).z));
            }
            lastPos.remove(id);
        }
    }

    @Override
    public void onRenderWorld(LevelRenderContext context) {
        if (spots.isEmpty()) return;
        Vec3 cam = context.levelState().cameraRenderState.pos;
        int color = ARGB.colorFromFloat(0.5f, 0.8f, 0.3f, 1f);

        for (Spot s : spots) {
            AABB box = new AABB(s.pos().x - 0.3, s.pos().y, s.pos().z - 0.3, s.pos().x + 0.3, s.pos().y + 1.8, s.pos().z + 0.3);
            context.poseStack().pushPose();
            context.poseStack().translate(-cam.x, -cam.y, -cam.z);
            context.submitNodeCollector().submitCustomGeometry(context.poseStack(), RenderTypes.debugFilledBox(),
                (pose, buffer) -> RenderUtils.drawFilledBox(pose, buffer, box, color));
            context.poseStack().popPose();
        }
    }
}
