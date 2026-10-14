package dev.novautils.modules.world;

import dev.novautils.modules.Category;
import dev.novautils.modules.Module;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.item.Items;
import net.minecraft.world.phys.EntityHitResult;

/** Uses the name tag you hold on nearby unnamed mobs. Never targets players. */
public class AutoNametag extends Module {
    private int cooldown;

    public AutoNametag() {
        super("AutoNametag", "Applies your held name tag to nearby unnamed mobs.", Category.WORLD);
    }

    @Override
    public void onTick() {
        if (mc.player == null || mc.level == null || mc.gameMode == null) return;
        if (!mc.player.getMainHandItem().is(Items.NAME_TAG)) return;
        if (cooldown-- > 0) return;

        for (Entity e : mc.level.entitiesForRendering()) {
            if (e instanceof Mob mob && !mob.hasCustomName() && mc.player.distanceToSqr(e) <= 16) {
                mc.gameMode.interact(mc.player, e, InteractionHand.MAIN_HAND);
                dev.novautils.utils.InvUtils.swing();
                cooldown = 10;
                return;
            }
        }
    }
}
