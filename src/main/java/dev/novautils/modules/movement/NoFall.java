package dev.novautils.modules.movement;

import dev.novautils.modules.Category;
import dev.novautils.modules.Module;

/**
 * Keeps resetting the client's fall distance. Whether that actually prevents damage depends on the
 * server: servers that track your falling themselves will still hurt you. It reliably removes
 * client-side fall damage/animation, and works where the server trusts the client's state.
 */
public class NoFall extends Module {
    public NoFall() {
        super("NoFall", "Resets your fall distance (server may still apply damage).", Category.MOVEMENT);
    }

    @Override
    public void onTick() {
        if (mc.player == null) return;
        if (mc.player.fallDistance > 2.0) {
            mc.player.resetFallDistance();
        }
    }
}
