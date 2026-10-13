package dev.novautils.modules.movement;

import dev.novautils.modules.Category;
import dev.novautils.modules.Module;

/** Holds the forward key for you - long highway/nether trips without keeping W pressed. */
public class AutoWalk extends Module {
    public AutoWalk() {
        super("AutoWalk", "Keeps walking forward. Press the module key again to stop.", Category.MOVEMENT);
    }

    @Override
    public void onTick() {
        if (mc.player == null) return;
        mc.options.keyUp.setDown(true);
    }

    @Override
    public void onDisable() {
        mc.options.keyUp.setDown(false);
    }
}
