package dev.novautils.modules.movement;

import dev.novautils.modules.Category;
import dev.novautils.modules.Module;
import dev.novautils.settings.Setting;

/**
 * Creative-style flight in survival. Client-authoritative movement is mostly gone in modern
 * Minecraft, so a real server will usually correct/kick you for this unless it explicitly allows
 * flight - that's the server's call to enforce, same as any other capability toggle in this mod.
 */
public class Flight extends Module {
    private final Setting<Double> speed = register(Setting.builder(Double.class, "speed")
        .description("Flying speed.")
        .defaultValue(0.1)
        .range(0.02, 1.0)
        .build());

    public Flight() {
        super("Flight", "Lets you fly like in creative mode.", Category.MOVEMENT);
    }

    @Override
    public void onEnable() {
        if (mc.player == null) return;
        var abilities = mc.player.getAbilities();
        abilities.mayfly = true;
        abilities.flying = true;
        abilities.setFlyingSpeed(speed.get().floatValue());
        mc.player.onUpdateAbilities();
    }

    @Override
    public void onDisable() {
        if (mc.player == null) return;
        var abilities = mc.player.getAbilities();
        boolean creativeLike = mc.player.getAbilities().instabuild;
        abilities.flying = false;
        if (!creativeLike) abilities.mayfly = false;
        abilities.setFlyingSpeed(0.05f);
        mc.player.onUpdateAbilities();
    }

    @Override
    public void onTick() {
        if (mc.player == null) return;
        mc.player.getAbilities().setFlyingSpeed(speed.get().floatValue());
    }
}
