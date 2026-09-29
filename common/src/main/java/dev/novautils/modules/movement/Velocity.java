package dev.novautils.modules.movement;

import dev.novautils.modules.Category;
import dev.novautils.modules.Module;
import dev.novautils.modules.ModuleManager;
import dev.novautils.settings.Setting;
import net.minecraft.client.Minecraft;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientboundSetEntityMotionPacket;
import net.minecraft.world.phys.Vec3;

/**
 * Anti-knockback: scales how much the server's hits and explosions push you. 0% = you don't move.
 */
public class Velocity extends Module {
    private final Setting<Integer> horizontal = register(Setting.builder(Integer.class, "horizontal")
        .description("Percent of horizontal knockback you still take.")
        .defaultValue(0).range(0, 100).build());

    private final Setting<Integer> vertical = register(Setting.builder(Integer.class, "vertical")
        .description("Percent of vertical knockback you still take.")
        .defaultValue(0).range(0, 100).build());

    private final Setting<Boolean> explosions = register(Setting.builder(Boolean.class, "explosions")
        .description("Also reduce knockback from explosions (crystals, TNT, beds).")
        .defaultValue(true).build());

    private static Vec3 beforeExplosion;

    public Velocity() {
        super("Velocity", "Reduces knockback from hits and explosions.", Category.MOVEMENT);
    }

    private Vec3 scale(Vec3 v) {
        double h = horizontal.get() / 100.0, y = vertical.get() / 100.0;
        return new Vec3(v.x * h, v.y * y, v.z * h);
    }

    @Override
    public boolean onReceivePacket(Packet<?> packet) {
        if (!(packet instanceof ClientboundSetEntityMotionPacket motion) || mc.player == null) return false;
        if (motion.id() != mc.player.getId()) return false;
        mc.player.setDeltaMovement(scale(motion.movement()));
        return true;
    }

    /** ClientPacketListenerMixin, before vanilla applies an explosion's push. */
    public static void beforeExplosion() {
        var mc = Minecraft.getInstance();
        beforeExplosion = mc.player == null ? null : mc.player.getDeltaMovement();
    }

    /** ...and after: keep only the allowed share of what the explosion added. */
    public static void afterExplosion() {
        var mc = Minecraft.getInstance();
        if (beforeExplosion == null || mc.player == null) return;
        Velocity v = ModuleManager.get().get(Velocity.class);
        if (v.isEnabled() && v.explosions.get()) {
            Vec3 added = mc.player.getDeltaMovement().subtract(beforeExplosion);
            mc.player.setDeltaMovement(beforeExplosion.add(v.scale(added)));
        }
        beforeExplosion = null;
    }
}
