package dev.novautils.gui.screens;

import dev.novautils.gui.Theme;
import dev.novautils.modules.HudModule;
import dev.novautils.modules.Module;
import dev.novautils.modules.ModuleManager;
import dev.novautils.utils.PlatformCompat;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * Drag HUD elements around. The real HUD keeps rendering underneath (no blur), so what you see is
 * exactly where things will be. Chips at the top switch HUD elements on and off; right-click a
 * box to send it back to its default spot. Edges and the screen centre lines snap.
 */
public class HudEditorScreen extends Screen {
    private static final int SNAP = 6;
    private static final int CHIP_Y = 22, CHIP_H = 13;

    private final Screen parent;
    private HudModule dragging;
    private int offX, offY;
    private boolean snappedX, snappedY;

    private record Chip(HudModule module, int x, int y, int w) {}
    private final List<Chip> chips = new ArrayList<>();

    public HudEditorScreen(Screen parent) {
        super(Component.literal("HUD Editor"));
        this.parent = parent;
    }

    private List<HudModule> hudModules() {
        List<HudModule> list = new ArrayList<>();
        for (Module m : ModuleManager.get().getModules()) if (m instanceof HudModule h) list.add(h);
        return list;
    }

    @Override
    protected void init() {
        HudModule.editing = true;
    }

    @Override
    public void removed() {
        HudModule.editing = false;
        super.removed();
    }

    @Override
    public void onClose() {
        ModuleManager.get().saveConfig();
        PlatformCompat.openScreen(Minecraft.getInstance(), parent);
    }

    /** No blur: the whole point is to see the HUD behind this screen. */
    @Override
    public void renderBackground(GuiGraphics g, int mouseX, int mouseY, float delta) {
    }

    private void layoutChips() {
        chips.clear();
        int x = 8, y = CHIP_Y;
        for (HudModule m : hudModules()) {
            int w = this.font.width(m.name) + 12;
            if (x + w > this.width - 8) {
                x = 8;
                y += CHIP_H + 3;
            }
            chips.add(new Chip(m, x, y, w));
            x += w + 4;
        }
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float delta) {
        layoutChips();

        // light dim + centre guides
        g.fill(0, 0, this.width, this.height, 0x30000000);
        int cx = this.width / 2, cy = this.height / 2;
        g.fill(cx, 0, cx + 1, this.height, snappedX ? 0x9022D3EE : 0x18FFFFFF);
        g.fill(0, cy, this.width, cy + 1, snappedY ? 0x9022D3EE : 0x18FFFFFF);

        // header
        g.fill(0, 0, this.width, 18, 0xC0101018);
        g.drawString(this.font, "HUD Editor", 8, 5, Theme.TEXT);
        g.drawString(this.font, "drag to move · right-click resets · ESC back",
            8 + this.font.width("HUD Editor") + 10, 5, Theme.TEXT_DIM);

        for (Chip c : chips) {
            boolean on = c.module().isEnabled();
            boolean hover = mouseX >= c.x && mouseX < c.x + c.w() && mouseY >= c.y() && mouseY < c.y() + CHIP_H;
            Theme.roundRect(g, c.x, c.y(), c.w(), CHIP_H, on ? Theme.ACCENT_DIM : (hover ? 0xE0303040 : 0xE01B1B26));
            Theme.border(g, c.x, c.y(), c.w(), CHIP_H, on ? Theme.ACCENT : Theme.PANEL_BORDER);
            g.drawString(this.font, c.module().name, c.x + 6, c.y() + 3, on ? 0xFFFFFFFF : Theme.TEXT_DIM);
        }

        // boxes around enabled HUD elements
        for (HudModule m : hudModules()) {
            if (!m.isEnabled()) continue;
            int x = m.hudX() - 2, y = m.hudY() - 2, w = m.hudWidth() + 4, h = m.hudHeight() + 4;
            boolean hover = m == dragging || (mouseX >= x && mouseX < x + w && mouseY >= y && mouseY < y + h);

            g.fill(x, y, x + w, y + h, hover ? 0x2622D3EE : 0x14FFFFFF);
            Theme.border(g, x, y, w, h, hover ? Theme.ACCENT : 0x80FFFFFF);

            String label = m.name + (m.isMoved() ? "" : " (default)");
            int ly = y - 10 < 18 ? y + h + 2 : y - 10;
            g.drawString(this.font, label, x, ly, hover ? Theme.ACCENT : Theme.TEXT_DIM);
        }

        super.render(g, mouseX, mouseY, delta);
    }

    private HudModule boxAt(int mx, int my) {
        List<HudModule> list = hudModules();
        for (int i = list.size() - 1; i >= 0; i--) {
            HudModule m = list.get(i);
            if (!m.isEnabled()) continue;
            int x = m.hudX() - 2, y = m.hudY() - 2, w = m.hudWidth() + 4, h = m.hudHeight() + 4;
            if (mx >= x && mx < x + w && my >= y && my < y + h) return m;
        }
        return null;
    }

    @Override
    public boolean mouseClicked(double evX, double evY, int evButton) {
        int mx = (int) evX, my = (int) evY;

        for (Chip c : chips) {
            if (mx >= c.x && mx < c.x + c.w() && my >= c.y() && my < c.y() + CHIP_H) {
                c.module().toggle();
                return true;
            }
        }

        HudModule hit = boxAt(mx, my);
        if (hit != null) {
            if (evButton == 1) {
                hit.resetPosition();
                ModuleManager.get().markDirty();
            } else {
                dragging = hit;
                offX = mx - hit.hudX();
                offY = my - hit.hudY();
            }
            return true;
        }
        return super.mouseClicked(evX, evY, evButton);
    }

    @Override
    public boolean mouseDragged(double evX, double evY, int evButton, double dx, double dy) {
        if (dragging == null) return super.mouseDragged(evX, evY, evButton, dx, dy);

        int w = dragging.hudWidth(), h = dragging.hudHeight();
        int x = (int) evX - offX;
        int y = (int) evY - offY;

        // snap to screen edges and to the centre lines
        snappedX = false;
        snappedY = false;
        if (Math.abs(x) < SNAP) x = 0;
        if (Math.abs(x + w - this.width) < SNAP) x = this.width - w;
        if (Math.abs(x + w / 2 - this.width / 2) < SNAP) { x = this.width / 2 - w / 2; snappedX = true; }
        if (Math.abs(y) < SNAP) y = 0;
        if (Math.abs(y + h - this.height) < SNAP) y = this.height - h;
        if (Math.abs(y + h / 2 - this.height / 2) < SNAP) { y = this.height / 2 - h / 2; snappedY = true; }

        x = Math.max(0, Math.min(x, this.width - w));
        y = Math.max(0, Math.min(y, this.height - h));
        dragging.moveTo(x, y);
        return true;
    }

    @Override
    public boolean mouseReleased(double evX, double evY, int evButton) {
        if (dragging != null) ModuleManager.get().markDirty();
        dragging = null;
        snappedX = false;
        snappedY = false;
        return super.mouseReleased(evX, evY, evButton);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
