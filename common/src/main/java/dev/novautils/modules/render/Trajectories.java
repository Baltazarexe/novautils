package dev.novautils.modules.render;

import dev.novautils.modules.Category;
import dev.novautils.modules.Module;
import dev.novautils.render.RenderContext;
import dev.novautils.settings.Setting;
import dev.novautils.utils.EspRender;
import dev.novautils.utils.RenderUtils;
import net.minecraft.core.component.DataComponents;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.List;

/**
 * Shows where what you're holding will fly: ender pearls, arrows (follows how far the bow is
 * drawn), crossbow shots, tridents, snowballs, eggs, splash/lingering potions, XP bottles and wind
 * charges. Simulated tick by tick with the game's own speed, gravity and drag, stopping at the
 * first block or creature it hits, which gets highlighted.
 */
public class Trajectories extends Module {
    /** gravityFirst: thrown items (pearls, potions...) fall and slow down before moving each tick; arrows move first. */
    private record Physics(double speed, double gravity, double drag, float pitchOffset, boolean gravityFirst, boolean teleports) {
        Physics(double speed, double gravity, double drag, float pitchOffset, boolean gravityFirst) {
            this(speed, gravity, drag, pitchOffset, gravityFirst, false);
        }
    }

    private final Setting<Integer> color = register(Setting.builder(Integer.class, "color")
        .description("Path colour.").defaultValue(0xC0FFFFFF).asColor().build());

    private final Setting<Integer> hitColor = register(Setting.builder(Integer.class, "hit-color")
        .description("Colour of the spot it lands on.").defaultValue(0x80FF5050).asColor().build());

    private Vec3 lastLanding;

    /** Where the last drawn path ends (null if it flies off into nothing). */
    public Vec3 lastLanding() {
        return lastLanding;
    }

    public Trajectories() {
        super("Trajectories", "Shows where pearls, arrows and potions will land.", Category.RENDER);
    }

    private Physics physicsFor(ItemStack s) {
        var p = mc.player;
        if (s.is(Items.BOW)) {
            float power = 1f;
            if (p.isUsingItem() && p.getUseItem() == s) {
                float t = p.getTicksUsingItem() / 20f;
                power = Math.min(1f, (t * t + t * 2f) / 3f);
                if (power < 0.1f) return null;
            }
            return new Physics(power * 3.0, 0.05, 0.99, 0, false);
        }
        if (s.is(Items.CROSSBOW)) {
            var charged = s.get(DataComponents.CHARGED_PROJECTILES);
            if (charged == null || charged.isEmpty()) return null;
            return new Physics(3.15, 0.05, 0.99, 0, false);
        }
        if (s.is(Items.TRIDENT)) return new Physics(2.5, 0.05, 0.99, 0, false);
        if (s.is(Items.ENDER_PEARL)) return new Physics(1.5, 0.03, 0.99, 0, true, true);
        if (s.is(Items.SNOWBALL) || s.is(Items.EGG)) return new Physics(1.5, 0.03, 0.99, 0, true);
        if (s.is(Items.SPLASH_POTION) || s.is(Items.LINGERING_POTION)) return new Physics(0.5, 0.05, 0.99, -20f, true);
        if (s.is(Items.EXPERIENCE_BOTTLE)) return new Physics(0.7, 0.07, 0.99, -20f, true);
        if (s.is(Items.WIND_CHARGE)) return new Physics(1.5, 0.0, 1.0, 0, true);
        return null;
    }

    @Override
    public void onRenderWorld(RenderContext context) {
        var p = mc.player;
        if (p == null || mc.level == null) return;
        Physics phys = physicsFor(p.getMainHandItem());
        if (phys == null) phys = physicsFor(p.getOffhandItem());
        if (phys == null) return;

        float partial = mc.getDeltaTracker().getGameTimeDeltaPartialTick(false);
        float yaw = p.getViewYRot(partial), pitch = p.getViewXRot(partial);
        double ry = Math.toRadians(yaw), rp = Math.toRadians(pitch), rpo = Math.toRadians(pitch + phys.pitchOffset());
        Vec3 dir = new Vec3(-Math.sin(ry) * Math.cos(rp), -Math.sin(rpo), Math.cos(ry) * Math.cos(rp)).normalize();
        Vec3 vel = dir.scale(phys.speed());
        Vec3 motion = p.getDeltaMovement();
        vel = vel.add(motion.x, p.onGround() ? 0 : motion.y, motion.z);
        Vec3 pos = p.getEyePosition(partial).subtract(0, 0.1, 0);

        lastLanding = null;
        List<Vec3> path = new ArrayList<>();
        path.add(pos);
        AABB hitBox = null;
        AABB arrivalBox = null;
        for (int i = 0; i < 300; i++) {
            if (phys.gravityFirst()) vel = vel.subtract(0, phys.gravity(), 0).scale(phys.drag());
            Vec3 next = pos.add(vel);
            BlockHitResult block = mc.level.clip(new ClipContext(pos, next, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, p));
            Entity hitEntity = null;
            Vec3 end = block.getType() == HitResult.Type.MISS ? next : block.getLocation();
            AABB sweep = new AABB(pos, end).inflate(0.3);
            for (Entity e : mc.level.getEntities(p, sweep, e -> e instanceof LivingEntity && e.isAlive())) {
                if (e.getBoundingBox().inflate(0.3).clip(pos, end).isPresent()) {
                    hitEntity = e;
                    break;
                }
            }
            if (hitEntity != null) {
                path.add(hitEntity.getBoundingBox().getCenter());
                hitBox = hitEntity.getBoundingBox().inflate(0.05);
                lastLanding = phys.teleports() ? pos : hitEntity.position();
                if (phys.teleports()) arrivalBox = new AABB(pos.x - 0.3, pos.y, pos.z - 0.3, pos.x + 0.3, pos.y + 1.8, pos.z + 0.3);
                break;
            }
            path.add(end);
            if (block.getType() != HitResult.Type.MISS) {
                Vec3 h = block.getLocation();
                // a pearl teleports you to where it was at the start of the tick it hit something
                lastLanding = phys.teleports() ? pos : h;
                if (phys.teleports()) arrivalBox = new AABB(pos.x - 0.3, pos.y, pos.z - 0.3, pos.x + 0.3, pos.y + 1.8, pos.z + 0.3);
                hitBox = new AABB(h.x - 0.25, h.y - 0.25, h.z - 0.25, h.x + 0.25, h.y + 0.25, h.z + 0.25);
                break;
            }
            pos = next;
            if (!phys.gravityFirst()) vel = vel.scale(phys.drag()).subtract(0, phys.gravity(), 0);
            if (pos.y < mc.level.getMinY() - 16) break;
        }

        Vec3 cam = context.cameraPos();
        int c = color.get(), hc = hitColor.get();
        AABB box = hitBox, arrival = arrivalBox;
        RenderUtils.world(context, (pose, buffer) -> {
            // skip the first bit so the line doesn't cover the crosshair
            for (int i = 2; i < path.size(); i++) RenderUtils.drawRibbon(pose, buffer, path.get(i - 1), path.get(i), 0.02, c);
            if (box != null) RenderUtils.drawBox(pose, buffer, box, hc);
            if (arrival != null) RenderUtils.drawBox(pose, buffer, arrival, 0x6055FFFF);
        });
    }
}
