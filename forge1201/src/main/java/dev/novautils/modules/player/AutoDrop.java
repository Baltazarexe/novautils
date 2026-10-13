package dev.novautils.modules.player;

import dev.novautils.modules.Category;
import dev.novautils.modules.Module;
import dev.novautils.settings.Setting;
import dev.novautils.utils.InvUtils;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.inventory.ClickType;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * Throws away junk as soon as it lands in your inventory, so long mining trips don't fill up with
 * stone variants. Pick the groups below, and add any other item with {@code .drop add <item>}.
 * Never touches the hotbar unless you allow it.
 */
public class AutoDrop extends Module {
    private final Setting<Boolean> stones = register(Setting.builder(Boolean.class, "stone-variants")
        .description("Andesite, diorite, granite, tuff, calcite, cobbled deepslate.").defaultValue(true).build());

    private final Setting<Boolean> dirt = register(Setting.builder(Boolean.class, "dirt-gravel")
        .description("Dirt, gravel, flint.").defaultValue(true).build());

    private final Setting<Boolean> netherrack = register(Setting.builder(Boolean.class, "netherrack")
        .description("Netherrack, basalt, blackstone.").defaultValue(false).build());

    private final Setting<Boolean> cobble = register(Setting.builder(Boolean.class, "cobblestone")
        .description("Cobblestone (off: you may want it for building).").defaultValue(false).build());

    private final Setting<Boolean> mobDrops = register(Setting.builder(Boolean.class, "mob-junk")
        .description("Rotten flesh, poisonous potatoes, spider eyes.").defaultValue(true).build());

    private final Setting<Boolean> hotbar = register(Setting.builder(Boolean.class, "hotbar-too")
        .description("Also drop junk from the hotbar.").defaultValue(false).build());

    /** Extra items from .drop add, comma separated ids. */
    private final Setting<String> custom = register(Setting.builder(String.class, "custom")
        .defaultValue("").visible(() -> false).build());

    private int cooldown;

    public AutoDrop() {
        super("AutoDrop", "Throws away junk blocks and items (.drop add <item> for more).", Category.PLAYER);
    }

    private Set<String> junk() {
        Set<String> s = new LinkedHashSet<>();
        if (stones.get()) s.addAll(List.of("andesite", "diorite", "granite", "tuff", "calcite", "cobbled_deepslate"));
        if (dirt.get()) s.addAll(List.of("dirt", "gravel", "flint"));
        if (netherrack.get()) s.addAll(List.of("netherrack", "basalt", "blackstone"));
        if (cobble.get()) s.add("cobblestone");
        if (mobDrops.get()) s.addAll(List.of("rotten_flesh", "poisonous_potato", "spider_eye"));
        s.addAll(customList());
        return s;
    }

    public List<String> customList() {
        List<String> out = new ArrayList<>();
        for (String id : custom.get().split(",")) if (!id.isBlank()) out.add(id.trim());
        return out;
    }

    public boolean addCustom(String id) {
        id = id.toLowerCase().replace("minecraft:", "");
        List<String> list = customList();
        if (list.contains(id)) return false;
        list.add(id);
        custom.set(String.join(",", list));
        return true;
    }

    public boolean removeCustom(String id) {
        id = id.toLowerCase().replace("minecraft:", "");
        List<String> list = customList();
        boolean removed = list.remove(id);
        custom.set(String.join(",", list));
        return removed;
    }

    private boolean isJunk(ItemStack s, Set<String> junk) {
        return !s.isEmpty() && junk.contains(BuiltInRegistries.ITEM.getKey(s.getItem()).getPath());
    }

    @Override
    public void onTick() {
        if (mc.player == null || mc.gameMode == null || !InvUtils.canClick()) return;
        if (cooldown-- > 0) return;
        Set<String> junk = junk();
        var inv = mc.player.getInventory();
        for (int i = hotbar.get() ? 0 : 9; i < 36; i++) {
            if (!isJunk(inv.getItem(i), junk)) continue;
            mc.gameMode.handleInventoryMouseClick(mc.player.inventoryMenu.containerId, InvUtils.menuSlot(i), 1, ClickType.THROW, mc.player);
            cooldown = 2;
            return;
        }
    }
}
