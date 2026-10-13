package dev.novautils.modules.player;

import dev.novautils.modules.Category;
import dev.novautils.modules.Module;
import dev.novautils.settings.Setting;
import dev.novautils.utils.InvUtils;
import dev.novautils.utils.PlatformCompat;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.food.FoodProperties;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

/**
 * Eats when you get hungry (or when health is low, if you want) and goes back to what you were
 * holding. Vanilla stops eating the moment the use key isn't held, so this holds it down for you
 * until the food is gone. Picks the most filling food on your hotbar, never eats rotten flesh,
 * spider eyes, pufferfish, poisonous potatoes or chorus fruit (teleports you), and only uses
 * golden apples if you allow it.
 */
public class AutoEat extends Module {
    private final Setting<Integer> hunger = register(Setting.builder(Integer.class, "hunger")
        .description("Eat when the food bar is at or below this (20 = full).")
        .defaultValue(14).range(1, 19).build());

    private final Setting<Integer> health = register(Setting.builder(Integer.class, "health")
        .description("Also eat when health is at or below this (0 = ignore health).")
        .defaultValue(0).range(0, 19).build());

    private final Setting<Boolean> gapples = register(Setting.builder(Boolean.class, "golden-apples")
        .description("Allow eating golden apples (preferred when health is low).")
        .defaultValue(false).build());

    private final Setting<Boolean> pauseInScreens = register(Setting.builder(Boolean.class, "pause-in-menus")
        .description("Don't start eating while a menu is open.")
        .defaultValue(true).build());

    private int previousSlot = -1;
    private boolean eating;

    public AutoEat() {
        super("AutoEat", "Eats automatically when you're hungry.", Category.PLAYER);
    }

    public boolean isEating() {
        return eating;
    }

    @Override
    public void onDisable() {
        stop();
    }

    private static boolean bad(ItemStack s) {
        return s.is(Items.ROTTEN_FLESH) || s.is(Items.SPIDER_EYE) || s.is(Items.POISONOUS_POTATO)
            || s.is(Items.PUFFERFISH) || s.is(Items.CHORUS_FRUIT) || s.is(Items.SUSPICIOUS_STEW);
    }

    private static boolean golden(ItemStack s) {
        return s.is(Items.GOLDEN_APPLE) || s.is(Items.ENCHANTED_GOLDEN_APPLE);
    }

    private int findFood(boolean lowHealth) {
        int best = -1;
        double bestScore = -1;
        for (int slot = 0; slot < 9; slot++) {
            ItemStack stack = mc.player.getInventory().getItem(slot);
            FoodProperties food = stack.getItem().getFoodProperties(stack, mc.player);
            if (food == null || bad(stack)) continue;
            if (golden(stack) && !gapples.get()) continue;
            double score = food.getNutrition() + food.getSaturationModifier() * food.getNutrition() * 2;
            if (golden(stack) && lowHealth) score += 100;
            if (score > bestScore) {
                bestScore = score;
                best = slot;
            }
        }
        return best;
    }

    private void stop() {
        if (eating) mc.options.keyUse.setDown(false);
        eating = false;
        if (previousSlot != -1 && mc.player != null) InvUtils.selectHotbar(previousSlot);
        previousSlot = -1;
    }

    @Override
    public void onTick() {
        if (mc.player == null || mc.gameMode == null) return;
        var p = mc.player;

        if (eating) {
            // finished (or interrupted): let go; next tick decides whether to eat another
            if (!p.isUsingItem() || !p.getMainHandItem().isEdible()) {
                stop();
                return;
            }
            // vanilla releases the item the moment the use key is up
            mc.options.keyUse.setDown(true);
            return;
        }

        if (pauseInScreens.get() && PlatformCompat.currentScreen(mc) != null) return;
        if (p.isUsingItem()) return;

        boolean hungry = p.getFoodData().getFoodLevel() <= hunger.get();
        boolean lowHealth = health.get() > 0 && p.getHealth() <= health.get();
        if (!hungry && !lowHealth) return;
        if (!p.getFoodData().needsFood() && !(lowHealth && gapples.get())) return;

        int slot = findFood(lowHealth);
        if (slot == -1) return;

        previousSlot = p.getInventory().selected;
        if (slot != previousSlot) InvUtils.selectHotbar(slot);
        else previousSlot = -1;
        eating = true;
        mc.options.keyUse.setDown(true);
        mc.gameMode.useItem(p, InteractionHand.MAIN_HAND);
    }
}
