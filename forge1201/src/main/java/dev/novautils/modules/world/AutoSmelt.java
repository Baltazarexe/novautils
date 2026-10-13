package dev.novautils.modules.world;

import dev.novautils.mixin.FurnaceMenuInvoker;
import dev.novautils.modules.Category;
import dev.novautils.modules.Module;
import dev.novautils.settings.Setting;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.inventory.AbstractFurnaceMenu;
import net.minecraft.world.inventory.ClickType;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

/**
 * Open a furnace, smoker or blast furnace and it fills itself: smeltable items from your inventory
 * go in the top, fuel in the bottom, and finished items are taken out. What counts as smeltable and
 * as fuel comes from the furnace itself, so modded recipes and fuels work too.
 */
public class AutoSmelt extends Module {
    public enum Input { ORES, FOOD, ORES_AND_FOOD, EVERYTHING }
    public enum Fuel { COAL, ANY }

    private final Setting<Input> input = register(Setting.builder(Input.class, "input")
        .description("What to put in: raw ores, raw food, both, or anything that smelts.")
        .defaultValue(Input.ORES_AND_FOOD).build());

    private final Setting<Fuel> fuel = register(Setting.builder(Fuel.class, "fuel")
        .description("COAL: only coal, charcoal and coal blocks. ANY: any fuel (careful with logs/planks).")
        .defaultValue(Fuel.COAL).build());

    private final Setting<Boolean> takeOutput = register(Setting.builder(Boolean.class, "take-output")
        .description("Take finished items out.").defaultValue(true).build());

    private final Setting<Integer> delay = register(Setting.builder(Integer.class, "delay")
        .description("Ticks between clicks.").defaultValue(2).range(0, 20).build());

    private int wait;

    public AutoSmelt() {
        super("AutoSmelt", "Fills furnaces with ores/food and fuel, takes the output.", Category.WORLD);
    }

    private boolean wanted(ItemStack s) {
        String id = BuiltInRegistries.ITEM.getKey(s.getItem()).getPath();
        boolean ore = id.startsWith("raw_") || id.endsWith("_ore") || id.equals("ancient_debris");
        boolean food = s.isEdible();
        return switch (input.get()) {
            case ORES -> ore;
            case FOOD -> food;
            case ORES_AND_FOOD -> ore || food;
            case EVERYTHING -> true;
        };
    }

    private boolean fuelOk(ItemStack s) {
        if (fuel.get() == Fuel.ANY) return true;
        return s.is(Items.COAL) || s.is(Items.CHARCOAL) || s.is(Items.COAL_BLOCK);
    }

    private boolean fits(ItemStack slot, ItemStack s) {
        return slot.isEmpty() || (ItemStack.isSameItemSameTags(slot, s) && slot.getCount() < slot.getMaxStackSize());
    }

    private void click(AbstractFurnaceMenu menu, int slot, int button, ClickType type) {
        mc.gameMode.handleInventoryMouseClick(menu.containerId, slot, button, type, mc.player);
    }

    @Override
    public void onTick() {
        if (mc.player == null || mc.gameMode == null) return;
        if (!(mc.player.containerMenu instanceof AbstractFurnaceMenu menu)) return;
        if (wait-- > 0) return;
        wait = delay.get();
        if (!menu.getCarried().isEmpty()) return;
        var inv = (FurnaceMenuInvoker) menu;

        if (takeOutput.get() && menu.getSlot(2).hasItem()) {
            click(menu, 2, 0, ClickType.QUICK_MOVE);
            return;
        }
        // player inventory occupies slots 3..38 of a furnace menu
        ItemStack in = menu.getSlot(0).getItem();
        for (int i = 3; i < menu.slots.size(); i++) {
            ItemStack s = menu.getSlot(i).getItem();
            if (s.isEmpty() || !wanted(s) || !inv.novautils$canSmelt(s) || !fits(in, s)) continue;
            // shift-click routes smeltables to the input slot
            click(menu, i, 0, ClickType.QUICK_MOVE);
            return;
        }
        ItemStack fuelSlot = menu.getSlot(1).getItem();
        for (int i = 3; i < menu.slots.size(); i++) {
            ItemStack s = menu.getSlot(i).getItem();
            if (s.isEmpty() || !fuelOk(s) || !inv.novautils$isFuel(s) || !fits(fuelSlot, s)) continue;
            // pick up and drop on the fuel slot (shift-click would send smeltable fuels like logs to the top)
            click(menu, i, 0, ClickType.PICKUP);
            click(menu, 1, 0, ClickType.PICKUP);
            if (!menu.getCarried().isEmpty()) click(menu, i, 0, ClickType.PICKUP);
            return;
        }
    }
}
