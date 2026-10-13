package dev.novautils.modules.movement;

import dev.novautils.modules.Category;
import dev.novautils.modules.Module;
import dev.novautils.settings.Setting;
import net.minecraft.world.entity.ai.attributes.Attributes;

/** Raises your step height so you walk up blocks without jumping. */
public class Step extends Module {
    private final Setting<Double> height = register(Setting.builder(Double.class, "height")
        .description("Step height in blocks (vanilla is 0.6).")
        .defaultValue(1.0).range(0.6, 2.5).build());

    public Step() {
        super("Step", "Walk up blocks without jumping.", Category.MOVEMENT);
    }

    private void apply(double value) {
        if (mc.player == null) return;
        mc.player.setMaxUpStep((float) value);
    }

    @Override public void onEnable() { apply(height.get()); }
    @Override public void onDisable() { apply(0.6); }
    @Override public void onTick() { apply(height.get()); }
}
