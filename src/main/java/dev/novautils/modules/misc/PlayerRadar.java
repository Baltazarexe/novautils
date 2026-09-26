package dev.novautils.modules.misc;

import dev.novautils.modules.Category;
import dev.novautils.modules.Module;
import dev.novautils.settings.Setting;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.util.CommonColors;
import net.minecraft.world.entity.player.Player;

/** Lists other players currently loaded around you (name + distance) on the right side of the screen. Info only. */
public class PlayerRadar extends Module {
    private final Setting<Integer> maxEntries = register(Setting.builder(Integer.class, "max-entries")
        .description("Maximum players listed.")
        .defaultValue(8)
        .range(1, 20)
        .build());

    public PlayerRadar() {
        super("PlayerRadar", "Shows nearby players and their distance so nobody sneaks up on you.", Category.MISC);
    }

    @Override
    public void onRenderHud(GuiGraphicsExtractor graphics, DeltaTracker deltaTracker) {
        if (mc.player == null || mc.level == null) return;

        int right = graphics.guiWidth() - 4;
        int y = 4;
        int shown = 0;

        for (Player other : mc.level.players()) {
            if (other == mc.player) continue;
            if (shown >= maxEntries.get()) break;

            String line = other.getName().getString() + "  " + Math.round(mc.player.distanceTo(other)) + "m";
            int width = mc.font.width(line);
            graphics.text(mc.font, line, right - width, y, CommonColors.SOFT_RED);
            y += mc.font.lineHeight + 2;
            shown++;
        }
    }
}
