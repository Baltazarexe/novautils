package dev.novautils.modules.world;

import dev.novautils.modules.Category;
import dev.novautils.modules.Module;
import dev.novautils.settings.Setting;
import dev.novautils.utils.InvUtils;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.CocoaBlock;
import net.minecraft.world.level.block.CropBlock;
import net.minecraft.world.level.block.NetherWartBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;

import java.util.HashMap;
import java.util.Map;

/**
 * Walk through your farm and it harvests what's ripe (wheat, carrots, potatoes, beetroot, nether
 * wart, cocoa, optionally pumpkins and melons) and replants the empty farmland / soul sand behind
 * you with seeds from your hotbar - the same crop that grew there when it knows, any seed otherwise.
 */
public class AutoFarm extends Module {
    private final Setting<Double> range = register(Setting.builder(Double.class, "range")
        .description("How far around you to farm.").defaultValue(4.0).range(1.0, 5.0).build());

    private final Setting<Boolean> harvest = register(Setting.builder(Boolean.class, "harvest")
        .description("Break fully grown crops.").defaultValue(true).build());

    private final Setting<Boolean> replant = register(Setting.builder(Boolean.class, "replant")
        .description("Plant seeds on empty farmland and soul sand.").defaultValue(true).build());

    private final Setting<Boolean> pumpkins = register(Setting.builder(Boolean.class, "pumpkins-melons")
        .description("Also break pumpkins and melons (only ones next to a stem).").defaultValue(false).build());

    private final Setting<Integer> perTick = register(Setting.builder(Integer.class, "actions-per-tick")
        .description("Blocks harvested or planted per tick.").defaultValue(2).range(1, 6).build());

    /** What grew where, so the right seed goes back. */
    private final Map<BlockPos, Item> lastCrop = new HashMap<>();

    public AutoFarm() {
        super("AutoFarm", "Harvests ripe crops and replants them.", Category.WORLD);
    }

    @Override
    public void onDisable() {
        lastCrop.clear();
    }

    private static Item seedFor(Block b) {
        if (b == Blocks.WHEAT) return Items.WHEAT_SEEDS;
        if (b == Blocks.CARROTS) return Items.CARROT;
        if (b == Blocks.POTATOES) return Items.POTATO;
        if (b == Blocks.BEETROOTS) return Items.BEETROOT_SEEDS;
        if (b == Blocks.NETHER_WART) return Items.NETHER_WART;
        return null;
    }

    private boolean ripe(BlockPos pos, BlockState s) {
        Block b = s.getBlock();
        if (b instanceof CropBlock crop) return crop.isMaxAge(s);
        if (b instanceof NetherWartBlock) return s.getValue(NetherWartBlock.AGE) >= 3;
        if (b instanceof CocoaBlock) return s.getValue(CocoaBlock.AGE) >= 2;
        if (pumpkins.get() && (b == Blocks.PUMPKIN || b == Blocks.MELON)) {
            for (Direction d : Direction.Plane.HORIZONTAL) {
                Block n = mc.level.getBlockState(pos.relative(d)).getBlock();
                if (n == Blocks.ATTACHED_PUMPKIN_STEM || n == Blocks.ATTACHED_MELON_STEM) return true;
            }
        }
        return false;
    }

    private int hotbarSlot(Item preferred, boolean soulSand) {
        var inv = mc.player.getInventory();
        if (preferred != null) {
            for (int i = 0; i < 9; i++) if (inv.getItem(i).is(preferred)) return i;
        }
        for (int i = 0; i < 9; i++) {
            var st = inv.getItem(i);
            if (soulSand ? st.is(Items.NETHER_WART)
                : st.is(Items.WHEAT_SEEDS) || st.is(Items.CARROT) || st.is(Items.POTATO) || st.is(Items.BEETROOT_SEEDS)) return i;
        }
        return -1;
    }

    @Override
    public void onTick() {
        if (mc.player == null || mc.level == null || mc.gameMode == null || dev.novautils.utils.PlatformCompat.currentScreen(mc) != null) return;
        int budget = perTick.get();
        int r = (int) Math.ceil(range.get());
        Vec3 eyes = mc.player.getEyePosition();
        BlockPos base = mc.player.blockPosition();

        for (BlockPos pos : BlockPos.betweenClosed(base.offset(-r, -2, -r), base.offset(r, 2, r))) {
            if (budget <= 0) return;
            if (eyes.distanceTo(Vec3.atCenterOf(pos)) > range.get()) continue;
            BlockState s = mc.level.getBlockState(pos);

            if (harvest.get() && ripe(pos, s)) {
                Item seed = seedFor(s.getBlock());
                if (seed != null) lastCrop.put(pos.immutable(), seed);
                mc.gameMode.startDestroyBlock(pos.immutable(), Direction.UP);
                InvUtils.swing();
                budget--;
                continue;
            }

            if (!replant.get() || !s.isAir()) continue;
            BlockState below = mc.level.getBlockState(pos.below());
            boolean farmland = below.is(Blocks.FARMLAND), soul = below.is(Blocks.SOUL_SAND);
            if (!farmland && !soul) continue;
            int slot = hotbarSlot(lastCrop.get(pos), soul);
            if (slot < 0) continue;

            int original = mc.player.getInventory().selected;
            if (slot != original) InvUtils.selectHotbar(slot);
            BlockPos ground = pos.below().immutable();
            mc.gameMode.useItemOn(mc.player, InteractionHand.MAIN_HAND,
                new BlockHitResult(Vec3.atCenterOf(ground).add(0, 0.5, 0), Direction.UP, ground, false));
            if (slot != original) InvUtils.selectHotbar(original);
            lastCrop.remove(pos);
            budget--;
        }
    }
}
