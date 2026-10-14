package dev.novautils.modules.world;

import dev.novautils.modules.Category;
import dev.novautils.modules.Module;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.animal.Animal;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.EntityHitResult;

/** Feeds nearby adult animals the food you are holding so they breed. Never targets players. */
public class AutoBreed extends Module {
    private int cooldown;

    public AutoBreed() {
        super("AutoBreed", "Breeds nearby animals with the food in your hand.", Category.WORLD);
    }

    @Override
    public void onTick() {
        if (mc.player == null || mc.level == null || mc.gameMode == null) return;
        if (mc.player.getMainHandItem().isEmpty()) return;
        if (cooldown-- > 0) return;

        for (Entity e : mc.level.entitiesForRendering()) {
            if (e instanceof Player) continue;
            if (e instanceof Animal animal && animal.getAge() == 0 && animal.canFallInLove()
                && animal.isFood(mc.player.getMainHandItem()) && mc.player.distanceToSqr(e) <= 16) {
                mc.gameMode.interact(mc.player, e, InteractionHand.MAIN_HAND);
                dev.novautils.utils.InvUtils.swing();
                cooldown = 6;
                return;
            }
        }
    }
}
