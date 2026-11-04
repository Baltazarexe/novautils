package dev.novautils.modules.world;

import dev.novautils.gui.Theme;
import dev.novautils.modules.Category;
import dev.novautils.modules.Module;
import dev.novautils.render.RenderContext;
import dev.novautils.settings.Setting;
import dev.novautils.utils.InvUtils;
import dev.novautils.utils.RenderUtils;
import net.minecraft.core.BlockPos;
import net.minecraft.util.Mth;
import net.minecraft.world.Container;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.entity.decoration.HangingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.vehicle.ContainerEntity;
import net.minecraft.world.level.block.BedBlock;
import net.minecraft.world.level.block.BellBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.ButtonBlock;
import net.minecraft.world.level.block.DoorBlock;
import net.minecraft.world.level.block.FenceGateBlock;
import net.minecraft.world.level.block.LeverBlock;
import net.minecraft.world.level.block.NoteBlock;
import net.minecraft.world.level.block.TrapDoorBlock;
import net.minecraft.world.level.block.entity.BannerBlockEntity;
import net.minecraft.world.level.block.entity.BeehiveBlockEntity;
import net.minecraft.world.level.block.entity.BellBlockEntity;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BrushableBlockEntity;
import net.minecraft.world.level.block.entity.CampfireBlockEntity;
import net.minecraft.world.level.block.entity.ChiseledBookShelfBlockEntity;
import net.minecraft.world.level.block.entity.CommandBlockEntity;
import net.minecraft.world.level.block.entity.ComparatorBlockEntity;
import net.minecraft.world.level.block.entity.ConduitBlockEntity;
import net.minecraft.world.level.block.entity.DaylightDetectorBlockEntity;
import net.minecraft.world.level.block.entity.DecoratedPotBlockEntity;
import net.minecraft.world.level.block.entity.JigsawBlockEntity;
import net.minecraft.world.level.block.entity.JukeboxBlockEntity;
import net.minecraft.world.level.block.entity.SculkCatalystBlockEntity;
import net.minecraft.world.level.block.entity.SculkSensorBlockEntity;
import net.minecraft.world.level.block.entity.SculkShriekerBlockEntity;
import net.minecraft.world.level.block.entity.SignBlockEntity;
import net.minecraft.world.level.block.entity.SkullBlockEntity;
import net.minecraft.world.level.block.entity.SpawnerBlockEntity;
import net.minecraft.world.level.block.entity.StructureBlockEntity;
import net.minecraft.world.level.block.entity.TheEndPortalBlockEntity;
import net.minecraft.world.level.block.piston.PistonMovingBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Right-click through walls. When what's under the crosshair isn't something you can use, the click
 * follows your line of sight through the blocks in the way and uses what is behind them: chests,
 * barrels, furnaces and other containers first (modded storage and machines too), otherwise the
 * first lever, door, button... or creature within reach. Signs, beds, banners, item frames, armour
 * stands and the like never take the click, so a decorated wall doesn't get in the way.
 *
 * <p>Every container within reach gets a faint outline, so chests you can't see can be found, and
 * the aim snaps to a hidden container the crosshair passes close to. Reach is the normal 4.5 by
 * default; servers refuse clicks past about 5.5 blocks (6 on 1.20.1), so a longer reach would only
 * make the click silently fail. Sneaking turns it off, so placing blocks against a wall still works.
 */
public class GhostHand extends Module {
    private final Setting<Boolean> blocks = register(Setting.builder(Boolean.class, "containers")
        .description("Chests, barrels, furnaces, crafting tables, anvils, shulker boxes, modded storage and machines.").defaultValue(true).build());

    private final Setting<Boolean> usables = register(Setting.builder(Boolean.class, "switches-doors")
        .description("Levers, buttons, doors, trapdoors, fence gates, note blocks, bells.").defaultValue(true).build());

    private final Setting<Boolean> entities = register(Setting.builder(Boolean.class, "creatures")
        .description("Villagers, animals, minecarts, boats... through walls (never players, item frames or armour stands).").defaultValue(true).build());

    private final Setting<Boolean> preferContainers = register(Setting.builder(Boolean.class, "prefer-containers")
        .description("When a container and something else are both on the line of sight, open the container even if the other is closer.")
        .defaultValue(true).build());

    private final Setting<Double> range = register(Setting.builder(Double.class, "range")
        .description("How far the click reaches. Survival is 4.5; servers refuse clicks past about 5.5 blocks (6 on 1.20.1).")
        .defaultValue(4.5).range(1.0, 6.0).build());

    private final Setting<Double> aimAssist = register(Setting.builder(Double.class, "aim-assist")
        .description("How close the crosshair has to pass to a hidden container to snap to it, in blocks (0 = off).")
        .defaultValue(0.3).range(0.0, 0.6).visible(blocks::get).build());

    private final Setting<Boolean> preview = register(Setting.builder(Boolean.class, "preview")
        .description("Outline what the click would use.").defaultValue(true).build());

    private final Setting<Boolean> showReachable = register(Setting.builder(Boolean.class, "show-reachable")
        .description("Faintly outline every container within reach, so chests behind walls can be found and aimed at.")
        .defaultValue(true).visible(preview::get).build());

    private final Setting<Integer> color = register(Setting.builder(Integer.class, "preview-color")
        .description("Outline colour.").defaultValue(0x60C49BFF).asColor().visible(preview::get).build());

    /** What a block is to GhostHand: a container, a switch or door, some other block entity, or nothing. */
    private enum Kind { NONE, CONTAINER, SWITCH, OTHER }

    private record Target(BlockHitResult block, Entity entity, double distance, boolean container) {}

    /** Containers within reach, refreshed every tick (outlines and aim assist). */
    private final List<BlockPos> reachable = new ArrayList<>();

    public GhostHand() {
        super("GhostHand", "Open chests behind walls, flip levers and use villagers through blocks.", Category.WORLD);
    }

    private Kind kindOf(BlockState state, BlockPos pos) {
        if (state.isAir()) return Kind.NONE;
        Block b = state.getBlock();
        if (blocks.get()) {
            if (b == Blocks.ENDER_CHEST || state.getMenuProvider(mc.level, pos) != null) return Kind.CONTAINER;
            BlockEntity be = mc.level.getBlockEntity(pos);
            // modded storage and machines often only show up as a block entity on the client: the ones
            // that hold items or open a menu say so, anything else is kept as a last resort
            if (be != null && !decorative(b, be)) return be instanceof Container || be instanceof MenuProvider ? Kind.CONTAINER : Kind.OTHER;
        }
        if (usables.get() && (b instanceof LeverBlock || b instanceof ButtonBlock || b instanceof DoorBlock
            || b instanceof TrapDoorBlock || b instanceof FenceGateBlock || b instanceof NoteBlock || b instanceof BellBlock)) return Kind.SWITCH;
        return Kind.NONE;
    }

    /** Block entities a right-click shouldn't land on: it would edit a sign, sleep, eject a disc, take a book... */
    private static boolean decorative(Block b, BlockEntity be) {
        return b instanceof BedBlock || be instanceof SignBlockEntity || be instanceof BannerBlockEntity || be instanceof SkullBlockEntity
            || be instanceof CampfireBlockEntity || be instanceof BeehiveBlockEntity || be instanceof SpawnerBlockEntity
            || be instanceof JukeboxBlockEntity || be instanceof ChiseledBookShelfBlockEntity || be instanceof ComparatorBlockEntity
            || be instanceof DaylightDetectorBlockEntity || be instanceof DecoratedPotBlockEntity || be instanceof ConduitBlockEntity
            || be instanceof BellBlockEntity || be instanceof SculkSensorBlockEntity || be instanceof SculkShriekerBlockEntity
            || be instanceof SculkCatalystBlockEntity || be instanceof TheEndPortalBlockEntity || be instanceof BrushableBlockEntity
            || be instanceof CommandBlockEntity || be instanceof StructureBlockEntity || be instanceof JigsawBlockEntity
            || be instanceof PistonMovingBlockEntity;
    }

    /** A point on the block on the face looking at us: for odd or empty outlines and aim-assist snaps. */
    private static BlockHitResult aimAt(BlockPos pos, Vec3 point, Vec3 eye) {
        Vec3 centre = Vec3.atCenterOf(pos);
        return new BlockHitResult(point, net.minecraft.core.Direction.getNearest(eye.x - centre.x, eye.y - centre.y, eye.z - centre.z), pos, false);
    }

    private static BlockHitResult hitOn(BlockState state, BlockPos pos, Vec3 eye, Vec3 end) {
        BlockHitResult hit = state.getShape(mc.level, pos).clip(eye, end, pos);
        if (hit != null && hit.getType() == HitResult.Type.BLOCK) return hit;
        return aimAt(pos, Vec3.atCenterOf(pos), eye);
    }

    /** {first container, first other usable block} on the line of sight within reach, ignoring whatever is in front. */
    private BlockHitResult[] scanBlocks(Vec3 eye, Vec3 end) {
        Vec3 dir = end.subtract(eye);
        double len = dir.length();
        BlockPos last = null;
        BlockHitResult container = null, other = null;
        for (double d = 0; d <= len && container == null; d += 0.05) {
            BlockPos pos = BlockPos.containing(eye.add(dir.scale(d / len)));
            if (pos.equals(last)) continue;
            last = pos;
            BlockState state = mc.level.getBlockState(pos);
            Kind kind = kindOf(state, pos);
            if (kind == Kind.NONE) continue;
            if (kind == Kind.CONTAINER) container = hitOn(state, pos, eye, end);
            else if (other == null) other = hitOn(state, pos, eye, end);
        }
        return new BlockHitResult[]{container, other};
    }

    /** Aim assist: the hidden container the line of sight passes closest to (within the snap distance), aimed at its nearest point. */
    private Target snapToContainer(Vec3 eye, Vec3 end) {
        double snap = aimAssist.get();
        if (snap <= 0) return null;
        Target best = null;
        for (BlockPos pos : reachable) {
            if (kindOf(mc.level.getBlockState(pos), pos) != Kind.CONTAINER) continue;
            Optional<Vec3> hit = new AABB(pos).inflate(snap).clip(eye, end);
            if (hit.isEmpty()) continue;
            double d = hit.get().distanceTo(eye);
            if (best != null && d >= best.distance()) continue;
            Vec3 p = hit.get();
            Vec3 onBlock = new Vec3(Mth.clamp(p.x, pos.getX(), pos.getX() + 1), Mth.clamp(p.y, pos.getY(), pos.getY() + 1), Mth.clamp(p.z, pos.getZ(), pos.getZ() + 1));
            best = new Target(aimAt(pos, onBlock, eye), null, d, true);
        }
        return best;
    }

    private Target findEntity(Vec3 eye, Vec3 end) {
        AABB search = new AABB(eye, end).inflate(1.0);
        Entity best = null;
        double bestDist = Double.MAX_VALUE;
        for (Entity e : mc.level.getEntities((Entity) null, search, x -> x.isAlive() && x.isPickable() && x != mc.player && !x.isSpectator()
            && !(x instanceof Player) && !(x instanceof HangingEntity) && !(x instanceof ArmorStand))) {
            Optional<Vec3> hit = e.getBoundingBox().inflate(e.getPickRadius()).clip(eye, end);
            if (hit.isEmpty()) continue;
            double d = hit.get().distanceTo(eye);
            if (d < bestDist) {
                bestDist = d;
                best = e;
            }
        }
        return best == null ? null : new Target(null, best, bestDist, best instanceof ContainerEntity);
    }

    private static Target nearest(List<Target> options, boolean containersOnly) {
        Target best = null;
        for (Target t : options) {
            if (containersOnly && !t.container()) continue;
            if (best == null || t.distance() < best.distance()) best = t;
        }
        return best;
    }

    /** What a right-click would use right now, or null. */
    public Target target() {
        if (mc.player == null || mc.level == null) return null;
        Vec3 eye = mc.player.getEyePosition();
        Vec3 end = eye.add(mc.player.getLookAngle().scale(range.get()));
        BlockHitResult[] found = scanBlocks(eye, end);
        List<Target> options = new ArrayList<>(3);
        if (found[0] != null) options.add(new Target(found[0], null, found[0].getLocation().distanceTo(eye), true));
        if (found[1] != null) options.add(new Target(found[1], null, found[1].getLocation().distanceTo(eye), false));
        if (entities.get()) {
            Target e = findEntity(eye, end);
            if (e != null) options.add(e);
        }
        if (preferContainers.get()) {
            Target container = nearest(options, true);
            if (container != null) return container;
            Target snapped = snapToContainer(eye, end);
            return snapped != null ? snapped : nearest(options, false);
        }
        Target any = nearest(options, false);
        return any != null ? any : snapToContainer(eye, end);
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

        // vanilla handles it when the crosshair already points at a creature
        if (mc.hitResult instanceof EntityHitResult) return false;
        Target t = gh.target();
        if (mc.hitResult instanceof BlockHitResult bhr && bhr.getType() == HitResult.Type.BLOCK) {
            Kind under = gh.kindOf(mc.level.getBlockState(bhr.getBlockPos()), bhr.getBlockPos());
            // something you can see and use (a chest, a door, a lever) is vanilla's; a plain block
            // entity in the wall (a modpack's decorative block, a cable) only wins with nothing better behind it
            if (under == Kind.CONTAINER || under == Kind.SWITCH) return false;
            if (under == Kind.OTHER && (t == null || !t.container())) return false;
        }
        if (t == null) return false;
        if (t.block() != null) {
            mc.gameMode.useItemOn(mc.player, InteractionHand.MAIN_HAND, t.block());
        } else {
            mc.gameMode.interact(mc.player, t.entity(), InteractionHand.MAIN_HAND);
        }
        InvUtils.swing();
        return true;
    }

    /** Distance squared from a point to the nearest point of a block. */
    private static double distSqToBlock(BlockPos pos, Vec3 p) {
        double dx = Math.max(0, Math.max(pos.getX() - p.x, p.x - (pos.getX() + 1)));
        double dy = Math.max(0, Math.max(pos.getY() - p.y, p.y - (pos.getY() + 1)));
        double dz = Math.max(0, Math.max(pos.getZ() - p.z, p.z - (pos.getZ() + 1)));
        return dx * dx + dy * dy + dz * dz;
    }

    @Override
    public void onTick() {
        reachable.clear();
        if (mc.player == null || mc.level == null || !blocks.get()) return;
        if (!(preview.get() && showReachable.get()) && aimAssist.get() <= 0) return;
        Vec3 eye = mc.player.getEyePosition();
        double r = range.get();
        int minCx = Mth.floor(eye.x - r - 1) >> 4, maxCx = Mth.floor(eye.x + r + 1) >> 4;
        int minCz = Mth.floor(eye.z - r - 1) >> 4, maxCz = Mth.floor(eye.z + r + 1) >> 4;
        for (int cx = minCx; cx <= maxCx; cx++) {
            for (int cz = minCz; cz <= maxCz; cz++) {
                if (!mc.level.hasChunk(cx, cz)) continue;
                for (BlockEntity be : mc.level.getChunk(cx, cz).getBlockEntities().values()) {
                    BlockPos pos = be.getBlockPos();
                    if (distSqToBlock(pos, eye) > r * r) continue;
                    if (kindOf(be.getBlockState(), pos) == Kind.CONTAINER) reachable.add(pos.immutable());
                }
            }
        }
    }

    @Override
    public void onDisable() {
        reachable.clear();
    }

    /** Containers GhostHand could open from here (in-game test). */
    public int reachableCount() {
        return reachable.size();
    }

    @Override
    public void onRenderWorld(RenderContext context) {
        if (!preview.get() || mc.player == null || mc.player.isShiftKeyDown()) return;
        Target t = target();
        int c = color.get();
        if (showReachable.get()) {
            int faint = Theme.alpha(c, 0.4f);
            for (BlockPos pos : reachable) {
                if (t != null && t.block() != null && t.block().getBlockPos().equals(pos)) continue;
                AABB box = new AABB(pos);
                RenderUtils.world(context, (pose, buffer) -> RenderUtils.drawBox(pose, buffer, box, faint));
            }
        }
        if (t == null) return;
        AABB box = t.block() != null ? new AABB(t.block().getBlockPos()) : t.entity().getBoundingBox();
        RenderUtils.world(context, (pose, buffer) -> RenderUtils.drawBox(pose, buffer, box, c));
    }
}
