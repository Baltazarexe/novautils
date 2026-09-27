package dev.novautils.modules.movement;

import dev.novautils.modules.Category;
import dev.novautils.modules.Module;

public class Sneak extends Module {
    public Sneak() {
        super("Sneak", "Holds sneak for you.", Category.MOVEMENT);
    }

    @Override public void onTick() { mc.options.keyShift.setDown(true); }
    @Override public void onDisable() { mc.options.keyShift.setDown(false); }
}
