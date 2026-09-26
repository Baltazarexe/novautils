package dev.novautils.modules.combat;

import dev.novautils.modules.Category;
import dev.novautils.modules.Module;
import dev.novautils.settings.Setting;
import dev.novautils.utils.InvUtils;
import net.minecraft.world.item.Items;

/** Keeps a Totem of Undying in your offhand (swaps one in from your inventory). Pure self-defense. */
public class AutoTotem extends Module {
    private final Setting<Integer> healthThreshold = register(Setting.builder(Integer.class, "health-threshold")
        .description("Only equip when health is at or below this. 20 = always keep one ready.")
        .defaultValue(20).range(1, 20).build());

    private int cooldown;

    public AutoTotem() {
        super("AutoTotem", "Keeps a totem in your offhand.", Category.COMBAT);
    }

    @Override
    public void onTick() {
        if (!InvUtils.canClick()) return;
        if (cooldown-- > 0) return;
        if (mc.player.getOffhandItem().is(Items.TOTEM_OF_UNDYING)) return;
        if (mc.player.getHealth() > healthThreshold.get()) return;

        var inv = mc.player.getInventory();
        for (int i = 0; i < 36; i++) {
            if (inv.getItem(i).is(Items.TOTEM_OF_UNDYING)) {
                InvUtils.swap(InvUtils.menuSlot(i), 40);
                cooldown = 4;
                return;
            }
        }
    }
}
