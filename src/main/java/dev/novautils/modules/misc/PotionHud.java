package dev.novautils.modules.misc;

import dev.novautils.modules.Category;
import dev.novautils.modules.Module;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.world.effect.MobEffectInstance;

/** Lists your active potion effects with the time left, on the right-hand side. */
public class PotionHud extends Module {
    private static final String[] ROMAN = {"", " II", " III", " IV", " V"};

    public PotionHud() {
        super("PotionHud", "Shows your active effects and how long they last.", Category.MISC);
    }

    private static String time(int ticks) {
        int seconds = ticks / 20;
        return String.format("%d:%02d", seconds / 60, seconds % 60);
    }

    @Override
    public void onRenderHud(GuiGraphicsExtractor g, DeltaTracker deltaTracker) {
        if (mc.player == null) return;

        // Start below the module list if it is sitting in the same corner.
        int y = 4 + dev.novautils.modules.ModuleManager.get().get(ActiveModules.class)
            .heightIn(ActiveModules.Corner.TOP_RIGHT);
        for (MobEffectInstance effect : mc.player.getActiveEffects()) {
            String name = effect.getEffect().value().getDisplayName().getString();
            int amplifier = Math.min(effect.getAmplifier(), ROMAN.length - 1);
            String left = time(effect.getDuration());
            String line = name + ROMAN[amplifier] + " " + left;

            int color = effect.getDuration() < 200 ? 0xFFFF6B6B : 0xFFE9E9F2;
            g.text(mc.font, line, g.guiWidth() - 4 - mc.font.width(line), y, color);
            y += mc.font.lineHeight + 1;
        }
    }
}
