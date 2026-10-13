package dev.novautils.modules.combat;

import dev.novautils.modules.Category;
import dev.novautils.modules.Module;
import dev.novautils.settings.Setting;
import dev.novautils.utils.InvUtils;
import net.minecraft.world.item.Items;

/**
 * Keeps a Totem of Undying in your offhand. In SMART mode the offhand stays free for something
 * else (a shield, gapples) until you're in danger: low health, a long fall, or elytra flight.
 */
public class AutoTotem extends Module {
    public enum Mode { ALWAYS, SMART }

    private final Setting<Mode> mode = register(Setting.builder(Mode.class, "mode")
        .description("ALWAYS: keep one in. SMART: only when low, falling far or elytra flying.")
        .defaultValue(Mode.ALWAYS).build());

    private final Setting<Integer> health = register(Setting.builder(Integer.class, "health")
        .description("SMART: put a totem in at or below this much health.")
        .defaultValue(10).range(1, 20)
        .visible(() -> mode.get() == Mode.SMART).build());

    private final Setting<Integer> delay = register(Setting.builder(Integer.class, "delay-ticks")
        .description("Ticks between swaps (0 = as fast as possible).")
        .defaultValue(0).range(0, 10).build());

    private int cooldown;

    public AutoTotem() {
        super("AutoTotem", "Keeps a totem in your offhand.", Category.COMBAT);
    }

    private boolean danger() {
        var p = mc.player;
        return p.getHealth() + p.getAbsorptionAmount() <= health.get()
            || p.fallDistance > 8 || p.isFallFlying();
    }

    @Override
    public void onTick() {
        if (!InvUtils.canClick()) return;
        if (cooldown-- > 0) return;
        if (mc.player.getOffhandItem().is(Items.TOTEM_OF_UNDYING)) return;
        if (mode.get() == Mode.SMART && !danger()) return;

        var inv = mc.player.getInventory();
        for (int i = 0; i < 36; i++) {
            if (inv.getItem(i).is(Items.TOTEM_OF_UNDYING)) {
                InvUtils.swap(InvUtils.menuSlot(i), 40);
                cooldown = delay.get();
                return;
            }
        }
    }
}
