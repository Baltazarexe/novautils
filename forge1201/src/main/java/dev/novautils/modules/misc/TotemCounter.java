package dev.novautils.modules.misc;

import dev.novautils.gui.Theme;
import dev.novautils.modules.Category;
import dev.novautils.modules.HudModule;
import dev.novautils.settings.Setting;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import java.util.ArrayList;
import java.util.List;

/** Counts your survival supplies (totems, gapples, XP bottles, crystals, obsidian) across the whole inventory. */
public class TotemCounter extends HudModule {
    private final Setting<Boolean> totems = register(Setting.builder(Boolean.class, "totems")
        .description("Count Totems of Undying (offhand included).").defaultValue(true).build());
    private final Setting<Boolean> gapples = register(Setting.builder(Boolean.class, "golden-apples")
        .description("Count golden and enchanted golden apples.").defaultValue(true).build());
    private final Setting<Boolean> xp = register(Setting.builder(Boolean.class, "xp-bottles")
        .description("Count bottles o' enchanting.").defaultValue(false).build());
    private final Setting<Boolean> crystals = register(Setting.builder(Boolean.class, "end-crystals")
        .description("Count end crystals.").defaultValue(false).build());
    private final Setting<Boolean> obsidian = register(Setting.builder(Boolean.class, "obsidian")
        .description("Count obsidian.").defaultValue(false).build());
    private final Setting<Integer> lowTotems = register(Setting.builder(Integer.class, "low-totem-warning")
        .description("Totem count shown in red at or below this.").defaultValue(1).range(0, 10).build());

    public TotemCounter() {
        super("TotemCounter", "Counts totems, gapples and other supplies in your inventory.", Category.MISC);
    }

    @Override
    protected int defaultX(int screenW, int w) {
        return screenW / 2 + 96;
    }

    @Override
    protected int defaultY(int screenH, int h) {
        return screenH - h - 4;
    }

    private int count(Item... items) {
        var inv = mc.player.getInventory();
        int total = 0;
        for (int i = 0; i < inv.getContainerSize(); i++) {
            ItemStack stack = inv.getItem(i);
            for (Item item : items) if (stack.is(item)) total += stack.getCount();
        }
        return total;
    }

    @Override
    protected int[] render(GuiGraphics g, int x, int y, boolean right) {
        List<String> lines = new ArrayList<>();
        List<Integer> colors = new ArrayList<>();

        if (totems.get()) {
            int n = count(Items.TOTEM_OF_UNDYING);
            lines.add("Totems: " + n);
            colors.add(n <= lowTotems.get() ? 0xFFFF5555 : 0xFFFFD84A);
        }
        if (gapples.get()) {
            lines.add("Gapples: " + count(Items.GOLDEN_APPLE, Items.ENCHANTED_GOLDEN_APPLE));
            colors.add(0xFFFFC94A);
        }
        if (xp.get()) {
            lines.add("XP: " + count(Items.EXPERIENCE_BOTTLE));
            colors.add(0xFF7CFC5C);
        }
        if (crystals.get()) {
            lines.add("Crystals: " + count(Items.END_CRYSTAL));
            colors.add(0xFFE07CFF);
        }
        if (obsidian.get()) {
            lines.add("Obsidian: " + count(Items.OBSIDIAN));
            colors.add(0xFFA070C8);
        }

        return table(g, x, y, lines, colors);
    }
}
