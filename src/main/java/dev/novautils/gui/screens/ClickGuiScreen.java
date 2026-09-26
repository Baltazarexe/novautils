package dev.novautils.gui.screens;

import com.mojang.blaze3d.platform.InputConstants;
import dev.novautils.gui.GuiState;
import dev.novautils.gui.Theme;
import dev.novautils.modules.Category;
import dev.novautils.modules.Module;
import dev.novautils.modules.ModuleManager;
import dev.novautils.settings.Setting;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.CharacterEvent;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * The in-game config menu. Everything is drawn by hand instead of with vanilla widgets: draggable
 * panels per category, inline settings with sliders and toggles, hover animation and a search box.
 * Panel positions are remembered between sessions.
 */
public class ClickGuiScreen extends Screen {
    private static final int PANEL_W = 116;
    private static final int HEADER_H = 15;
    private static final int ROW_H = 13;
    private static final int SETTING_H = 12;

    private enum RowType { MODULE, BOOL, NUMBER, ENUM, KEYBIND }

    private record Row(Panel panel, RowType type, int x, int y, int w, int h, Module module, Setting<?> setting) {}

    private static final class Panel {
        final Category category;
        int x, y;
        boolean open = true;
        float scroll;
        int maxBody = Integer.MAX_VALUE;
        int contentH;
        int viewH;

        Panel(Category category, int x, int y) {
            this.category = category;
            this.x = x;
            this.y = y;
        }

        boolean scrollable() {
            return contentH > viewH;
        }
    }

    /** Expanded modules, hover animation and panel placement survive closing the screen. */
    private static final Set<Module> EXPANDED = new HashSet<>();
    private static final Map<Module, Float> HOVER = new HashMap<>();
    private static final Map<Category, Panel> PANELS = new EnumMap<>(Category.class);

    private final List<Row> rows = new ArrayList<>();

    private String search = "";
    private Panel dragging;
    private int dragOffX, dragOffY;
    private Setting<?> slidingSetting;
    private Row slidingRow;
    private Module bindingFor;

    /** Opens or closes a module's settings section (also used by the in-game test). */
    public static void setExpanded(Module module, boolean expanded) {
        if (expanded) EXPANDED.add(module);
        else EXPANDED.remove(module);
    }

    public ClickGuiScreen() {
        super(Component.literal("NovaUtils"));
    }

    @Override
    protected void init() {
        if (PANELS.isEmpty()) {
            GuiState.get().load();
            int columns = Math.max(1, (this.width - 8) / (PANEL_W + 6));
            int index = 0;
            for (Category category : Category.values()) {
                int[] saved = GuiState.get().position(category);
                Panel panel;
                if (saved != null) {
                    panel = new Panel(category, saved[0], saved[1]);
                    panel.open = saved[2] != 0;
                } else {
                    int col = index % columns, rowIndex = index / columns;
                    int gridRows = (int) Math.ceil(Category.values().length / (double) columns);
                    int spacing = Math.max(70, (this.height - 32) / gridRows);
                    panel = new Panel(category, 8 + col * (PANEL_W + 6), 28 + rowIndex * spacing);
                    panel.maxBody = spacing - HEADER_H - 8;
                }
                PANELS.put(category, panel);
                index++;
            }
        }
        clampPanels();
    }

    /** Keeps panels on screen after a resize or a config from a bigger window. */
    private void clampPanels() {
        for (Panel panel : PANELS.values()) {
            panel.x = Math.max(0, Math.min(panel.x, this.width - PANEL_W));
            panel.y = Math.max(0, Math.min(panel.y, this.height - HEADER_H));
        }
    }

    @Override
    public void onClose() {
        for (Panel panel : PANELS.values()) {
            GuiState.get().store(panel.category, panel.x, panel.y, panel.open);
        }
        GuiState.get().save();
        ModuleManager.get().saveConfig();
        super.onClose();
    }

    // ------------------------------------------------------------------ layout

    private boolean matchesSearch(Module module) {
        if (search.isEmpty()) return true;
        String q = search.toLowerCase(Locale.ROOT);
        return module.name.toLowerCase(Locale.ROOT).contains(q)
            || module.description.toLowerCase(Locale.ROOT).contains(q);
    }

    private RowType rowTypeOf(Setting<?> setting) {
        Object value = setting.get();
        if (value instanceof Boolean) return RowType.BOOL;
        if (value instanceof Number) return RowType.NUMBER;
        if (value instanceof Enum<?>) return RowType.ENUM;
        return null;
    }

    private void layout() {
        rows.clear();
        for (Panel panel : PANELS.values()) {
            panel.viewH = Math.max(ROW_H * 3, Math.min(panel.maxBody, this.height - panel.y - HEADER_H - 6));
            if (!panel.open) {
                panel.contentH = 0;
                continue;
            }

            int contentH = 0;
            for (Module module : ModuleManager.get().getModules()) {
                if (module.category != panel.category || !matchesSearch(module)) continue;
                contentH += ROW_H;
                if (!EXPANDED.contains(module)) continue;
                for (Setting<?> setting : module.getSettings()) {
                    if (setting.isVisible() && rowTypeOf(setting) != null) contentH += SETTING_H;
                }
                contentH += SETTING_H;
            }
            panel.contentH = contentH;
            panel.scroll = Math.max(0, Math.min(panel.scroll, Math.max(0, contentH - panel.viewH)));

            int y = panel.y + HEADER_H - (int) panel.scroll;
            for (Module module : ModuleManager.get().getModules()) {
                if (module.category != panel.category || !matchesSearch(module)) continue;

                rows.add(new Row(panel, RowType.MODULE, panel.x, y, PANEL_W, ROW_H, module, null));
                y += ROW_H;

                if (!EXPANDED.contains(module)) continue;

                for (Setting<?> setting : module.getSettings()) {
                    if (!setting.isVisible()) continue;
                    RowType type = rowTypeOf(setting);
                    if (type == null) continue;
                    rows.add(new Row(panel, type, panel.x, y, PANEL_W, SETTING_H, module, setting));
                    y += SETTING_H;
                }
                rows.add(new Row(panel, RowType.KEYBIND, panel.x, y, PANEL_W, SETTING_H, module, null));
                y += SETTING_H;
            }
        }
    }

    private int panelHeight(Panel panel) {
        return HEADER_H + Math.min(panel.contentH, panel.viewH);
    }

    /** A row is only interactive where the panel actually shows it. */
    private boolean visible(Row row) {
        Panel panel = row.panel();
        int top = panel.y + HEADER_H, bottom = panel.y + panelHeight(panel);
        return row.y() + row.h() > top && row.y() < bottom;
    }

    private boolean hovered(Row row, int mouseX, int mouseY) {
        return visible(row)
            && mouseX >= row.x() && mouseX < row.x() + row.w()
            && mouseY >= row.y() && mouseY < row.y() + row.h();
    }

    // ------------------------------------------------------------------ drawing

    @Override
    public void extractRenderState(GuiGraphicsExtractor g, int mouseX, int mouseY, float delta) {
        layout();

        g.fill(0, 0, this.width, this.height, 0x99101018);
        drawSearch(g);

        for (Panel panel : PANELS.values()) drawPanel(g, panel, mouseX, mouseY, delta);

        String hint = bindingFor != null
            ? "Press a key to bind " + bindingFor.name + "   (ESC clears it)"
            : "L-click toggle · R-click settings · drag title";
        g.text(this.font, hint, 166, 12, bindingFor != null ? Theme.ACCENT : Theme.TEXT_DIM);
    }

    private void drawSearch(GuiGraphicsExtractor g) {
        int w = 150, h = 14, x = 8, y = 8;
        Theme.roundRect(g, x, y, w, h, 0xF01B1B26);
        Theme.border(g, x, y, w, h, search.isEmpty() ? Theme.PANEL_BORDER : Theme.ACCENT);
        g.text(this.font, search.isEmpty() ? "Type to search..." : search, x + 5, y + 3,
            search.isEmpty() ? Theme.TEXT_OFF : Theme.TEXT);
    }

    private void drawPanel(GuiGraphicsExtractor g, Panel panel, int mouseX, int mouseY, float delta) {
        int h = panelHeight(panel);

        Theme.shadow(g, panel.x, panel.y, PANEL_W, h);
        Theme.roundRect(g, panel.x, panel.y, PANEL_W, h, Theme.PANEL_BG);
        Theme.border(g, panel.x, panel.y, PANEL_W, h, Theme.PANEL_BORDER);

        g.fillGradient(panel.x + 1, panel.y + 1, panel.x + PANEL_W - 1, panel.y + HEADER_H, Theme.HEADER_TOP, Theme.HEADER_BOTTOM);
        g.fill(panel.x + 1, panel.y + HEADER_H - 1, panel.x + PANEL_W - 1, panel.y + HEADER_H, Theme.ACCENT);
        g.text(this.font, panel.category.name(), panel.x + 6, panel.y + 4, Theme.TEXT);
        g.text(this.font, panel.open ? "−" : "+", panel.x + PANEL_W - 11, panel.y + 4, Theme.TEXT_DIM);

        if (!panel.open) return;

        g.enableScissor(panel.x, panel.y + HEADER_H, panel.x + PANEL_W, panel.y + h);
        for (Row row : rows) {
            if (row.panel() != panel || !visible(row)) continue;
            switch (row.type()) {
                case MODULE -> drawModuleRow(g, row, mouseX, mouseY, delta);
                case BOOL -> drawBoolRow(g, row, mouseX, mouseY);
                case NUMBER -> drawNumberRow(g, row);
                case ENUM -> drawEnumRow(g, row, mouseX, mouseY);
                case KEYBIND -> drawKeybindRow(g, row, mouseX, mouseY);
            }
        }
        g.disableScissor();

        if (panel.scrollable()) {
            int trackTop = panel.y + HEADER_H + 1, trackH = h - HEADER_H - 2;
            int thumbH = Math.max(12, (int) (trackH * (panel.viewH / (float) panel.contentH)));
            int thumbY = trackTop + (int) ((trackH - thumbH) * (panel.scroll / (float) (panel.contentH - panel.viewH)));
            g.fill(panel.x + PANEL_W - 3, trackTop, panel.x + PANEL_W - 1, trackTop + trackH, 0x30FFFFFF);
            g.fill(panel.x + PANEL_W - 3, thumbY, panel.x + PANEL_W - 1, thumbY + thumbH, Theme.ACCENT);
        }
    }

    private void drawModuleRow(GuiGraphicsExtractor g, Row row, int mouseX, int mouseY, float delta) {
        Module module = row.module();
        boolean hover = hovered(row, mouseX, mouseY);
        float anim = Theme.approach(HOVER.getOrDefault(module, 0f), hover ? 1f : 0f, delta + 0.6f);
        HOVER.put(module, anim);

        if (module.isEnabled()) g.fill(row.x() + 1, row.y(), row.x() + row.w() - 1, row.y() + row.h(), Theme.ROW_ACTIVE);
        if (anim > 0.01f) g.fill(row.x() + 1, row.y(), row.x() + row.w() - 1, row.y() + row.h(), Theme.alpha(Theme.ROW_HOVER, anim));
        if (module.isEnabled()) g.fill(row.x() + 1, row.y(), row.x() + 3, row.y() + row.h(), Theme.ACCENT);

        g.text(this.font, module.name, row.x() + 7 + (int) (anim * 2), row.y() + 3,
            module.isEnabled() ? Theme.TEXT : Theme.TEXT_OFF);
        g.text(this.font, EXPANDED.contains(module) ? "▾" : "▸", row.x() + row.w() - 11, row.y() + 3, Theme.TEXT_DIM);

        if (hover && !module.description.isEmpty()) {
            g.setTooltipForNextFrame(Component.literal(module.description), mouseX, mouseY);
        }
    }

    private void drawBoolRow(GuiGraphicsExtractor g, Row row, int mouseX, int mouseY) {
        @SuppressWarnings("unchecked")
        Setting<Boolean> setting = (Setting<Boolean>) row.setting();
        boolean on = setting.get();
        if (hovered(row, mouseX, mouseY)) g.fill(row.x() + 1, row.y(), row.x() + row.w() - 1, row.y() + row.h(), 0x14FFFFFF);

        g.text(this.font, setting.name, row.x() + 12, row.y() + 2, on ? Theme.TEXT : Theme.TEXT_DIM);

        int bx = row.x() + row.w() - 20, by = row.y() + 3;
        Theme.roundRect(g, bx, by, 14, 7, on ? Theme.ACCENT_DIM : Theme.TRACK);
        Theme.roundRect(g, on ? bx + 8 : bx + 1, by + 1, 5, 5, on ? 0xFFFFFFFF : 0xFF9A9AAE);
    }

    private void drawNumberRow(GuiGraphicsExtractor g, Row row) {
        Setting<?> setting = row.setting();
        double value = ((Number) setting.get()).doubleValue();
        double min = Double.isFinite(setting.min) ? setting.min : 0;
        double max = Double.isFinite(setting.max) ? setting.max : 1;
        float frac = max > min ? (float) ((value - min) / (max - min)) : 0f;

        boolean isInt = setting.get() instanceof Integer;
        String label = setting.name + ": " + (isInt ? String.valueOf((int) value) : String.format(Locale.ROOT, "%.2f", value));
        g.text(this.font, label, row.x() + 12, row.y() + 1, Theme.TEXT_DIM);

        int tx = row.x() + 12, ty = row.y() + row.h() - 3, tw = row.w() - 24;
        g.fill(tx, ty, tx + tw, ty + 2, Theme.TRACK);
        g.fill(tx, ty, tx + (int) (tw * frac), ty + 2, Theme.ACCENT);
        int knob = tx + (int) (tw * frac);
        g.fill(knob - 1, ty - 2, knob + 2, ty + 4, 0xFFFFFFFF);
    }

    private void drawEnumRow(GuiGraphicsExtractor g, Row row, int mouseX, int mouseY) {
        Setting<?> setting = row.setting();
        if (hovered(row, mouseX, mouseY)) g.fill(row.x() + 1, row.y(), row.x() + row.w() - 1, row.y() + row.h(), 0x14FFFFFF);
        g.text(this.font, setting.name, row.x() + 12, row.y() + 2, Theme.TEXT_DIM);
        String value = ((Enum<?>) setting.get()).name();
        g.text(this.font, value, row.x() + row.w() - 8 - this.font.width(value), row.y() + 2, Theme.ACCENT);
    }

    private void drawKeybindRow(GuiGraphicsExtractor g, Row row, int mouseX, int mouseY) {
        Module module = row.module();
        if (hovered(row, mouseX, mouseY)) g.fill(row.x() + 1, row.y(), row.x() + row.w() - 1, row.y() + row.h(), 0x14FFFFFF);
        g.text(this.font, "bind", row.x() + 12, row.y() + 2, Theme.TEXT_DIM);
        String key = bindingFor == module ? "..." : keyName(module.getKeyBind());
        g.text(this.font, key, row.x() + row.w() - 8 - this.font.width(key), row.y() + 2,
            bindingFor == module ? Theme.ACCENT : Theme.TEXT_OFF);
    }

    private String keyName(int keyCode) {
        if (keyCode == -1) return "none";
        return InputConstants.Type.KEYBOARD.getOrCreate(keyCode).getDisplayName().getString();
    }

    // ------------------------------------------------------------------ input

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
        int mx = (int) event.x(), my = (int) event.y();

        for (Panel panel : PANELS.values()) {
            if (mx >= panel.x && mx < panel.x + PANEL_W && my >= panel.y && my < panel.y + HEADER_H) {
                if (event.button() == 1) {
                    panel.open = !panel.open;
                } else {
                    dragging = panel;
                    dragOffX = mx - panel.x;
                    dragOffY = my - panel.y;
                }
                return true;
            }
        }

        for (int i = rows.size() - 1; i >= 0; i--) {
            Row row = rows.get(i);
            if (!hovered(row, mx, my)) continue;

            switch (row.type()) {
                case MODULE -> {
                    if (event.button() == 1) {
                        if (!EXPANDED.remove(row.module())) EXPANDED.add(row.module());
                    } else {
                        row.module().toggle();
                        ModuleManager.get().saveConfig();
                    }
                }
                case BOOL -> {
                    @SuppressWarnings("unchecked")
                    Setting<Boolean> setting = (Setting<Boolean>) row.setting();
                    setting.set(!setting.get());
                    ModuleManager.get().saveConfig();
                }
                case NUMBER -> {
                    slidingSetting = row.setting();
                    slidingRow = row;
                    applySlider(mx);
                }
                case ENUM -> cycleEnum(row.setting(), event.button() == 1 ? -1 : 1);
                case KEYBIND -> bindingFor = row.module();
            }
            return true;
        }

        bindingFor = null;
        return super.mouseClicked(event, doubleClick);
    }

    @Override
    public boolean mouseDragged(MouseButtonEvent event, double dx, double dy) {
        if (dragging != null) {
            dragging.x = (int) event.x() - dragOffX;
            dragging.y = (int) event.y() - dragOffY;
            return true;
        }
        if (slidingSetting != null) {
            applySlider((int) event.x());
            return true;
        }
        return super.mouseDragged(event, dx, dy);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        for (Panel panel : PANELS.values()) {
            int h = panelHeight(panel);
            if (mouseX >= panel.x && mouseX < panel.x + PANEL_W && mouseY >= panel.y && mouseY < panel.y + h) {
                if (panel.scrollable()) {
                    panel.scroll = (float) Math.max(0, Math.min(panel.contentH - panel.viewH, panel.scroll - scrollY * 18));
                    return true;
                }
            }
        }
        return super.mouseScrolled(mouseX, mouseY, scrollX, scrollY);
    }

    @Override
    public boolean mouseReleased(MouseButtonEvent event) {
        if (slidingSetting != null) ModuleManager.get().saveConfig();
        dragging = null;
        slidingSetting = null;
        slidingRow = null;
        return super.mouseReleased(event);
    }

    @SuppressWarnings("unchecked")
    private void applySlider(int mouseX) {
        if (slidingSetting == null || slidingRow == null) return;
        int tx = slidingRow.x() + 12, tw = slidingRow.w() - 24;
        double frac = Math.max(0, Math.min(1, (mouseX - tx) / (double) tw));
        double min = Double.isFinite(slidingSetting.min) ? slidingSetting.min : 0;
        double max = Double.isFinite(slidingSetting.max) ? slidingSetting.max : 1;
        double value = min + (max - min) * frac;

        if (slidingSetting.get() instanceof Integer) {
            ((Setting<Integer>) slidingSetting).set((int) Math.round(value));
        } else {
            ((Setting<Double>) slidingSetting).set(Math.round(value * 100.0) / 100.0);
        }
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private void cycleEnum(Setting<?> setting, int direction) {
        Object[] constants = setting.type.getEnumConstants();
        if (constants == null || constants.length == 0) return;
        int index = 0;
        for (int i = 0; i < constants.length; i++) if (constants[i] == setting.get()) index = i;
        ((Setting) setting).set(constants[Math.floorMod(index + direction, constants.length)]);
        ModuleManager.get().saveConfig();
    }

    @Override
    public boolean keyPressed(KeyEvent event) {
        if (bindingFor != null) {
            bindingFor.setKeyBind(event.key() == InputConstants.KEY_ESCAPE ? -1 : event.key());
            bindingFor = null;
            ModuleManager.get().saveConfig();
            return true;
        }
        if (event.key() == InputConstants.KEY_BACKSPACE && !search.isEmpty()) {
            search = search.substring(0, search.length() - 1);
            return true;
        }
        return super.keyPressed(event);
    }

    @Override
    public boolean charTyped(CharacterEvent event) {
        if (bindingFor == null && event.isAllowedChatCharacter()) {
            search += event.codepointAsString();
            return true;
        }
        return super.charTyped(event);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
