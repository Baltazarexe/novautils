package dev.novautils.modules.movement;

import dev.novautils.modules.Category;
import dev.novautils.modules.Module;

/**
 * Stops you at block edges at full walking speed, without crouching. The work is one hook in
 * PlayerMixin: vanilla's own sneak-edge clipping, switched on for you.
 */
public class SafeWalk extends Module {
    public SafeWalk() {
        super("SafeWalk", "Stops you from walking off edges (no crouching).", Category.MOVEMENT);
    }
}
