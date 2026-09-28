package dev.novautils.modules.misc;

import dev.novautils.modules.Category;
import dev.novautils.modules.HudModule;
import dev.novautils.settings.Setting;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.util.CommonColors;
import net.minecraft.world.entity.player.Player;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/** Nearby players with their distance, closest first. Info only. Movable in the HUD editor. */
public class PlayerRadar extends HudModule {
    private final Setting<Integer> maxEntries = register(Setting.builder(Integer.class, "max-entries")
        .description("Maximum players listed.").defaultValue(8).range(1, 20).build());

    private final Setting<Integer> warnDistance = register(Setting.builder(Integer.class, "warn-distance")
        .description("Players closer than this are shown in red.").defaultValue(30).range(5, 200).build());

    public PlayerRadar() {
        super("PlayerRadar", "Shows nearby players and their distance so nobody sneaks up on you.", Category.MISC);
    }

    @Override
    protected int defaultY(int screenH, int h) {
        return screenH / 3;
    }

    @Override
    protected int[] render(GuiGraphicsExtractor g, int x, int y, boolean right) {
        List<String> lines = new ArrayList<>();
        List<Integer> colors = new ArrayList<>();

        if (mc.level != null) {
            List<Player> others = new ArrayList<>();
            for (Player p : mc.level.players()) if (p != mc.player) others.add(p);
            others.sort(Comparator.comparingDouble(p -> mc.player.distanceTo(p)));

            for (Player p : others) {
                if (lines.size() >= maxEntries.get()) break;
                int dist = Math.round(mc.player.distanceTo(p));
                lines.add(p.getName().getString() + "  " + dist + "m");
                colors.add(dist <= warnDistance.get() ? CommonColors.SOFT_RED : 0xFFE9E9F2);
            }
        }
        if (lines.isEmpty() && editing) {
            lines.add("Player  42m");
            colors.add(0x80E9E9F2);
        }

        int w = 0;
        for (String l : lines) w = Math.max(w, mc.font.width(l));
        int lineH = mc.font.lineHeight + 2;
        for (int i = 0; i < lines.size(); i++) {
            line(g, lines.get(i), x, y + i * lineH, w, right, colors.get(i));
        }
        return new int[]{w, lines.size() * lineH};
    }
}
