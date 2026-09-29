package dev.novautils.modules.movement;

import dev.novautils.modules.Category;
import dev.novautils.modules.Module;

/** Walk at full speed while eating, drinking, blocking or drawing a bow (hook in LocalPlayerMixin). */
public class NoSlow extends Module {
    public NoSlow() {
        super("NoSlow", "No slowdown while using items.", Category.MOVEMENT);
    }
}
