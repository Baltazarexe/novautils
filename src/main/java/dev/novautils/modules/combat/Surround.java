package dev.novautils.modules.combat;

import dev.novautils.modules.Category;
import dev.novautils.modules.Module;
import dev.novautils.settings.Setting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;

/**
 * Places blocks around your feet for protection (classic PvP survival technique). This is
 * self-defense - it doesn't interact with other players at all, only your own surroundings.
 * Requires a placeable block already in your main hand; it won't dig through your inventory
 * for one (keeps this simple and predictable).
 */
public class Surround extends Module {
    private final Setting<Integer> delayTicks = register(Setting.builder(Integer.class, "delay-ticks")
        .description("Ticks to wait between placement passes.")
        .defaultValue(4)
        .range(2, 20)
        .build());

    private int cooldown = 0;

    public Surround() {
        super("Surround", "Places blocks around your feet for protection. Hold a block to use it.", Category.COMBAT);
    }

    @Override
    public void onTick() {
        if (mc.player == null || mc.level == null || mc.gameMode == null) return;
        if (!(mc.player.getMainHandItem().getItem() instanceof BlockItem)) return;
        if (cooldown-- > 0) return;

        BlockPos playerPos = mc.player.blockPosition();
        boolean placedAny = false;

        for (Direction dir : Direction.Plane.HORIZONTAL) {
            BlockPos target = playerPos.relative(dir);
            if (!mc.level.getBlockState(target).canBeReplaced()) continue;

            BlockPos below = target.below();
            if (!mc.level.getBlockState(below).isSolid()) continue;

            Vec3 hitPos = new Vec3(below.getX() + 0.5, below.getY() + 1.0, below.getZ() + 0.5);
            BlockHitResult hitResult = new BlockHitResult(hitPos, Direction.UP, below, false);

            mc.gameMode.useItemOn(mc.player, InteractionHand.MAIN_HAND, hitResult);
            mc.player.swing(InteractionHand.MAIN_HAND);
            placedAny = true;
        }

        if (placedAny) cooldown = delayTicks.get();
    }
}
