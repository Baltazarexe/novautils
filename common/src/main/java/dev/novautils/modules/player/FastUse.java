package dev.novautils.modules.player;

import dev.novautils.mixin.MinecraftAccessor;
import dev.novautils.modules.Category;
import dev.novautils.modules.Module;
import dev.novautils.settings.Setting;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

/** Removes the 4-tick delay between right-click uses - for everything, or only XP bottles / blocks. */
public class FastUse extends Module {
    public enum Mode { ALL, XP_BOTTLES, BLOCKS, XP_AND_BLOCKS }

    private final Setting<Mode> mode = register(Setting.builder(Mode.class, "mode")
        .description("What gets the no-delay treatment.")
        .defaultValue(Mode.XP_AND_BLOCKS).build());

    public FastUse() {
        super("FastUse", "No delay between item uses and block placements.", Category.PLAYER);
    }

    private boolean applies(ItemStack stack) {
        boolean xp = stack.is(Items.EXPERIENCE_BOTTLE);
        boolean block = stack.getItem() instanceof BlockItem;
        return switch (mode.get()) {
            case ALL -> true;
            case XP_BOTTLES -> xp;
            case BLOCKS -> block;
            case XP_AND_BLOCKS -> xp || block;
        };
    }

    @Override
    public void onTick() {
        if (mc.player == null) return;
        if (applies(mc.player.getMainHandItem()) || applies(mc.player.getOffhandItem())) {
            ((MinecraftAccessor) mc).novautils$setRightClickDelay(0);
        }
    }
}
