package dev.novautils.modules.render;

import dev.novautils.gui.Theme;
import dev.novautils.modules.Category;
import dev.novautils.modules.HudModule;
import dev.novautils.modules.ModuleManager;
import dev.novautils.settings.Setting;
import net.minecraft.client.gui.GuiGraphics;

/**
 * A heading bar along the top of the screen: N/E/S/W and degree ticks sliding as you turn, with a
 * marker (and its distance) for every waypoint in view. Drag it anywhere in the HUD editor.
 */
public class Compass extends HudModule {
    private static final String[] NAMES = {"N", "NE", "E", "SE", "S", "SW", "W", "NW"};

    private final Setting<Integer> width = register(Setting.builder(Integer.class, "width")
        .description("Bar width in pixels.").defaultValue(240).range(120, 500).build());

    private final Setting<Boolean> waypoints = register(Setting.builder(Boolean.class, "waypoints")
        .description("Markers for your waypoints.").defaultValue(true).build());

    private final Setting<Boolean> degrees = register(Setting.builder(Boolean.class, "degrees")
        .description("Show the heading in degrees.").defaultValue(true).build());

    public Compass() {
        super("Compass", "A heading bar with your waypoints.", Category.RENDER);
    }

    @Override
    protected int defaultX(int screenW, int w) {
        return screenW / 2 - w / 2;
    }

    @Override
    protected int defaultY(int screenH, int h) {
        return 4;
    }

    /** Heading as a compass bearing: 0 = north, 90 = east. */
    private static float bearing(float yaw) {
        float b = (yaw + 180f) % 360f;
        return b < 0 ? b + 360f : b;
    }

    @Override
    protected int[] render(GuiGraphics g, int x, int y, boolean right) {
        int w = width.get(), h = 26;
        float heading = bearing(mc.player.getYRot());
        float ppd = w / 180f; // the bar shows 180 degrees
        int cx = x + w / 2;

        Theme.rounded(g, x, y, w, h, 5, 0xB0141519);
        int range = 90;
        int first = (int) Math.floor((heading - range) / 5f) * 5;
        for (int deg = first; deg <= heading + range; deg += 5) {
            int px = cx + Math.round((deg - heading) * ppd);
            if (px < x + 3 || px > x + w - 3) continue;
            int d = ((deg % 360) + 360) % 360;
            if (d % 45 == 0) {
                String label = NAMES[d / 45];
                int c = d % 90 == 0 ? Theme.TEXT : Theme.TEXT_DIM;
                if (d == 0) c = 0xFFFF6B6B; // north in red
                g.drawString(mc.font, label, px - mc.font.width(label) / 2, y + 4, c, true);
            } else if (d % 15 == 0) {
                g.fill(px, y + 4, px + 1, y + 10, 0x70FFFFFF);
            } else {
                g.fill(px, y + 6, px + 1, y + 9, 0x30FFFFFF);
            }
        }
        // the heading mark
        g.fill(cx - 1, y + h - 8, cx + 2, y + h - 3, Theme.accent());
        if (degrees.get()) {
            String t = Math.round(heading) + "°";
            g.drawString(mc.font, t, cx - mc.font.width(t) / 2, y + h - 11, Theme.TEXT_DIM, false);
        }

        var wps = ModuleManager.get().get(Waypoints.class);
        if (waypoints.get() && wps.isEnabled()) {
            for (double[] p : wps.positionsHere()) {
                double dx = p[0] - mc.player.getX(), dz = p[2] - mc.player.getZ();
                float target = bearing((float) Math.toDegrees(Math.atan2(-dx, dz)));
                float delta = ((target - heading + 540f) % 360f) - 180f;
                if (Math.abs(delta) > range - 4) continue;
                int px = cx + Math.round(delta * ppd);
                g.fill(px - 2, y + 11, px + 3, y + 16, Theme.accent2());
                String dist = Math.round(Math.sqrt(dx * dx + dz * dz)) + "m";
                g.drawString(mc.font, dist, px - mc.font.width(dist) / 2, y + h - 2, Theme.TEXT_DIM, false);
            }
        }
        return new int[]{w, h + 8};
    }
}
