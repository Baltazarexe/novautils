package dev.novautils.modules.world;

import dev.novautils.modules.Category;
import dev.novautils.modules.Module;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.Shearable;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Items;
import net.minecraft.world.phys.EntityHitResult;

/** Shears nearby sheep (and other shearable mobs) that are ready. Hold shears. Never targets players. */
public class AutoShearer extends Module {
    private int cooldown;

    public AutoShearer() {
        super("AutoShearer", "Shears nearby sheep automatically while you hold shears.", Category.WORLD);
    }

    @Override
    public void onTick() {
        if (mc.player == null || mc.level == null || mc.gameMode == null) return;
        if (!mc.player.getMainHandItem().is(Items.SHEARS)) return;
        if (cooldown-- > 0) return;

        for (Entity e : mc.level.entitiesForRendering()) {
            if (e instanceof Player) continue;
            if (e instanceof Shearable s && s.readyForShearing() && mc.player.distanceToSqr(e) <= 16) {
                mc.gameMode.interact(mc.player, e, InteractionHand.MAIN_HAND);
                dev.novautils.utils.InvUtils.swing();
                cooldown = 6;
                return;
            }
        }
    }
}
