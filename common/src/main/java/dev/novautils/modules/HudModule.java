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

    /** Helper for list-style HUDs: draws a line left- or right-aligned inside a block of the given width. */
    protected void line(GuiGraphicsExtractor g, String text, int x, int y, int blockW, boolean right, int color) {
        int tx = right ? x + blockW - mc.font.width(text) : x;
        g.text(mc.font, text, tx, y, color);
    }
}
