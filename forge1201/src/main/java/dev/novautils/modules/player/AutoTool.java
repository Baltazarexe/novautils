package dev.novautils.modules.player;

import dev.novautils.modules.Category;
import dev.novautils.modules.Module;
import dev.novautils.settings.Setting;
import dev.novautils.utils.InvUtils;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;

/**
 * Switches to the fastest hotbar tool for the block you're mining and back when you stop.
 * Won't pick a tool that's about to break, can prefer Silk Touch or Fortune, and ignores swords
 * (a sword is "fast" on cobwebs and leaves but you don't want to wear it out mining).
 */
public class AutoTool extends Module {
    public enum Prefer { NONE, SILK_TOUCH, FORTUNE }

    private final Setting<Boolean> silent = register(Setting.builder(Boolean.class, "silent")
        .description("Swap only for the server and the mining itself - your hand keeps showing the item you hold (hotbar tools only).")
        .defaultValue(true).build());

    private final Setting<Prefer> prefer = register(Setting.builder(Prefer.class, "prefer")
        .description("Break ties toward Silk Touch or Fortune tools.")
        .defaultValue(Prefer.NONE).build());

    private final Setting<Integer> saveDurability = register(Setting.builder(Integer.class, "anti-break")
        .description("Don't use a tool with this many uses left or fewer (0 = off).")
        .defaultValue(10).range(0, 100).build());

    private final Setting<Boolean> switchBack = register(Setting.builder(Boolean.class, "switch-back")
        .description("Go back to the previous slot when you stop mining.")
        .defaultValue(true).visible(() -> !silent.get()).build());

    private final Setting<Boolean> fromInventory = register(Setting.builder(Boolean.class, "from-inventory")
        .description("If the right tool is only in your main inventory, swap it into your hand.")
        .defaultValue(true).visible(() -> !silent.get()).build());

    private int previousSlot = -1;
    /** Silent mode: the slot we borrowed the tool from, to give back after this tick's clicks. */
    private int silentOriginal = -1;

    public AutoTool() {
        super("AutoTool", "Picks the best hotbar tool for the block you mine.", Category.PLAYER);
    }

    @Override
    public void onDisable() {
        restore();
    }

    private void restore() {
        if (switchBack.get() && previousSlot != -1 && mc.player != null) InvUtils.selectHotbar(previousSlot);
        previousSlot = -1;
    }

    /** MinecraftUseMixin, right before vanilla handles this tick's attack and use keys. */
    public static void beforeKeybinds() {
        if (!dev.novautils.NovaUtils.modulesReady) return;
        AutoTool m = dev.novautils.modules.ModuleManager.get().get(AutoTool.class);
        if (m.isEnabled() && m.silent.get()) m.silentSwap();
    }

    /** MinecraftUseMixin, right after: the slot goes back before the frame is drawn, so nothing shows. */
    public static void afterKeybinds() {
        if (!dev.novautils.NovaUtils.modulesReady) return;
        dev.novautils.modules.ModuleManager.get().get(AutoTool.class).silentRestore();
    }

    private int bestHotbar(BlockState state, int current) {
        var inv = mc.player.getInventory();
        int best = current;
        double bestScore = score(inv.getItem(current), state);
        for (int i = 0; i < 9; i++) {
            double s = score(inv.getItem(i), state);
            if (s > bestScore) {
                bestScore = s;
                best = i;
            }
        }
        return best;
    }

    private void silentSwap() {
        silentOriginal = -1;
        if (mc.player == null || mc.level == null || mc.gameMode == null || mc.player.getAbilities().instabuild) return;
        if (dev.novautils.utils.PlatformCompat.currentScreen(mc) != null || !mc.options.keyAttack.isDown()) return;
        if (!(mc.hitResult instanceof BlockHitResult hit) || hit.getType() != HitResult.Type.BLOCK) return;
        BlockState state = mc.level.getBlockState(hit.getBlockPos());
        if (state.getDestroySpeed(mc.level, hit.getBlockPos()) == 0) return;
        int current = mc.player.getInventory().selected;
        int best = bestHotbar(state, current);
        if (best == current) return;
        silentOriginal = current;
        InvUtils.selectHotbar(best);
    }

    private void silentRestore() {
        if (silentOriginal == -1) return;
        int original = silentOriginal;
        silentOriginal = -1;
        if (mc.player != null) InvUtils.selectHotbar(original);
    }

    private int level(ItemStack stack, Enchantment key) {
        return EnchantmentHelper.getItemEnchantmentLevel(key, stack);
    }

    private double score(ItemStack stack, BlockState state) {
        if (stack.isDamageableItem() && saveDurability.get() > 0
            && stack.getMaxDamage() - stack.getDamageValue() <= saveDurability.get()) return -1;
        float speed = stack.getDestroySpeed(state);
        if (speed <= 1f) return 0;
        double s = speed;
        // Efficiency adds level^2 + 1 to any tool that is already faster than a hand
        int eff = level(stack, Enchantments.BLOCK_EFFICIENCY);
        if (eff > 0) s += eff * eff + 1;
        if (stack.isCorrectToolForDrops(state)) s += 1000;
        if (prefer.get() == Prefer.SILK_TOUCH) s += level(stack, Enchantments.SILK_TOUCH) * 50;
        if (prefer.get() == Prefer.FORTUNE) s += level(stack, Enchantments.BLOCK_FORTUNE) * 50;
        return s;
    }

    @Override
    public void onTick() {
        if (mc.player == null || mc.level == null) return;
        if (silent.get()) return;
        if (!mc.options.keyAttack.isDown()) {
            restore();
            return;
        }
        if (!(mc.hitResult instanceof BlockHitResult hit) || hit.getType() != HitResult.Type.BLOCK) return;
        BlockState state = mc.level.getBlockState(hit.getBlockPos());
        if (state.getDestroySpeed(mc.level, hit.getBlockPos()) == 0) return; // instant-break anyway

        var inv = mc.player.getInventory();
        int current = inv.selected;
        int best = current;
        double bestScore = score(inv.getItem(current), state);
        for (int i = 0; i < 9; i++) {
            double s = score(inv.getItem(i), state);
            if (s > bestScore) {
                bestScore = s;
                best = i;
            }
        }
        if (fromInventory.get() && dev.novautils.utils.InvUtils.canClick()) {
            int bestInv = -1;
            for (int i = 9; i < 36; i++) {
                double s = score(inv.getItem(i), state);
                if (s > bestScore + 0.5) {
                    bestScore = s;
                    bestInv = i;
                }
            }
            if (bestInv != -1) {
                // swap it with whatever is in your hand (that goes where the tool was)
                InvUtils.swap(InvUtils.menuSlot(bestInv), current);
                return;
            }
        }
        if (best != current) {
            if (previousSlot == -1) previousSlot = current;
            InvUtils.selectHotbar(best);
        }
    }
}
