package dev.novautils.modules.combat;

import dev.novautils.modules.Category;
import dev.novautils.modules.Module;
import dev.novautils.settings.Setting;
import dev.novautils.utils.BlockUtils;
import dev.novautils.utils.Rotations;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import java.util.LinkedHashSet;
import java.util.Set;

/**
 * Boxes your feet in with blast-proof blocks (defensive; doesn't touch anyone else). Picks
 * obsidian from the hotbar by itself, centres you on the block first so four blocks are enough,
 * handles standing across two blocks, adds a support block under a gap if needed, and switches
 * off when you jump or walk away so it doesn't keep building behind you.
 */
public class Surround extends Module {
    public enum Blocks { OBSIDIAN, ANY }

    private final Setting<Blocks> blocks = register(Setting.builder(Blocks.class, "blocks")
        .description("OBSIDIAN: obsidian, crying obsidian or ender chests. ANY: any full block.")
        .defaultValue(Blocks.OBSIDIAN).build());

    private final Setting<Boolean> center = register(Setting.builder(Boolean.class, "center")
        .description("Move to the middle of the block first.")
        .defaultValue(true).build());

    private final Setting<Boolean> rotate = register(Setting.builder(Boolean.class, "rotate")
        .description("Face each block while placing (one per tick when on).")
        .defaultValue(true).build());

    private final Setting<Integer> perTick = register(Setting.builder(Integer.class, "blocks-per-tick")
        .description("Blocks placed per tick when rotate is off.")
        .defaultValue(4).range(1, 8)
        .visible(() -> !rotate.get()).build());

    private final Setting<Boolean> disableOnLeave = register(Setting.builder(Boolean.class, "disable-on-leave")
        .description("Turn off when you jump or leave the block.")
        .defaultValue(true).build());

    private BlockPos startPos;

    public Surround() {
        super("Surround", "Surrounds your feet with obsidian.", Category.COMBAT);
    }

    private boolean usable(ItemStack stack) {
        if (blocks.get() == Blocks.ANY) return BlockUtils.isBuildingBlock(stack);
        return stack.is(Items.OBSIDIAN) || stack.is(Items.CRYING_OBSIDIAN) || stack.is(Items.ENDER_CHEST);
    }

    @Override
    public void onEnable() {
        startPos = null;
        if (mc.player == null) return;
        startPos = mc.player.blockPosition();
        if (center.get()) {
            double cx = startPos.getX() + 0.5, cz = startPos.getZ() + 0.5;
            mc.player.setDeltaMovement(0, mc.player.getDeltaMovement().y, 0);
            mc.player.setPos(cx, mc.player.getY(), cz);
        }
    }

    /** Every block around the feet, including when the hitbox overlaps two or four blocks. */
    private Set<BlockPos> targets() {
        var box = mc.player.getBoundingBox();
        int y = mc.player.blockPosition().getY();
        Set<BlockPos> feet = new LinkedHashSet<>();
        for (int x = (int) Math.floor(box.minX + 0.001); x <= (int) Math.floor(box.maxX - 0.001); x++) {
            for (int z = (int) Math.floor(box.minZ + 0.001); z <= (int) Math.floor(box.maxZ - 0.001); z++) {
                feet.add(new BlockPos(x, y, z));
            }
        }
        Set<BlockPos> out = new LinkedHashSet<>();
        for (BlockPos f : feet) {
            for (Direction d : Direction.Plane.HORIZONTAL) {
                BlockPos t = f.relative(d);
                if (!feet.contains(t)) out.add(t);
            }
        }
        return out;
    }

    @Override
    public void onTick() {
        var p = mc.player;
        if (p == null || mc.level == null || mc.gameMode == null) return;

        if (disableOnLeave.get() && startPos != null
            && (p.getY() > startPos.getY() + 0.5 || !p.blockPosition().atY(startPos.getY()).equals(startPos))) {
            setEnabled(false);
            return;
        }
        if (Rotations.busy()) return;

        int slot = BlockUtils.findHotbar(this::usable);
        if (slot == -1) return;

        int budget = rotate.get() ? 1 : perTick.get();
        for (BlockPos t : targets()) {
            if (budget <= 0) return;
            if (!BlockUtils.replaceable(t) || !BlockUtils.freeOfEntities(t)) continue;
            BlockPos pos = BlockUtils.placeSide(t) != null ? t : t.below();
            if (pos != t && (!BlockUtils.replaceable(pos) || BlockUtils.placeSide(pos) == null)) continue;
            if (BlockUtils.place(pos, slot, rotate.get(), true)) budget--;
        }
    }
}
