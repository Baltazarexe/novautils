package dev.novautils.modules.movement;

import dev.novautils.modules.Category;
import dev.novautils.modules.Module;
import dev.novautils.settings.Setting;
import dev.novautils.utils.MoveUtils;
import net.minecraft.world.effect.MobEffects;

/**
 * Keeps you sprinting. STRICT follows vanilla's rules (forward only, enough food, not blind, not
 * sneaking or using an item); OMNI sprints in every direction.
 */
public class AutoSprint extends Module {
    public enum Mode { STRICT, OMNI }

    private final Setting<Mode> mode = register(Setting.builder(Mode.class, "mode")
        .description("STRICT: only when vanilla would let you. OMNI: any direction.")
        .defaultValue(Mode.STRICT).build());

    public AutoSprint() {
        super("AutoSprint", "Sprints automatically.", Category.MOVEMENT);
    }

    @Override
    public void onTick() {
        var p = mc.player;
        if (p == null) return;
        boolean canSprint = (p.getFoodData().getFoodLevel() > 6 || p.getAbilities().mayfly)
            && !p.hasEffect(MobEffects.BLINDNESS) && !p.isShiftKeyDown() && !p.isPassenger()
            && (!p.isUsingItem() || mc.player != null && dev.novautils.modules.ModuleManager.get().isActive(NoSlow.class));
        boolean go = mode.get() == Mode.OMNI ? MoveUtils.moving() : p.input.hasForwardImpulse();
        if (go && canSprint && !p.horizontalCollision) p.setSprinting(true);
    }
}
