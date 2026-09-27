package dev.novautils.modules.movement;

import dev.novautils.modules.Category;
import dev.novautils.modules.Module;
import dev.novautils.settings.Setting;

/** Remembers the last place you stood on solid ground and pulls you back there if you fall below the trigger height. */
public class AntiVoid extends Module {
    private final Setting<Integer> triggerY = register(Setting.builder(Integer.class, "trigger-y")
        .description("Y level below which you get pulled back up. Overworld void starts at -64.")
        .defaultValue(-60)
        .range(-256, 256)
        .build());

    private double safeX, safeY, safeZ;
    private boolean hasSafe = false;

    public AntiVoid() {
        super("AntiVoid", "Pulls you back to solid ground if you fall into the void.", Category.MOVEMENT);
    }

    @Override
    public void onEnable() {
        hasSafe = false;
    }

    @Override
    public void onTick() {
        if (mc.player == null) return;

        if (mc.player.onGround() && mc.player.getY() > triggerY.get() + 4) {
            safeX = mc.player.getX();
            safeY = mc.player.getY();
            safeZ = mc.player.getZ();
            hasSafe = true;
        }

        if (hasSafe && mc.player.getY() < triggerY.get()) {
            mc.player.teleportTo(safeX, safeY, safeZ);
            mc.player.setDeltaMovement(0, 0, 0);
        }
    }
}
