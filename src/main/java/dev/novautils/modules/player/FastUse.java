package dev.novautils.modules.player;

import dev.novautils.mixin.MinecraftAccessor;
import dev.novautils.modules.Category;
import dev.novautils.modules.Module;

/** Removes the delay between right-click uses (placing blocks, throwing items, ...). */
public class FastUse extends Module {
    public FastUse() {
        super("FastUse", "No delay between item uses and block placements.", Category.PLAYER);
    }

    @Override
    public void onTick() {
        ((MinecraftAccessor) mc).novautils$setRightClickDelay(0);
    }
}
