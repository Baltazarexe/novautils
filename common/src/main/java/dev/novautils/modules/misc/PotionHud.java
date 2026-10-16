package dev.novautils.modules.misc;

import dev.novautils.modules.Category;
import dev.novautils.modules.HudModule;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.world.effect.MobEffectInstance;

import java.util.ArrayList;
import java.util.List;

/** Your active potion effects with the time left. Movable in the HUD editor. */
public class PotionHud extends HudModule {
    private static final String[] ROMAN = {"", " II", " III", " IV", " V"};

    public PotionHud() {
        super("PotionHud", "Shows your active effects and how long they last.", Category.MISC);
    }

    private static String time(int ticks) {
        int seconds = ticks / 20;
        return String.format("%d:%02d", seconds / 60, seconds % 60);
    }

    @Override
    protected int defaultX(int screenW, int w) {
        return screenW - w - 4;
    }

    @Override
    protected int defaultY(int screenH, int h) {
        return screenH / 3;
    }

    @Override
    protected int[] render(GuiGraphicsExtractor g, int x, int y, boolean right) {
        List<String> lines = new ArrayList<>();
        List<Integer> colors = new ArrayList<>();

        for (MobEffectInstance effect : mc.player.getActiveEffects()) {
            String name = effect.getEffect().value().getDisplayName().getString();
            int amplifier = Math.min(effect.getAmplifier(), ROMAN.length - 1);
            lines.add(name + ROMAN[amplifier] + " " + time(effect.getDuration()));
            colors.add(effect.getDuration() < 200 ? 0xFFFF6B6B : 0xFFE9E9F2);
        }
        if (lines.isEmpty() && editing) {
            lines.add("Speed II 1:23");
            colors.add(0x80E9E9F2);
        }

        return table(g, x, y, lines, colors);
    }
}
