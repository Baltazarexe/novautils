package dev.novautils.modules.misc;

import dev.novautils.modules.Category;
import dev.novautils.modules.Module;
import dev.novautils.modules.ModuleManager;
import dev.novautils.modules.render.Waypoints;
import dev.novautils.render.RenderContext;
import dev.novautils.settings.Setting;
import dev.novautils.utils.PlatformCompat;
import dev.novautils.utils.RenderUtils;
import net.minecraft.client.gui.screens.DeathScreen;
import net.minecraft.network.chat.Component;
import dev.novautils.utils.ARGB;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * Remembers where you died: a beam and a "Death - 120m" label through walls until you get back
 * to your stuff, a chat message with the coordinates, and optionally a permanent waypoint.
 */
public class DeathPoint extends Module {
    private final Setting<Boolean> waypoint = register(Setting.builder(Boolean.class, "save-waypoint")
        .description("Also save a \"death\" waypoint (replaced on each death).")
        .defaultValue(true).build());

    private Vec3 death;
    private String deathDimension;
    private boolean wasDead;

    public DeathPoint() {
        super("DeathPoint", "Marks where you died so you can find your stuff.", Category.MISC);
    }

    @Override
    public void onTick() {
        if (mc.player == null || mc.level == null) return;
        boolean dead = PlatformCompat.currentScreen(mc) instanceof DeathScreen || mc.player.getHealth() <= 0;
        if (dead && !wasDead) {
            death = mc.player.position();
            deathDimension = mc.level.dimension().location().toString();
            mc.player.sendSystemMessage(Component.literal(String.format(
                "[NovaUtils] Died at %d, %d, %d", (int) death.x, (int) death.y, (int) death.z)));
            if (waypoint.get()) ModuleManager.get().get(Waypoints.class).add("death", death.x, death.y, death.z);
        }
        wasDead = dead;
        if (death != null && mc.player.position().distanceToSqr(death) < 9) death = null;
    }

    @Override
    public void onRenderWorld(RenderContext context) {
        if (death == null || mc.player == null || mc.level == null) return;
        if (!mc.level.dimension().location().toString().equals(deathDimension)) return;
        Vec3 cam = context.cameraPos();
        int color = ARGB.colorFromFloat(0.55f, 1f, 0.3f, 0.3f);
        AABB box = new AABB(death.x - 0.3, death.y, death.z - 0.3, death.x + 0.3, death.y + 256, death.z + 0.3);
        RenderUtils.world(context, (pose, buffer) -> RenderUtils.drawBox(pose, buffer, box, color));

        double dist = mc.player.position().distanceTo(death);
        Vec3 eye = mc.player.getEyePosition();
        Vec3 labelPos = death.add(0, 1.5, 0);
        if (dist > 64) labelPos = eye.add(labelPos.subtract(eye).normalize().scale(64));
        RenderUtils.label(context, labelPos, Component.literal("Death").withStyle(s_ -> s_.withColor(0xFF5555))
            .append(Component.literal(" " + Math.round(dist) + "m").withStyle(s_ -> s_.withColor(0xAAAAAA))), 1.1f, true);
    }
}
