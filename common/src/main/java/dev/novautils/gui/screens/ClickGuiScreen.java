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

    private enum RowType { MODULE, BOOL, NUMBER, ENUM, KEYBIND, COLOR, CHANNEL }

    private record Row(Panel panel, RowType type, int x, int y, int w, int h, Module module, Setting<?> setting, int channel) {
        Row(Panel panel, RowType type, int x, int y, int w, int h, Module module, Setting<?> setting) {
            this(panel, type, x, y, w, h, module, setting, -1);
        }
    }

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
    private static final Set<Setting<?>> OPEN_COLORS = new HashSet<>();
    private static final Map<Category, Panel> PANELS = new EnumMap<>(Category.class);

    private final List<Row> rows = new ArrayList<>();

    private String search = "";
    private Panel dragging;
    private int dragOffX, dragOffY;
    private Setting<?> slidingSetting;
    private Row slidingRow;
    private Module bindingFor;
    private Setting<?> hoveredSetting;

    /** Opens or closes a module's settings section (also used by the in-game test). */
    public static void setExpanded(Module module, boolean expanded) {
        if (expanded) EXPANDED.add(module);
        else EXPANDED.remove(module);
    }

    /** Opens or closes a colour setting's R/G/B/A sliders (also used by the in-game test). */
    public static void setColorExpanded(Setting<?> setting, boolean expanded) {
        if (expanded) OPEN_COLORS.add(setting);
        else OPEN_COLORS.remove(setting);
    }

    /**
     * Centre of a row as laid out in the last frame: the module row ({@code setting == null}), one of
     * its settings, or its keybind row ({@code "bind"}). Null unless the whole row is shown. Used by
     * the in-game test to click things like a player would.
     */
    public int[] rowCenter(Module module, String setting) {
        for (Row row : rows) {
            if (row.module() != module) continue;
            Panel panel = row.panel();
            if (!panel.open || row.y() < panel.y + HEADER_H || row.y() + row.h() > panel.y + panelHeight(panel)) continue;
            boolean match = setting == null ? row.type() == RowType.MODULE
                : setting.equals("bind") ? row.type() == RowType.KEYBIND
                : row.setting() != null && row.type() != RowType.CHANNEL && row.setting().name.equals(setting);
            if (match) return new int[]{row.x() + row.w() / 2, row.y() + row.h() / 2};
        }
        return null;
    }

    /** Current search text (test hook). */
    public String searchText() {
        return search;
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
        if (setting.color) return RowType.COLOR;
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
                    if (!setting.isVisible()) continue;
                    RowType type = rowTypeOf(setting);
                    if (type == null) continue;
                    contentH += SETTING_H;
                    if (type == RowType.COLOR && OPEN_COLORS.contains(setting)) contentH += SETTING_H * 4;
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

                    if (type == RowType.COLOR && OPEN_COLORS.contains(setting)) {
                        for (int channel = 0; channel < 4; channel++) {
                            rows.add(new Row(panel, RowType.CHANNEL, panel.x, y, PANEL_W, SETTING_H, module, setting, channel));
                            y += SETTING_H;
                        }
                    }
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
        drawHudButton(g, mouseX, mouseY);

        for (Panel panel : PANELS.values()) drawPanel(g, panel, mouseX, mouseY, delta);

        String hint = bindingFor != null
            ? "Press a key to bind " + bindingFor.name + "   (ESC clears it)"
            : "L-click toggle · R-click settings · M-click resets";
        int hintX = HUD_BTN_X + HUD_BTN_W + 10;
        if (hintX + this.font.width(hint) < this.width - 4) {
            g.text(this.font, hint, hintX, 12, bindingFor != null ? Theme.ACCENT : Theme.TEXT_DIM);
        }

        // Setting tooltips are set here, after the panels, so they draw on top of everything.
        if (hoveredSetting != null && !hoveredSetting.description.isEmpty()) {
            g.setTooltipForNextFrame(Component.literal(hoveredSetting.description + "  (middle-click: reset)"), mouseX, mouseY);
        }
        hoveredSetting = null;
    }

    private static final int HUD_BTN_X = 164, HUD_BTN_Y = 8, HUD_BTN_W = 56, HUD_BTN_H = 14;

    private boolean overHudButton(int mx, int my) {
        return mx >= HUD_BTN_X && mx < HUD_BTN_X + HUD_BTN_W && my >= HUD_BTN_Y && my < HUD_BTN_Y + HUD_BTN_H;
    }

    private void drawHudButton(GuiGraphicsExtractor g, int mouseX, int mouseY) {
        boolean hover = overHudButton(mouseX, mouseY);
        Theme.roundRect(g, HUD_BTN_X, HUD_BTN_Y, HUD_BTN_W, HUD_BTN_H, hover ? Theme.ACCENT : Theme.ACCENT_DIM);
        String label = "Edit HUD";
        g.text(this.font, label, HUD_BTN_X + HUD_BTN_W / 2 - this.font.width(label) / 2, HUD_BTN_Y + 3, 0xFFFFFFFF);
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

        int total = 0, on = 0;
        for (Module m : ModuleManager.get().getModules()) {
            if (m.category != panel.category) continue;
            total++;
            if (m.isEnabled()) on++;
        }
        String count = on + "/" + total;
        g.text(this.font, count, panel.x + PANEL_W - 16 - this.font.width(count), panel.y + 4,
            on > 0 ? Theme.ACCENT : Theme.TEXT_OFF);
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
                case COLOR -> drawColorRow(g, row, mouseX, mouseY);
                case CHANNEL -> drawChannelRow(g, row);
            }
            if (row.setting() != null && row.type() != RowType.CHANNEL && hovered(row, mouseX, mouseY)) {
                hoveredSetting = row.setting();
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
        if (module.getKeyBind() != -1) {
            String key = keyName(module.getKeyBind());
            if (key.length() > 5) key = key.substring(0, 5);
            g.text(this.font, key, row.x() + row.w() - 15 - this.font.width(key), row.y() + 3, Theme.TEXT_OFF);
        }
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

    private void drawColorRow(GuiGraphicsExtractor g, Row row, int mouseX, int mouseY) {
        Setting<?> setting = row.setting();
        if (hovered(row, mouseX, mouseY)) g.fill(row.x() + 1, row.y(), row.x() + row.w() - 1, row.y() + row.h(), 0x14FFFFFF);

        boolean open = OPEN_COLORS.contains(setting);
        g.text(this.font, (open ? "▾ " : "▸ ") + setting.name, row.x() + 8, row.y() + 2, Theme.TEXT_DIM);

        int color = (Integer) setting.get();
        int sx = row.x() + row.w() - 22, sy = row.y() + 2;
        g.fill(sx - 1, sy - 1, sx + 15, sy + 9, 0xFF000000);
        // checkerboard so alpha is readable
        g.fill(sx, sy, sx + 14, sy + 8, 0xFF777777);
        g.fill(sx, sy, sx + 7, sy + 4, 0xFF444444);
        g.fill(sx + 7, sy + 4, sx + 14, sy + 8, 0xFF444444);
        g.fill(sx, sy, sx + 14, sy + 8, color);
    }

    private void drawChannelRow(GuiGraphicsExtractor g, Row row) {
        int color = (Integer) row.setting().get();
        int channel = row.channel();
        int value = channelValue(color, channel);
        String name = switch (channel) { case 0 -> "R"; case 1 -> "G"; case 2 -> "B"; default -> "A"; };

        g.text(this.font, name + " " + value, row.x() + 18, row.y() + 1, Theme.TEXT_DIM);

        int tx = row.x() + 34, ty = row.y() + 5, tw = row.w() - 44;
        int bar = switch (channel) { case 0 -> 0xFFFF5555; case 1 -> 0xFF55DD66; case 2 -> 0xFF5B8CFF; default -> 0xFFCCCCCC; };
        g.fill(tx, ty, tx + tw, ty + 2, Theme.TRACK);
        g.fill(tx, ty, tx + (int) (tw * (value / 255f)), ty + 2, bar);
        int knob = tx + (int) (tw * (value / 255f));
        g.fill(knob - 1, ty - 2, knob + 2, ty + 4, 0xFFFFFFFF);
    }

    private static int channelValue(int color, int channel) {
        return switch (channel) {
            case 0 -> (color >> 16) & 0xFF;
            case 1 -> (color >> 8) & 0xFF;
            case 2 -> color & 0xFF;
            default -> (color >>> 24) & 0xFF;
        };
    }

    private static int withChannel(int color, int channel, int value) {
        value = Math.max(0, Math.min(255, value));
        return switch (channel) {
            case 0 -> (color & 0xFF00FFFF) | (value << 16);
            case 1 -> (color & 0xFFFF00FF) | (value << 8);
            case 2 -> (color & 0xFFFFFF00) | value;
            default -> (color & 0x00FFFFFF) | (value << 24);
        };
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
        return dev.novautils.utils.PlatformCompat.keyDisplayName(keyCode);
    }

    // ------------------------------------------------------------------ input

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
        int mx = (int) event.x(), my = (int) event.y();

        if (overHudButton(mx, my)) {
            dev.novautils.utils.PlatformCompat.openScreen(net.minecraft.client.Minecraft.getInstance(), new HudEditorScreen(this));
            return true;
        }

        // Middle click: reset whatever setting is under the mouse (or clear a keybind).
        if (event.button() == 2) {
            for (int i = rows.size() - 1; i >= 0; i--) {
                Row row = rows.get(i);
                if (!hovered(row, mx, my)) continue;
                if (row.type() == RowType.KEYBIND) {
                    row.module().setKeyBind(-1);
                } else if (row.setting() != null) {
                    row.setting().reset();
                }
                ModuleManager.get().markDirty();
                return true;
            }
        }

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
                        ModuleManager.get().markDirty();
                    }
                }
                case BOOL -> {
                    @SuppressWarnings("unchecked")
                    Setting<Boolean> setting = (Setting<Boolean>) row.setting();
                    setting.set(!setting.get());
                    ModuleManager.get().markDirty();
                }
                case NUMBER -> {
                    slidingSetting = row.setting();
                    slidingRow = row;
                    applySlider(mx);
                }
                case ENUM -> cycleEnum(row.setting(), event.button() == 1 ? -1 : 1);
                case KEYBIND -> bindingFor = row.module();
                case COLOR -> {
                    if (!OPEN_COLORS.remove(row.setting())) OPEN_COLORS.add(row.setting());
                }
                case CHANNEL -> {
                    slidingSetting = row.setting();
                    slidingRow = row;
                    applySlider(mx);
                }
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
        if (slidingSetting != null) ModuleManager.get().markDirty();
        dragging = null;
        slidingSetting = null;
        slidingRow = null;
        return super.mouseReleased(event);
    }

    @SuppressWarnings("unchecked")
    private void applySlider(int mouseX) {
        if (slidingSetting == null || slidingRow == null) return;

        if (slidingRow.type() == RowType.CHANNEL) {
            int tx = slidingRow.x() + 34, tw = slidingRow.w() - 44;
            double frac = Math.max(0, Math.min(1, (mouseX - tx) / (double) tw));
            Setting<Integer> color = (Setting<Integer>) slidingSetting;
            color.set(withChannel(color.get(), slidingRow.channel(), (int) Math.round(frac * 255)));
            return;
        }

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
        ModuleManager.get().markDirty();
    }

    @Override
    public boolean keyPressed(KeyEvent event) {
        if (bindingFor != null) {
            bindingFor.setKeyBind(event.key() == InputConstants.KEY_ESCAPE ? -1 : event.key());
            bindingFor = null;
            ModuleManager.get().markDirty();
            return true;
        }
        if (event.key() == InputConstants.KEY_BACKSPACE && !search.isEmpty()) {
            search = search.substring(0, search.length() - 1);
            return true;
        }
        // First ESC clears the search, the second one closes the menu.
        if (event.key() == InputConstants.KEY_ESCAPE && !search.isEmpty()) {
            search = "";
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
