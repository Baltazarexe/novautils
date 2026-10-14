package dev.novautils.modules.world;

import dev.novautils.modules.Category;
import dev.novautils.modules.Module;
import dev.novautils.settings.Setting;
import net.minecraft.world.inventory.ClickType;
import net.minecraft.world.inventory.CraftingMenu;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import java.util.ArrayList;
import java.util.List;

/**
 * Open a crafting table and it packs your loose resources into blocks: ingots, nuggets, gems,
 * redstone, coal, and so on. It spreads a stack evenly over the 3x3 grid (the same drag a player
 * does) and shift-clicks the result, so it's as fast as the server allows.
 */
public class AutoCompact extends Module {
    private final Setting<Boolean> metals = register(Setting.builder(Boolean.class, "metals")
        .description("Iron, gold, copper ingots and iron/gold nuggets.").defaultValue(true).build());

    private final Setting<Boolean> gems = register(Setting.builder(Boolean.class, "gems")
        .description("Diamonds, emeralds, lapis, redstone.").defaultValue(true).build());

    private final Setting<Boolean> coal = register(Setting.builder(Boolean.class, "coal")
        .description("Coal into coal blocks.").defaultValue(false).build());

    private final Setting<Boolean> farm = register(Setting.builder(Boolean.class, "farm")
        .description("Wheat into hay, bone meal into bone blocks, dried kelp, slime balls.").defaultValue(false).build());

    private final Setting<Integer> delay = register(Setting.builder(Integer.class, "delay")
        .description("Ticks between crafts.").defaultValue(3).range(1, 20).build());

    private int wait;
    /** Ticks left to wait for the server to show the crafting result after a fill. */
    private int pending;

    public AutoCompact() {
        super("AutoCompact", "Crafts ingots, gems etc. into blocks at a crafting table.", Category.WORLD);
    }

    private List<Item> items() {
        List<Item> out = new ArrayList<>();
        if (metals.get()) out.addAll(List.of(Items.IRON_INGOT, Items.GOLD_INGOT, Items.COPPER_INGOT, Items.IRON_NUGGET, Items.GOLD_NUGGET));
        if (gems.get()) out.addAll(List.of(Items.DIAMOND, Items.EMERALD, Items.LAPIS_LAZULI, Items.REDSTONE));
        if (coal.get()) out.add(Items.COAL);
        if (farm.get()) out.addAll(List.of(Items.WHEAT, Items.BONE_MEAL, Items.DRIED_KELP, Items.SLIME_BALL));
        return out;
    }

    private void click(CraftingMenu m, int slot, int button, ClickType type) {
        mc.gameMode.handleInventoryMouseClick(m.containerId, slot, button, type, mc.player);
    }

    @Override
    public void onTick() {
        if (mc.player == null || mc.gameMode == null) return;
        if (!(mc.player.containerMenu instanceof CraftingMenu m)) return;
        if (wait-- > 0 && pending == 0) return;
        wait = delay.get();
        if (!m.getCarried().isEmpty()) return;

        // the server works out the result: wait for it, then shift-click it to craft them all
        if (pending > 0) {
            if (m.getSlot(0).hasItem()) {
                click(m, 0, 0, ClickType.QUICK_MOVE);
                pending = 0;
                return;
            }
            if (--pending > 0) return;
        }

        // anything left on the grid (leftovers, or no recipe for it) goes back first
        for (int g = 1; g <= 9; g++) {
            if (m.getSlot(g).hasItem()) {
                click(m, g, 0, ClickType.QUICK_MOVE);
                return;
            }
        }

        List<Item> wanted = items();
        for (int i = 10; i < m.slots.size(); i++) {
            ItemStack s = m.getSlot(i).getItem();
            if (s.getCount() < 9 || !wanted.contains(s.getItem())) continue;
            // pick the stack up and drag it evenly over the 9 grid slots
            click(m, i, 0, ClickType.PICKUP);
            click(m, -999, 0, ClickType.QUICK_CRAFT);           // start, even split
            for (int g = 1; g <= 9; g++) click(m, g, 1, ClickType.QUICK_CRAFT);
            click(m, -999, 2, ClickType.QUICK_CRAFT);           // end
            if (!m.getCarried().isEmpty()) click(m, i, 0, ClickType.PICKUP);
            pending = 20;
            return;
        }
    }
}
