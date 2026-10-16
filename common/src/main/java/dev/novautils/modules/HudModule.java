package dev.novautils.modules;

import dev.novautils.settings.Setting;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.gui.GuiGraphicsExtractor;

/**
 * A module that draws a block of HUD the player can drag around in the HUD editor.
 *
 * <p>Position is stored as two hidden settings, so it goes through the normal config save/load.
 * {@code -1} means "never moved": the module's own {@link #defaultX}/{@link #defaultY} decide,
 * which lets defaults depend on the screen size (right edge, above the hotbar, ...).
 *
 * <p>Size comes from the previous frame's render, which is what the editor uses for the drag box
 * and what clamping uses to keep the block on screen after a resolution change.
 */
public abstract class HudModule extends Module {
    /** Set while the HUD editor is open, so empty modules can draw sample content to drag. */
    public static boolean editing = false;

    private final Setting<Integer> posX = register(Setting.builder(Integer.class, "hud-x")
        .defaultValue(-1).range(-1, 10000).visible(() -> false).build());
    private final Setting<Integer> posY = register(Setting.builder(Integer.class, "hud-y")
        .defaultValue(-1).range(-1, 10000).visible(() -> false).build());

    private int width, height;
    private int lastX, lastY;

    protected HudModule(String name, String description, Category category) {
        super(name, description, category);
    }

    /** Draw at (x, y). {@code rightAligned} is true when the block sits in the right half of the screen. Return {width, height}. */
    protected abstract int[] render(GuiGraphicsExtractor g, int x, int y, boolean rightAligned);

    protected int defaultX(int screenW, int w) {
        return 4;
    }

    protected int defaultY(int screenH, int h) {
        return 4;
    }

    @Override
    public final void onRenderHud(GuiGraphicsExtractor g, DeltaTracker deltaTracker) {
        if (mc.player == null) return;

        int sw = g.guiWidth(), sh = g.guiHeight();
        int w = Math.max(width, 8), h = Math.max(height, 8);

        int x = posX.get() < 0 ? defaultX(sw, w) : posX.get();
        int y = posY.get() < 0 ? defaultY(sh, h) : posY.get();
        x = Math.max(0, Math.min(x, sw - w));
        y = Math.max(0, Math.min(y, sh - h));

        lastX = x;
        lastY = y;
        int[] size = render(g, x, y, x + w / 2 > sw / 2);
        width = size[0];
        height = size[1];
    }

    public int hudX() { return lastX; }
    public int hudY() { return lastY; }
    public int hudWidth() { return Math.max(width, 8); }
    public int hudHeight() { return Math.max(height, 8); }

    public void moveTo(int x, int y) {
        posX.set(Math.max(0, x));
        posY.set(Math.max(0, y));
    }

    public void resetPosition() {
        posX.set(-1);
        posY.set(-1);
    }

    public boolean isMoved() {
        return posX.get() >= 0;
    }

    // ------------------------------------------------------------------ shared look
    // Every HUD block uses the same card: translucent rounded panel, hairline border, soft shadow,
    // a dim label column and bright values, no heavy text shadow.

    protected static final int PAD = 5, ROW = 11;
    protected static final int HUD_BG = 0xB80D0E12, HUD_BORDER = 0x1EFFFFFF;
    protected static final int HUD_LABEL = 0xFF8A90A0, HUD_VALUE = 0xFFEDEFF5;

    protected static void panel(GuiGraphicsExtractor g, int x, int y, int w, int h) {
        dev.novautils.gui.Theme.softShadow(g, x, y, w, h, 4, 0.25f);
        dev.novautils.gui.Theme.rounded(g, x, y, w, h, 4, HUD_BG);
        dev.novautils.gui.Theme.border(g, x, y, w, h, HUD_BORDER);
    }

    /** "Label: value" / "Name  42m" / "Speed II 1:23" split into {label, value}. */
    protected static String[] splitRow(String line) {
        int i = line.indexOf(": ");
        if (i > 0) return new String[]{line.substring(0, i), line.substring(i + 2)};
        i = line.lastIndexOf("  ");
        if (i > 0) return new String[]{line.substring(0, i).trim(), line.substring(i + 2).trim()};
        i = line.lastIndexOf(' ');
        if (i > 0) return new String[]{line.substring(0, i), line.substring(i + 1)};
        return new String[]{line, ""};
    }

    /**
     * A card with one row per line: label on the left (dim), value on the right in the row's colour.
     * Returns the card size. Draws nothing for an empty list.
     */
    protected int[] table(GuiGraphicsExtractor g, int x, int y, java.util.List<String> lines, java.util.List<Integer> colors) {
        if (lines.isEmpty()) return new int[]{0, 0};
        int lw = 0, vw = 0;
        java.util.List<String[]> rows = new java.util.ArrayList<>();
        for (String l : lines) {
            String[] r = splitRow(l);
            rows.add(r);
            lw = Math.max(lw, mc.font.width(r[0]));
            vw = Math.max(vw, mc.font.width(r[1]));
        }
        int w = PAD + lw + (vw > 0 ? 10 + vw : 0) + PAD, h = PAD * 2 + rows.size() * ROW - 2;
        panel(g, x, y, w, h);
        for (int i = 0; i < rows.size(); i++) {
            int ry = y + PAD + i * ROW;
            int c = colors.get(i);
            boolean warn = (c & 0xFFFFFF) != (HUD_VALUE & 0xFFFFFF) && (c & 0xFFFFFF) != 0xE9E9F2 && (c & 0xFFFFFF) != 0xFFFFFF;
            g.text(mc.font, rows.get(i)[0], x + PAD, ry, rows.get(i)[1].isEmpty() ? c | 0xFF000000 : HUD_LABEL, false);
            String v = rows.get(i)[1];
            if (!v.isEmpty()) g.text(mc.font, v, x + w - PAD - mc.font.width(v), ry, warn ? c | 0xFF000000 : HUD_VALUE, false);
        }
        return new int[]{w, h};
    }

    /** Helper for list-style HUDs: draws a line left- or right-aligned inside a block of the given width. */
    protected void line(GuiGraphicsExtractor g, String text, int x, int y, int blockW, boolean right, int color) {
        int tx = right ? x + blockW - mc.font.width(text) : x;
        g.text(mc.font, text, tx, y, color);
    }
}
