package dev.novautils.modules.player;

import dev.novautils.utils.PlatformCompat;
import dev.novautils.modules.Category;
import dev.novautils.modules.Module;
import dev.novautils.settings.Setting;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;

/**
 * Keeps mining the block you look at, or keeps using your held item. It never attacks entities -
 * that is deliberate.
 */
public class AutoClicker extends Module {
    public enum Mode { MINE_BLOCKS, USE_ITEM }

    private final Setting<Mode> mode = register(Setting.builder(Mode.class, "mode")
        .description("Mine the targeted block, or right-click with your held item.")
        .defaultValue(Mode.MINE_BLOCKS).build());

    private final Setting<Integer> delay = register(Setting.builder(Integer.class, "delay-ticks")
        .description("Ticks between uses (USE_ITEM mode).")
        .defaultValue(4).range(1, 40).build());

    private int cooldown;

    public AutoClicker() {
        super("AutoClicker", "Automatically mines or uses items. Never hits entities.", Category.PLAYER);
    }

    @Override
    public void onTick() {
        if (mc.player == null || mc.gameMode == null || PlatformCompat.currentScreen(mc) != null) return;

        if (mode.get() == Mode.MINE_BLOCKS) {
            if (mc.hitResult instanceof BlockHitResult hit && hit.getType() == HitResult.Type.BLOCK) {
                dev.novautils.utils.MiningGuard.hold();
                if (!mc.gameMode.isDestroying()) mc.gameMode.startDestroyBlock(hit.getBlockPos(), hit.getDirection());
                mc.gameMode.continueDestroyBlock(hit.getBlockPos(), hit.getDirection());
                dev.novautils.utils.InvUtils.swing();
            }
        } else if (cooldown-- <= 0) {
            mc.gameMode.useItem(mc.player, InteractionHand.MAIN_HAND);
            cooldown = delay.get();
        }
    }
}
