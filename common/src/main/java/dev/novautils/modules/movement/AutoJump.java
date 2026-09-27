package dev.novautils.modules.movement;

import dev.novautils.modules.Category;
import dev.novautils.modules.Module;

/** Thin toggle wrapper around vanilla's own auto-jump option, so it can have a keybind like everything else. */
public class AutoJump extends Module {
    private boolean previous;

    public AutoJump() {
        super("AutoJump", "Automatically jumps over blocks in your way.", Category.MOVEMENT);
    }

    @Override
    public void onEnable() {
        previous = mc.options.autoJump().get();
        mc.options.autoJump().set(true);
    }

    @Override
    public void onDisable() {
        mc.options.autoJump().set(previous);
    }
}
