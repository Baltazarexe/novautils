package dev.novautils.gui.screens;

import com.mojang.blaze3d.platform.InputConstants;
import dev.novautils.gui.GuiState;
import dev.novautils.gui.Theme;
import dev.novautils.modules.Category;
import dev.novautils.modules.Module;
import dev.novautils.modules.ModuleManager;
import dev.novautils.settings.Setting;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
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
 * The classic menu, Meteor style: a draggable panel per category, modules toggled with a left
 * click and their settings opened inline with a right click. Panel positions are remembered.
 * The other style is ClickGuiScreen; the button next to Edit HUD switches between them.
 */
public class ClassicGuiScreen extends Screen {
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

    public ClassicGuiScreen() {
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
    public void render(GuiGraphics g, int mouseX, int mouseY, float delta) {
        layout();
        float t = openProgress();

        drawBackground(g);

        // slide up a few pixels while opening
        g.pose().pushPose();
        g.pose().translate(0, (1f - t) * 8f, 0);

        drawSearch(g);
        drawHudButton(g, mouseX, mouseY);
        drawLogo(g);
        for (Panel panel : PANELS.values()) drawPanel(g, panel, mouseX, mouseY, delta);

        String hint = bindingFor != null
            ? "Press a key to bind " + bindingFor.name + "   (ESC clears it)"
            : "L-click toggle  ·  R-click settings  ·  M-click reset  ·  type to search";
        int hw = this.font.width(hint);
        int hy = this.height - 10;
        Theme.roundRect(g, this.width / 2 - hw / 2 - 6, hy - 2, hw + 12, 11, 0xA0070A12);
        g.drawString(this.font, hint, this.width / 2 - hw / 2, hy,
            bindingFor != null ? Theme.alpha(Theme.ACCENT, 0.6f + 0.4f * Theme.pulse(6f)) : Theme.TEXT_OFF);

        g.pose().popPose();

        // fade in: a dark veil that clears as the menu opens
        if (t < 1f) g.fill(0, 0, this.width, this.height, Theme.alpha(0xFF05070D, 1f - t));

        // Setting tooltips are set here, after the panels, so they draw on top of everything.
        if (hoveredSetting != null && !hoveredSetting.description.isEmpty()) {
            g.renderTooltip(this.font, Component.literal(hoveredSetting.description + "  (middle-click: reset)"), mouseX, mouseY);
        }
        hoveredSetting = null;
    }

    private final long openedAt = System.nanoTime();

    /** 0 -> 1 over the first ~250 ms, eased. */
    private float openProgress() {
        float raw = Math.min(1f, (System.nanoTime() - openedAt) / 250_000_000f);
        return 1f - (1f - raw) * (1f - raw) * (1f - raw);
    }

    private void drawBackground(GuiGraphics g) {
        g.fill(0, 0, this.width, this.height, 0x99000000);
    }

    private void drawLogo(GuiGraphics g) {
        int x = this.width - 10 - this.font.width("Nova Utils"), y = 11;
        if (x < STYLE_BTN_X + HUD_BTN_W + 10) return;
        g.drawString(this.font, "Nova", x, y, Theme.ACCENT, false);
        g.drawString(this.font, "Utils", x + this.font.width("Nova "), y, Theme.TEXT_DIM, false);
    }

    private static final int HUD_BTN_X = 164, HUD_BTN_Y = 8, HUD_BTN_W = 56, HUD_BTN_H = 14;

    private boolean overHudButton(int mx, int my) {
        return mx >= HUD_BTN_X && mx < HUD_BTN_X + HUD_BTN_W && my >= HUD_BTN_Y && my < HUD_BTN_Y + HUD_BTN_H;
    }

    private static final int STYLE_BTN_X = HUD_BTN_X + HUD_BTN_W + 4;

    private boolean overStyleButton(int mx, int my) {
        return mx >= STYLE_BTN_X && mx < STYLE_BTN_X + HUD_BTN_W && my >= HUD_BTN_Y && my < HUD_BTN_Y + HUD_BTN_H;
    }

    private void drawHudButton(GuiGraphics g, int mouseX, int mouseY) {
        boolean hover = overHudButton(mouseX, mouseY);
        Theme.rounded(g, HUD_BTN_X, HUD_BTN_Y, HUD_BTN_W, HUD_BTN_H, 5, hover ? 0xFF262A36 : 0xFF1C1F26);
        String label = "Edit HUD";
        g.drawString(this.font, label, HUD_BTN_X + HUD_BTN_W / 2 - this.font.width(label) / 2, HUD_BTN_Y + 3, hover ? Theme.TEXT : Theme.TEXT_DIM, false);
        boolean sh = overStyleButton(mouseX, mouseY);
        Theme.rounded(g, STYLE_BTN_X, HUD_BTN_Y, HUD_BTN_W, HUD_BTN_H, 5, sh ? 0xFF262A36 : 0xFF1C1F26);
        String style = "Nova menu";
        g.drawString(this.font, style, STYLE_BTN_X + HUD_BTN_W / 2 - this.font.width(style) / 2, HUD_BTN_Y + 3, sh ? Theme.TEXT : Theme.TEXT_DIM, false);
    }

    private void drawSearch(GuiGraphics g) {
        int w = 150, h = 14, x = 8, y = 8;
        boolean typing = !search.isEmpty();
        Theme.rounded(g, x, y, w, h, 5, typing ? 0xFF22252E : 0xFF1C1F26);
        g.drawString(this.font, "⌕", x + 5, y + 3, typing ? Theme.ACCENT : Theme.TEXT_OFF);
        boolean caret = (Theme.time() % 1f) < 0.5f;
        String shown = typing ? search + (caret ? "_" : "") : "Search modules...";
        g.drawString(this.font, shown, x + 16, y + 3, typing ? Theme.TEXT : Theme.TEXT_OFF);
    }

    private static String icon(Category c) {
        return switch (c.name()) {
            case "RENDER" -> "◉";
            case "MOVEMENT" -> "➤";
            case "COMBAT" -> "⚔";
            case "PLAYER" -> "☻";
            case "WORLD" -> "⛏";
            default -> "✦";
        };
    }

    private static String title(Category c) {
        String n = c.name();
        return n.charAt(0) + n.substring(1).toLowerCase(Locale.ROOT);
    }

    private void drawPanel(GuiGraphics g, Panel panel, int mouseX, int mouseY, float delta) {
        int x = panel.x, y = panel.y, h = panelHeight(panel);
        float accentT = panel.category.ordinal() / (float) Math.max(1, Category.values().length - 1);
        int accent = Theme.ACCENT;

        Theme.softShadow(g, x, y, PANEL_W, h, 6, 0.5f);
        Theme.rounded(g, x, y, PANEL_W, h, 6, 0xF2141519);
        g.fill(x + 6, y + HEADER_H - 1, x + PANEL_W - 6, y + HEADER_H, 0x14FFFFFF);

        // header: icon, title, on/total pill, open/closed chevron
        g.drawString(this.font, icon(panel.category), x + 5, y + 4, accent);
        g.drawString(this.font, title(panel.category), x + 16, y + 4, Theme.TEXT);

        int total = 0, on = 0;
        for (Module m : ModuleManager.get().getModules()) {
            if (m.category != panel.category) continue;
            total++;
            if (m.isEnabled()) on++;
        }
        String count = on + "/" + total;
        int cw = this.font.width(count) + 6;
        int px = x + PANEL_W - 15 - cw;
        Theme.roundRect(g, px, y + 3, cw, 9, on > 0 ? Theme.alpha(accent, 0.22f) : 0x40000000);
        g.drawString(this.font, count, px + 3, y + 4, on > 0 ? accent : Theme.TEXT_OFF);
        g.drawString(this.font, panel.open ? "⌄" : "›", x + PANEL_W - 10, y + (panel.open ? 2 : 4), Theme.TEXT_DIM);

        if (!panel.open) return;

        g.enableScissor(x, y + HEADER_H, x + PANEL_W, y + h);
        for (Row row : rows) {
            if (row.panel() != panel || !visible(row)) continue;
            switch (row.type()) {
                case MODULE -> drawModuleRow(g, row, mouseX, mouseY, delta);
                case BOOL -> drawBoolRow(g, row, mouseX, mouseY);
                case NUMBER -> drawNumberRow(g, row, mouseX, mouseY);
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
            int trackTop = y + HEADER_H + 2, trackH = h - HEADER_H - 4;
            int thumbH = Math.max(12, (int) (trackH * (panel.viewH / (float) panel.contentH)));
            int thumbY = trackTop + (int) ((trackH - thumbH) * (panel.scroll / (float) (panel.contentH - panel.viewH)));
            g.fill(x + PANEL_W - 3, trackTop, x + PANEL_W - 2, trackTop + trackH, 0x18FFFFFF);
            Theme.rounded(g, x + PANEL_W - 3, thumbY, 2, thumbH, 1, Theme.alpha(Theme.ACCENT, 0.7f));
        }
    }

    /** Thin vertical guide on the left of settings, so it's clear which module they belong to. */
    private void settingGuide(GuiGraphics g, Row row) {
        g.fill(row.x() + 1, row.y(), row.x() + row.w() - 1, row.y() + row.h(), 0x26000000);
        g.fill(row.x() + 5, row.y(), row.x() + 6, row.y() + row.h(), Theme.alpha(Theme.ACCENT, 0.28f));
    }

    private void hoverFill(GuiGraphics g, Row row, int mouseX, int mouseY) {
        if (hovered(row, mouseX, mouseY)) {
            g.fill(row.x() + 6, row.y(), row.x() + row.w() - 1, row.y() + row.h(), 0x10FFFFFF);
        }
    }

    private void drawModuleRow(GuiGraphics g, Row row, int mouseX, int mouseY, float delta) {
        Module module = row.module();
        boolean hover = hovered(row, mouseX, mouseY);
        float anim = Theme.approach(HOVER.getOrDefault(module, 0f), hover ? 1f : 0f, delta + 0.6f);
        HOVER.put(module, anim);
        int x = row.x(), y = row.y(), w = row.w(), h = row.h();

        if (module.isEnabled()) {
            Theme.rounded(g, x + 3, y + 1, w - 6, h - 2, 3, 0x228B9CFF);
            g.fill(x + 3, y + 3, x + 4, y + h - 3, Theme.ACCENT);
        }
        if (anim > 0.01f) g.fill(x + 1, y, x + w - 1, y + h, Theme.alpha(Theme.ROW_HOVER, anim));
        g.fill(x + 6, y + h - 1, x + w - 6, y + h, 0x0CFFFFFF);

        int textColor = module.isEnabled() ? Theme.TEXT : Theme.mix(Theme.TEXT_OFF, Theme.TEXT_DIM, anim);
        g.drawString(this.font, module.name, x + 7 + (int) (anim * 2), y + 3, textColor);
        if (module.getKeyBind() != -1) {
            String key = keyName(module.getKeyBind());
            if (key.length() > 5) key = key.substring(0, 5);
            int kw = this.font.width(key);
            int kx = x + w - 16 - kw;
            Theme.roundRect(g, kx - 2, y + 2, kw + 4, 9, 0x50000000);
            g.drawString(this.font, key, kx, y + 3, Theme.TEXT_OFF);
        }
        boolean open = EXPANDED.contains(module);
        g.drawString(this.font, open ? "⌄" : "›", x + w - 9, y + (open ? 1 : 3), open ? Theme.ACCENT : Theme.TEXT_OFF);

        if (hover && !module.description.isEmpty()) {
            g.renderTooltip(this.font, Component.literal(module.description), mouseX, mouseY);
        }
    }

    private void drawBoolRow(GuiGraphics g, Row row, int mouseX, int mouseY) {
        @SuppressWarnings("unchecked")
        Setting<Boolean> setting = (Setting<Boolean>) row.setting();
        boolean on = setting.get();
        settingGuide(g, row);
        hoverFill(g, row, mouseX, mouseY);

        g.drawString(this.font, setting.name, row.x() + 12, row.y() + 2, on ? Theme.TEXT : Theme.TEXT_DIM);

        Theme.toggle(g, row.x() + row.w() - 22, row.y() + 2, on ? 1f : 0f);
    }

    private void drawNumberRow(GuiGraphics g, Row row, int mouseX, int mouseY) {
        Setting<?> setting = row.setting();
        settingGuide(g, row);
        hoverFill(g, row, mouseX, mouseY);
        double value = ((Number) setting.get()).doubleValue();
        double min = Double.isFinite(setting.min) ? setting.min : 0;
        double max = Double.isFinite(setting.max) ? setting.max : 1;
        float frac = max > min ? (float) ((value - min) / (max - min)) : 0f;

        boolean isInt = setting.get() instanceof Integer;
        String shown = isInt ? String.valueOf((int) value) : String.format(Locale.ROOT, "%.2f", value);
        g.drawString(this.font, setting.name, row.x() + 12, row.y() + 1, Theme.TEXT_DIM);
        g.drawString(this.font, shown, row.x() + row.w() - 8 - this.font.width(shown), row.y() + 1, Theme.ACCENT);

        // track + gradient fill + glowing knob (same geometry the slider input uses)
        int tx = row.x() + 12, ty = row.y() + row.h() - 3, tw = row.w() - 24;
        int fill = (int) (tw * frac);
        g.fill(tx, ty, tx + tw, ty + 2, Theme.TRACK);
        if (fill > 0) Theme.hGradient(g, tx, ty, tx + fill, ty + 2, Theme.ACCENT, Theme.accentAt(frac));
        int knob = tx + fill;
        g.fill(knob - 2, ty - 2, knob + 3, ty + 4, Theme.alpha(Theme.accentAt(frac), 0.35f));
        g.fill(knob - 1, ty - 1, knob + 2, ty + 3, 0xFFFFFFFF);
    }

    private void drawColorRow(GuiGraphics g, Row row, int mouseX, int mouseY) {
        Setting<?> setting = row.setting();
        settingGuide(g, row);
        hoverFill(g, row, mouseX, mouseY);

        boolean open = OPEN_COLORS.contains(setting);
        g.drawString(this.font, (open ? "⌄ " : "› ") + setting.name, row.x() + 9, row.y() + (open ? 1 : 2), Theme.TEXT_DIM);

        int color = (Integer) setting.get();
        int sx = row.x() + row.w() - 22, sy = row.y() + 2;
        g.fill(sx - 1, sy - 1, sx + 15, sy + 9, 0xFF000000);
        // checkerboard so alpha is readable
        g.fill(sx, sy, sx + 14, sy + 8, 0xFF777777);
        g.fill(sx, sy, sx + 7, sy + 4, 0xFF444444);
        g.fill(sx + 7, sy + 4, sx + 14, sy + 8, 0xFF444444);
        g.fill(sx, sy, sx + 14, sy + 8, color);
    }

    private void drawChannelRow(GuiGraphics g, Row row) {
        settingGuide(g, row);
        int color = (Integer) row.setting().get();
        int channel = row.channel();
        int value = channelValue(color, channel);
        String name = switch (channel) { case 0 -> "R"; case 1 -> "G"; case 2 -> "B"; default -> "A"; };

        g.drawString(this.font, name + " " + value, row.x() + 14, row.y() + 1, Theme.TEXT_DIM);

        int tx = row.x() + 34, ty = row.y() + 5, tw = row.w() - 44;
        int bar = switch (channel) { case 0 -> 0xFFFF4D6D; case 1 -> 0xFF34D399; case 2 -> 0xFF60A5FA; default -> 0xFFCBD5E1; };
        int fill = (int) (tw * (value / 255f));
        g.fill(tx, ty, tx + tw, ty + 2, Theme.TRACK);
        Theme.hGradient(g, tx, ty, tx + fill, ty + 2, Theme.alpha(bar, 0.4f), bar);
        g.fill(tx + fill - 1, ty - 1, tx + fill + 2, ty + 3, 0xFFFFFFFF);
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

    private void drawEnumRow(GuiGraphics g, Row row, int mouseX, int mouseY) {
        Setting<?> setting = row.setting();
        settingGuide(g, row);
        hoverFill(g, row, mouseX, mouseY);
        g.drawString(this.font, setting.name, row.x() + 12, row.y() + 2, Theme.TEXT_DIM);
        String value = ((Enum<?>) setting.get()).name().replace('_', ' ');
        String shown = "‹ " + value + " ›";
        g.drawString(this.font, shown, row.x() + row.w() - 8 - this.font.width(shown), row.y() + 2, Theme.ACCENT);
    }

    private void drawKeybindRow(GuiGraphics g, Row row, int mouseX, int mouseY) {
        Module module = row.module();
        settingGuide(g, row);
        hoverFill(g, row, mouseX, mouseY);
        g.drawString(this.font, "bind", row.x() + 12, row.y() + 2, Theme.TEXT_DIM);
        boolean binding = bindingFor == module;
        String key = binding ? "press a key..." : keyName(module.getKeyBind());
        int kw = this.font.width(key);
        int kx = row.x() + row.w() - 8 - kw;
        Theme.roundRect(g, kx - 3, row.y() + 1, kw + 6, 10,
            binding ? Theme.alpha(Theme.ACCENT, 0.15f + 0.2f * Theme.pulse(8f)) : 0x50000000);
        g.drawString(this.font, key, kx, row.y() + 2, binding ? Theme.ACCENT : Theme.TEXT_OFF);
    }

    private String keyName(int keyCode) {
        if (keyCode == -1) return "none";
        return dev.novautils.utils.PlatformCompat.keyDisplayName(keyCode);
    }

    // ------------------------------------------------------------------ input

    @Override
    public boolean mouseClicked(double evX, double evY, int evButton) {
        int mx = (int) evX, my = (int) evY;

        if (overStyleButton(mx, my)) {
            dev.novautils.gui.GuiState.get().setClassic(false);
            dev.novautils.utils.PlatformCompat.openScreen(net.minecraft.client.Minecraft.getInstance(), new ClickGuiScreen());
            return true;
        }
        if (overHudButton(mx, my)) {
            dev.novautils.utils.PlatformCompat.openScreen(net.minecraft.client.Minecraft.getInstance(), new HudEditorScreen(this));
            return true;
        }

        // Middle click: reset whatever setting is under the mouse (or clear a keybind).
        if (evButton == 2) {
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
                if (evButton == 1) {
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
                    if (evButton == 1) {
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
                case ENUM -> cycleEnum(row.setting(), evButton == 1 ? -1 : 1);
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
        return super.mouseClicked(evX, evY, evButton);
    }

    @Override
    public boolean mouseDragged(double evX, double evY, int evButton, double dx, double dy) {
        if (dragging != null) {
            dragging.x = (int) evX - dragOffX;
            dragging.y = (int) evY - dragOffY;
            return true;
        }
        if (slidingSetting != null) {
            applySlider((int) evX);
            return true;
        }
        return super.mouseDragged(evX, evY, evButton, dx, dy);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollY) {
        for (Panel panel : PANELS.values()) {
            int h = panelHeight(panel);
            if (mouseX >= panel.x && mouseX < panel.x + PANEL_W && mouseY >= panel.y && mouseY < panel.y + h) {
                if (panel.scrollable()) {
                    panel.scroll = (float) Math.max(0, Math.min(panel.contentH - panel.viewH, panel.scroll - scrollY * 18));
                    return true;
                }
            }
        }
        return super.mouseScrolled(mouseX, mouseY, scrollY);
    }

    @Override
    public boolean mouseReleased(double evX, double evY, int evButton) {
        if (slidingSetting != null) ModuleManager.get().markDirty();
        dragging = null;
        slidingSetting = null;
        slidingRow = null;
        return super.mouseReleased(evX, evY, evButton);
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
    public boolean keyPressed(int evKey, int evScan, int evMods) {
        if (bindingFor != null) {
            bindingFor.setKeyBind(evKey == InputConstants.KEY_ESCAPE ? -1 : evKey);
            bindingFor = null;
            ModuleManager.get().markDirty();
            return true;
        }
        if (evKey == InputConstants.KEY_BACKSPACE && !search.isEmpty()) {
            search = search.substring(0, search.length() - 1);
            return true;
        }
        // First ESC clears the search, the second one closes the menu.
        if (evKey == InputConstants.KEY_ESCAPE && !search.isEmpty()) {
            search = "";
            return true;
        }
        return super.keyPressed(evKey, evScan, evMods);
    }

    @Override
    public boolean charTyped(char evChar, int evMods) {
        if (bindingFor == null && net.minecraft.SharedConstants.isAllowedChatCharacter(evChar)) {
            search += String.valueOf(evChar);
            return true;
        }
        return super.charTyped(evChar, evMods);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
