package dev.novautils.modules.world;

import dev.novautils.modules.Category;
import dev.novautils.modules.Module;
import dev.novautils.settings.Setting;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientboundSoundPacket;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.Items;

import java.util.concurrent.ThreadLocalRandom;

/**
 * Fishes for you: listens for the splash a bite makes right at your bobber, reels in, and casts
 * again after a short (slightly random) pause. Hold a fishing rod in your main hand.
 */
public class AutoFish extends Module {
    private final Setting<Integer> recastDelay = register(Setting.builder(Integer.class, "recast-delay")
        .description("Ticks to wait before casting again.").defaultValue(12).range(4, 60).build());

    private final Setting<Boolean> autoCast = register(Setting.builder(Boolean.class, "auto-cast")
        .description("Also cast when the rod isn't out (e.g. right after you switch to it).").defaultValue(true).build());

    private int reelIn = -1, recast = -1, idle;

    public AutoFish() {
        super("AutoFish", "Reels in and recasts the fishing rod for you.", Category.WORLD);
    }

    @Override
    public void onEnable() {
        reelIn = -1;
        recast = -1;
        idle = 0;
    }

    private boolean holdingRod() {
        return mc.player != null && mc.player.getMainHandItem().is(Items.FISHING_ROD);
    }

    @Override
    public boolean onReceivePacket(Packet<?> packet) {
        if (!(packet instanceof ClientboundSoundPacket sound) || mc.player == null) return false;
        var hook = mc.player.fishing;
        if (hook == null || !holdingRod() || reelIn >= 0) return false;
        if (sound.getSound().value() != SoundEvents.FISHING_BOBBER_SPLASH) return false;
        if (hook.distanceToSqr(sound.getX(), sound.getY(), sound.getZ()) > 2.25) return false;
        // a human reacts in a few ticks, not instantly
        reelIn = 2 + ThreadLocalRandom.current().nextInt(3);
        return false;
    }

    private void use() {
        mc.gameMode.useItem(mc.player, InteractionHand.MAIN_HAND);
        dev.novautils.utils.InvUtils.swing();
    }

    @Override
    public void onTick() {
        if (mc.player == null || mc.gameMode == null || !holdingRod()) {
            reelIn = recast = -1;
            return;
        }
        if (reelIn >= 0 && reelIn-- == 0) {
            if (mc.player.fishing != null) use();
            recast = recastDelay.get() + ThreadLocalRandom.current().nextInt(6);
            return;
        }
        if (recast >= 0 && recast-- == 0) {
            if (mc.player.fishing == null) use();
            return;
        }
        // nothing out and nothing scheduled: cast (after a moment, so switching to the rod feels natural)
        if (autoCast.get() && mc.player.fishing == null && reelIn < 0 && recast < 0) {
            if (++idle > 20) {
                idle = 0;
                use();
            }
        } else {
            idle = 0;
        }
    }
}
