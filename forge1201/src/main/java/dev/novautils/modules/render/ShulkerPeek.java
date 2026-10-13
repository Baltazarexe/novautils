package dev.novautils.modules.render;

import dev.novautils.gui.Theme;
import dev.novautils.modules.Category;
import dev.novautils.modules.Module;
import dev.novautils.settings.Setting;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.core.NonNullList;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.ShulkerBoxBlock;

/**
 * Hover a shulker box in any inventory or chest and see its 27 slots as a little grid, tinted
 * with the box colour, instead of the five-line text tooltip. ContainerScreenMixin calls
 * {@link #draw}; hold the "vanilla" key (shift) to get the normal tooltip back.
 */
public class ShulkerPeek extends Module {
    private final Setting<Boolean> shiftForVanilla = register(Setting.builder(Boolean.class, "shift-for-tooltip")
        .description("Hold shift to see the normal tooltip instead.").defaultValue(true).build());

    private final Setting<Boolean> tint = register(Setting.builder(Boolean.class, "tint")
        .description("Colour the panel like the shulker box.").defaultValue(true).build());

    private final Setting<Boolean> showEmpty = register(Setting.builder(Boolean.class, "empty-boxes")
        .description("Also show the grid for empty boxes.").defaultValue(false).build());

    private static final int SLOT = 18, COLS = 9;

    public ShulkerPeek() {
        super("ShulkerPeek", "See what's inside a shulker box by hovering it.", Category.RENDER);
    }

    public static boolean isShulker(ItemStack stack) {
        return stack.getItem() instanceof BlockItem b && b.getBlock() instanceof ShulkerBoxBlock;
    }

    /** The 27 slots of a shulker box item (all empty if it has none). */
    public static NonNullList<ItemStack> contents(ItemStack stack) {
        NonNullList<ItemStack> items = NonNullList.withSize(27, ItemStack.EMPTY);
        // 1.20.1 keeps a shulker item's contents as block entity NBT: BlockEntityTag.Items
        var tag = net.minecraft.world.item.BlockItem.getBlockEntityData(stack);
        if (tag != null && tag.contains("Items", 9)) net.minecraft.world.ContainerHelper.loadAllItems(tag, items);
        return items;
    }

    /** Returns true when it drew the preview (the vanilla tooltip is then skipped). */
    public boolean draw(GuiGraphics g, ItemStack stack, int mouseX, int mouseY) {
        boolean echest = stack.is(net.minecraft.world.item.Items.ENDER_CHEST);
        if (!isShulker(stack) && !echest) return false;
        if (shiftForVanilla.get() && net.minecraft.client.gui.screens.Screen.hasShiftDown()) return false;
        NonNullList<ItemStack> items;
        if (echest) {
            // what ChestTracker saw the last time you opened your ender chest
            var tracker = dev.novautils.modules.ModuleManager.get().get(dev.novautils.modules.misc.ChestTracker.class);
            var seen = tracker.isEnabled() ? tracker.enderChest() : null;
            if (seen == null) return false;
            items = NonNullList.withSize(27, ItemStack.EMPTY);
            for (int i = 0; i < Math.min(27, seen.size()); i++) items.set(i, seen.get(i));
        } else {
            items = contents(stack);
        }
        boolean any = false;
        for (ItemStack s : items) any |= !s.isEmpty();
        if (!any && !showEmpty.get()) return false;

        int rows = (items.size() + COLS - 1) / COLS;
        int w = COLS * SLOT + 10, h = rows * SLOT + 22;
        int x = mouseX + 12, y = mouseY - 12;
        if (x + w > g.guiWidth() - 2) x = mouseX - 12 - w;
        y = Math.max(2, Math.min(y, g.guiHeight() - h - 2));
        x = Math.max(2, x);

        int bg = 0xF0141519;
        if (tint.get() && stack.getItem() instanceof BlockItem b && b.getBlock() instanceof ShulkerBoxBlock box && box.getColor() != null) {
            bg = Theme.mix(bg, 0xF0000000 | (box.getColor().getMapColor().col & 0xFFFFFF), 0.28f);
        }

        g.pose().pushPose();
        g.pose().translate(0, 0, 500); // above the slots and their items
        Theme.softShadow(g, x, y, w, h, 6, 0.6f);
        Theme.rounded(g, x, y, w, h, 6, bg);
        g.drawString(mc.font, echest ? Component.literal("Ender Chest (last opened)") : stack.getHoverName(), x + 6, y + 5, Theme.TEXT, false);
        int gx = x + 5, gy = y + 17;
        for (int i = 0; i < items.size(); i++) {
            int sx = gx + (i % COLS) * SLOT, sy = gy + (i / COLS) * SLOT;
            g.fill(sx + 1, sy + 1, sx + SLOT - 1, sy + SLOT - 1, 0x40000000);
            ItemStack s = items.get(i);
            if (s.isEmpty()) continue;
            g.renderItem(s, sx + 1, sy + 1);
            g.renderItemDecorations(mc.font, s, sx + 1, sy + 1);
        }
        g.pose().popPose();
        return true;
    }
}
