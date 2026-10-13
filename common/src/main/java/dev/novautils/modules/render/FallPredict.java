package dev.novautils.modules.render;

import dev.novautils.modules.Category;
import dev.novautils.modules.Module;
import dev.novautils.render.RenderContext;
import dev.novautils.settings.Setting;
import dev.novautils.utils.RenderUtils;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * While you're in the air, shows where you'll land and about how much fall damage it'll do,
 * red when it would kill you. Uses vanilla's own fall physics (gravity 0.08, drag 0.98 / 0.91),
 * so it follows jumps and knockback too. Water, ladders and the like land you safely.
 */
public class FallPredict extends Module {
    private final Setting<Integer> minDamage = register(Setting.builder(Integer.class, "min-damage")
        .description("Only show when the fall would hurt at least this much (0 = always while falling).")
        .defaultValue(0).range(0, 20).build());

    private final Setting<Integer> color = register(Setting.builder(Integer.class, "color")
        .description("Landing spot colour when the fall is safe.").defaultValue(0x6066DD77).asColor().build());

    private Vec3 landing;
    private float damage;
    private boolean safe;

    public FallPredict() {
        super("FallPredict", "Shows where you'll land and the fall damage.", Category.RENDER);
    }

    @Override
    public void onTick() {
        landing = null;
        var p = mc.player;
        if (p == null || mc.level == null || p.onGround() || p.getAbilities().flying || p.isFallFlying()
            || p.isInWater() || p.isPassenger() || p.onClimbable()) return;

        Vec3 pos = p.position(), vel = p.getDeltaMovement();
        double startY = pos.y;
        for (int t = 0; t < 400; t++) {
            vel = new Vec3(vel.x * 0.91, (vel.y - 0.08) * 0.98, vel.z * 0.91);
            Vec3 next = pos.add(vel);
            BlockPos below = BlockPos.containing(next.x, next.y - 0.001, next.z);
            BlockState state = mc.level.getBlockState(below);
            boolean liquid = !state.getFluidState().isEmpty();
            if (liquid || !state.getCollisionShape(mc.level, below).isEmpty() || next.y < mc.level.getMinY()) {
                double top = liquid ? below.getY() + 1 : below.getY() + Math.max(0, state.getCollisionShape(mc.level, below).max(net.minecraft.core.Direction.Axis.Y));
                landing = new Vec3(next.x, top, next.z);
                safe = liquid || state.is(net.minecraft.world.level.block.Blocks.HAY_BLOCK)
                    || state.is(net.minecraft.world.level.block.Blocks.SLIME_BLOCK) || state.is(net.minecraft.world.level.block.Blocks.COBWEB)
                    || state.is(net.minecraft.world.level.block.Blocks.POWDER_SNOW);
                double fall = p.fallDistance + Math.max(0, startY - top);
                double safeDist = p.getAttributeValue(Attributes.SAFE_FALL_DISTANCE);
                damage = safe ? 0 : (float) Math.max(0, Math.ceil((fall - safeDist) * p.getAttributeValue(Attributes.FALL_DAMAGE_MULTIPLIER)));
                if (next.y < mc.level.getMinY()) damage = Float.MAX_VALUE;
                return;
            }
            pos = next;
        }
    }

    /** Where the current fall ends, or null when not falling (tests). */
    public Vec3 landing() {
        return landing;
    }

    @Override
    public void onRenderWorld(RenderContext context) {
        if (landing == null || mc.player == null || damage < minDamage.get()) return;
        boolean deadly = damage >= mc.player.getHealth() + mc.player.getAbsorptionAmount();
        int c = deadly ? 0x80FF3030 : damage > 0 ? 0x70FFB040 : color.get();
        AABB box = new AABB(landing.x - 0.3, landing.y, landing.z - 0.3, landing.x + 0.3, landing.y + 0.05, landing.z + 0.3);
        RenderUtils.world(context, (pose, buffer) -> RenderUtils.drawBox(pose, buffer, box, c));
        String text = damage == Float.MAX_VALUE ? "void" : damage <= 0 ? "safe" : "~" + Math.round(damage) + " dmg" + (deadly ? " DEATH" : "");
        RenderUtils.label(context, landing.add(0, 0.4, 0), Component.literal(text).withColor(c & 0xFFFFFF), 1f, true);
    }
}
