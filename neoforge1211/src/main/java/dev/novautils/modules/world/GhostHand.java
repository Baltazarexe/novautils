package dev.novautils.modules.world;

import dev.novautils.modules.Category;
import dev.novautils.modules.Module;
import dev.novautils.render.RenderContext;
import dev.novautils.settings.Setting;
import dev.novautils.utils.InvUtils;
import dev.novautils.utils.RenderUtils;
import net.minecraft.core.BlockPos;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.BellBlock;
import net.minecraft.world.level.block.ButtonBlock;
import net.minecraft.world.level.block.DoorBlock;
import net.minecraft.world.level.block.FenceGateBlock;
import net.minecraft.world.level.block.LeverBlock;
import net.minecraft.world.level.block.NoteBlock;
import net.minecraft.world.level.block.TrapDoorBlock;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

import java.util.Optional;

/**
 * Right-click through walls. When what's under the crosshair isn't something you can use, the click
 * follows your line of sight through the blocks in the way and uses the first chest, furnace,
 * crafting table, lever, door, button... (or villager, animal, minecart - never a player) within
 * reach. Reach is the normal 4.5 by default, so servers see an ordinary block use; it just ignores
 * what's in front of it. Sneaking turns it off, so placing blocks against a wall still works.
 */
public class GhostHand extends Module {
    private final Setting<Boolean> blocks = register(Setting.builder(Boolean.class, "containers")
        .description("Chests, barrels, furnaces, crafting tables, anvils, shulker boxes...").defaultValue(true).build());

    private final Setting<Boolean> usables = register(Setting.builder(Boolean.class, "switches-doors")
        .description("Levers, buttons, doors, trapdoors, fence gates, note blocks, bells.").defaultValue(true).build());

    private final Setting<Boolean> entities = register(Setting.builder(Boolean.class, "creatures")
        .description("Villagers, animals, minecarts, boats... through walls (never players).").defaultValue(true).build());

    private final Setting<Double> range = register(Setting.builder(Double.class, "range")
        .description("How far the click reaches (survival is 4.5).").defaultValue(4.5).range(1.0, 6.0).build());

    private final Setting<Boolean> preview = register(Setting.builder(Boolean.class, "preview")
        .description("Outline what the click would use.").defaultValue(true).build());

    private final Setting<Integer> color = register(Setting.builder(Integer.class, "preview-color")
        .description("Outline colour.").defaultValue(0x60C49BFF).asColor().visible(preview::get).build());

    private record Target(BlockHitResult block, Entity entity, double distance) {}

    public GhostHand() {
        super("GhostHand", "Use chests, levers, doors and villagers through walls.", Category.WORLD);
    }

    private boolean usable(BlockState state, BlockPos pos) {
        Block b = state.getBlock();
        // containers and machines: vanilla ones say so (menu provider), modded ones (Mekanism, AE2,
        // Sophisticated Storage...) usually only have a block entity on the client
        if (blocks.get() && (b == Blocks.ENDER_CHEST || state.getMenuProvider(mc.level, pos) != null || mc.level.getBlockEntity(pos) != null)) return true;
        return usables.get() && (b instanceof LeverBlock || b instanceof ButtonBlock || b instanceof DoorBlock
            || b instanceof TrapDoorBlock || b instanceof FenceGateBlock || b instanceof NoteBlock || b instanceof BellBlock);
    }

    /** First usable block on the line of sight within reach, ignoring whatever is in front of it. */
    private BlockHitResult findBlock(Vec3 eye, Vec3 end) {
        Vec3 dir = end.subtract(eye);
        double len = dir.length();
        BlockPos last = null;
        for (double d = 0; d <= len; d += 0.05) {
            Vec3 p = eye.add(dir.scale(d / len));
            BlockPos pos = BlockPos.containing(p);
            if (pos.equals(last)) continue;
            last = pos;
            BlockState state = mc.level.getBlockState(pos);
            if (state.isAir() || !usable(state, pos)) continue;
            BlockHitResult hit = state.getShape(mc.level, pos).clip(eye, end, pos);
            if (hit != null && hit.getType() == HitResult.Type.BLOCK) return hit;
            // odd or empty outline (some modded blocks): aim at the centre, on the face looking at us
            Vec3 centre = Vec3.atCenterOf(pos);
            return new BlockHitResult(centre, net.minecraft.core.Direction.getNearest(eye.x - centre.x, eye.y - centre.y, eye.z - centre.z), pos, false);
        }
        return null;
    }

    private Target findEntity(Vec3 eye, Vec3 end) {
        AABB search = new AABB(eye, end).inflate(1.0);
        Entity best = null;
        double bestDist = Double.MAX_VALUE;
        for (Entity e : mc.level.getEntities((Entity) null, search, x -> x.isAlive() && x.isPickable() && x != mc.player && !x.isSpectator() && !(x instanceof Player))) {
            Optional<Vec3> hit = e.getBoundingBox().inflate(e.getPickRadius()).clip(eye, end);
            if (hit.isEmpty()) continue;
            double d = hit.get().distanceTo(eye);
            if (d < bestDist) {
                bestDist = d;
                best = e;
            }
        }
        return best == null ? null : new Target(null, best, bestDist);
    }

    /** What a right-click would use right now, or null. */
    public Target target() {
        if (mc.player == null || mc.level == null) return null;
        Vec3 eye = mc.player.getEyePosition();
        Vec3 end = eye.add(mc.player.getLookAngle().scale(range.get()));
        BlockHitResult block = findBlock(eye, end);
        Target entity = entities.get() ? findEntity(eye, end) : null;
        Target blockTarget = block == null ? null : new Target(block, null, block.getLocation().distanceTo(eye));
        if (blockTarget == null) return entity;
        if (entity == null) return blockTarget;
        return entity.distance() < blockTarget.distance() ? entity : blockTarget;
    }

    /**
     * MinecraftUseMixin, on every vanilla right-click. True = we used something through the wall
     * and vanilla should not also use the item.
     */
    public static boolean tryUse() {
        if (!dev.novautils.NovaUtils.modulesReady) return false;
        GhostHand gh = dev.novautils.modules.ModuleManager.get().get(GhostHand.class);
        if (!gh.isEnabled() || mc.player == null || mc.level == null || mc.gameMode == null) return false;
        if (mc.player.isShiftKeyDown() || mc.player.isHandsBusy()) return false;

        // vanilla handles it when the crosshair already points at a creature or something usable
        if (mc.hitResult instanceof EntityHitResult) return false;
        if (mc.hitResult instanceof BlockHitResult bhr && bhr.getType() == HitResult.Type.BLOCK) {
            BlockState state = mc.level.getBlockState(bhr.getBlockPos());
            if (gh.usable(state, bhr.getBlockPos())) return false;
        }

        Target t = gh.target();
        if (t == null) return false;
        if (t.block() != null) {
            mc.gameMode.useItemOn(mc.player, InteractionHand.MAIN_HAND, t.block());
        } else {
            mc.gameMode.interact(mc.player, t.entity(), InteractionHand.MAIN_HAND);
        }
        InvUtils.swing();
        return true;
    }

    @Override
    public void onRenderWorld(RenderContext context) {
        if (!preview.get() || mc.player == null || mc.player.isShiftKeyDown()) return;
        Target t = target();
        if (t == null) return;
        int c = color.get();
        AABB box = t.block() != null ? new AABB(t.block().getBlockPos()) : t.entity().getBoundingBox();
        RenderUtils.world(context, (pose, buffer) -> RenderUtils.drawBox(pose, buffer, box, c));
    }
}
