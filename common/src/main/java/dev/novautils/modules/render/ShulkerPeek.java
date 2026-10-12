package dev.novautils.modules.render;

import dev.novautils.gui.Theme;
import dev.novautils.modules.Category;
import dev.novautils.modules.Module;
import dev.novautils.settings.Setting;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.core.NonNullList;
import net.minecraft.core.component.DataComponents;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.ItemContainerContents;
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
        ItemContainerContents c = stack.get(DataComponents.CONTAINER);
        if (c != null) c.copyInto(items);
        return items;
    }

    /** Returns true when it drew the preview (the vanilla tooltip is then skipped). */
    public boolean draw(GuiGraphicsExtractor g, ItemStack stack, int mouseX, int mouseY) {
        if (!isShulker(stack)) return false;
        if (shiftForVanilla.get() && mc.hasShiftDown()) return false;
        NonNullList<ItemStack> items = contents(stack);
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
            bg = Theme.mix(bg, 0xF0000000 | (box.getColor().getTextureDiffuseColor() & 0xFFFFFF), 0.28f);
        }

        g.nextStratum();
        Theme.softShadow(g, x, y, w, h, 6, 0.6f);
        Theme.rounded(g, x, y, w, h, 6, bg);
        g.text(mc.font, stack.getHoverName(), x + 6, y + 5, Theme.TEXT, false);
        int gx = x + 5, gy = y + 17;
        for (int i = 0; i < items.size(); i++) {
            int sx = gx + (i % COLS) * SLOT, sy = gy + (i / COLS) * SLOT;
            g.fill(sx + 1, sy + 1, sx + SLOT - 1, sy + SLOT - 1, 0x40000000);
            ItemStack s = items.get(i);
            if (s.isEmpty()) continue;
            g.item(s, sx + 1, sy + 1);
            g.itemDecorations(mc.font, s, sx + 1, sy + 1);
        }
        return true;
    }
}
