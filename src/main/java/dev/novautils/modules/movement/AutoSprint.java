package dev.novautils.modules.movement;

import dev.novautils.modules.Category;
import dev.novautils.modules.Module;

/** Keeps you sprinting while moving forward, as long as you have enough food. */
public class AutoSprint extends Module {
    public AutoSprint() {
        super("AutoSprint", "Sprints automatically while moving forward.", Category.MOVEMENT);
    }

    @Override
    public void onTick() {
        if (mc.player == null) return;
        if (mc.options.keyUp.isDown() && !mc.player.isCrouching() && mc.player.getFoodData().getFoodLevel() > 6) {
            mc.player.setSprinting(true);
        }
    }
}
