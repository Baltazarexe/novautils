package dev.novautils.modules.player;

import dev.novautils.gui.Toasts;
import dev.novautils.modules.Category;
import dev.novautils.modules.Module;
import dev.novautils.settings.Setting;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.Container;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerInput;
import net.minecraft.world.inventory.InventoryMenu;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * Sorts the open chest (or, with no chest open, your main inventory - never the hotbar or armour)
 * by item, same items together, empty slots last. Press its keybind (it also works inside chests
 * and the inventory screen) or type {@code .sort}; it switches itself off when done. Works by
 * normal clicks, a few per tick.
 */
public class InventorySort extends Module {
    private final Setting<Integer> perTick = register(Setting.builder(Integer.class, "swaps-per-tick")
        .description("How many slots to fix per tick (lower is gentler on strict servers).").defaultValue(6).range(1, 30).build());

    private List<Integer> slots = List.of();
    private AbstractContainerMenu menu;
    private int swaps;

    public InventorySort() {
        super("InventorySort", "Sorts the open chest or your inventory (keybind or .sort).", Category.PLAYER);
    }

    @Override
    public void onEnable() {
        menu = null;
        swaps = 0;
    }

    private static String key(ItemStack s) {
        if (s.isEmpty()) return "￿";
        // group by creative-tab-ish order: item id, then components so enchanted copies sit together
        return BuiltInRegistries.ITEM.getKey(s.getItem()).getPath() + "#" + s.getComponents().hashCode();
    }

    private List<Integer> sortableSlots(AbstractContainerMenu m) {
        List<Integer> out = new ArrayList<>();
        Container playerInv = mc.player.getInventory();
        if (m instanceof InventoryMenu) {
            for (int i = 9; i <= 35; i++) out.add(i); // main inventory, not hotbar (36-44)
        } else {
            for (int i = 0; i < m.slots.size(); i++) {
                Slot s = m.slots.get(i);
                if (s.container != playerInv) out.add(i);
            }
        }
        return out;
    }

    @Override
    public void onTick() {
        if (mc.player == null || mc.gameMode == null) {
            setEnabled(false);
            return;
        }
        AbstractContainerMenu m = mc.player.containerMenu;
        if (menu == null) {
            menu = m;
            slots = sortableSlots(m);
        }
        if (m != menu || !m.getCarried().isEmpty()) {
            setEnabled(false);
            return;
        }

        List<String> target = new ArrayList<>();
        for (int i : slots) target.add(key(m.getSlot(i).getItem()));
        target.sort(Comparator.naturalOrder());

        int budget = perTick.get();
        for (int k = 0; k < slots.size() && budget > 0; k++) {
            int i = slots.get(k);
            if (key(m.getSlot(i).getItem()).equals(target.get(k))) continue;
            int from = -1;
            for (int k2 = k + 1; k2 < slots.size(); k2++) {
                int j = slots.get(k2);
                if (key(m.getSlot(j).getItem()).equals(target.get(k)) && !key(m.getSlot(j).getItem()).equals(target.get(k2))) {
                    from = j;
                    break;
                }
            }
            if (from == -1) continue;
            // pick up the right stack, drop it here (picking up what was here), put that back
            click(m, from);
            click(m, i);
            if (!m.getCarried().isEmpty()) click(m, from);
            swaps++;
            budget--;
        }
        if (budget > 0) {
            Toasts.push(swaps == 0 ? "Already sorted" : "Sorted (" + swaps + " moves)", 0xFF66DD77);
            setEnabled(false);
        }
    }

    private void click(AbstractContainerMenu m, int slot) {
        mc.gameMode.handleContainerInput(m.containerId, slot, 0, ContainerInput.PICKUP, mc.player);
    }
}
