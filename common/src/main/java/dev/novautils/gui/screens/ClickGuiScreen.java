package dev.novautils.gui.screens;

import com.mojang.blaze3d.platform.InputConstants;
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
import net.minecraft.util.FormattedCharSequence;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * The in-game menu: one centred window with soft rounded corners. Categories down the left, that
 * category's modules in the middle (switch on the right of each), and the selected module's
 * settings on the right. Everything eases: hover highlights fade, switches slide, scrolling glides,
 * and the window settles in when it opens.
 *
 * <p>Mouse: left-click a module to switch it on/off, right-click it to open its settings,
 * middle-click a setting to reset it. Typing anywhere searches all modules.
 */
public class ClickGuiScreen extends Screen {
    private static final int SIDEBAR = 96;
    private static final int RADIUS = 7;
    private static final int MODULE_H = 26;
    private static final int SET_H = 20;
    private static final int NUM_H = 26;
    private static final int CHANNEL_H = 15;

    private enum Pane { LIST, SETTINGS }
    private enum RowType { MODULE, BOOL, NUMBER, ENUM, KEYBIND, COLOR, CHANNEL }

    /** {@code pair}: the colour setting shown inline next to a switch (Xray "diamond" + "diamond-color"). */
    private record Row(Pane pane, RowType type, int x, int y, int w, int h, Module module, Setting<?> setting, int channel, Setting<?> pair) {}

    // remembered while the game runs
    private static Category selectedCategory = Category.values()[0];
    private static Module selected;
    private static final Set<Setting<?>> OPEN_COLORS = new HashSet<>();
    private static float listScroll, listScrollTarget, setScroll, setScrollTarget;

    private final Map<Object, Float> anim = new HashMap<>();
    private final List<Row> rows = new ArrayList<>();

    private String search = "";
    private Setting<?> slidingSetting;
    private Row slidingRow;
    private Module bindingFor;
    private Setting<?> hoveredSetting;
    private final long openedAt = System.nanoTime();
    private long lastFrame = System.nanoTime();

    // window geometry, recomputed every frame
    private int wx, wy, ww, wh, listX, listW, setX, setW, bodyTop, bodyBottom, setRowsTop;

    public ClickGuiScreen() {
        super(Component.literal("NovaUtils"));
    }

    // ------------------------------------------------------------------ test hooks / API

    /** Opens (selects) or closes a module's settings (also used by the in-game test). */
    public static void setExpanded(Module module, boolean expanded) {
        if (expanded) {
            selected = module;
            selectedCategory = module.category;
            setScroll = setScrollTarget = 0;
        } else if (selected == module) {
            selected = null;
        }
    }

    /** Opens or closes a colour setting's R/G/B/A sliders (also used by the in-game test). */
    public static void setColorExpanded(Setting<?> setting, boolean expanded) {
        if (expanded) OPEN_COLORS.add(setting);
        else OPEN_COLORS.remove(setting);
    }

    /**
     * Centre of a row from the last frame: the module's row in the list ({@code setting == null}),
     * one of its settings, or its keybind row ({@code "bind"}). Null unless fully on screen.
     */
    public int[] rowCenter(Module module, String setting) {
        Row r = findRow(module, setting);
        return r == null ? null : new int[]{r.x() + r.w() / 2, r.y() + r.h() / 2};
    }

    /** A point on a number setting's slider track, {@code frac} 0 = left end, 1 = right end. */
    public int[] sliderPoint(Module module, String setting, float frac) {
        Row r = findRow(module, setting);
        if (r == null || r.type() != RowType.NUMBER) return null;
        int[] t = track(r);
        return new int[]{t[0] + Math.round(t[2] * frac), t[1]};
    }

    public int[] categoryCenter(Category c) {
        int i = c.ordinal();
        return new int[]{wx + SIDEBAR / 2, categoryY(i) + 8};
    }

    public int[] hudButtonCenter() {
        return new int[]{wx + SIDEBAR / 2, hudButtonY() + 7};
    }

    /** Somewhere inside the module list / settings pane (for scrolling in tests). */
    public int[] listCenter() {
        return new int[]{listX + listW / 2, (bodyTop + bodyBottom) / 2};
    }

    public int[] settingsCenter() {
        return new int[]{setX + setW / 2, (setRowsTop + bodyBottom) / 2};
    }

    public String searchText() {
        return search;
    }

    private Row findRow(Module module, String setting) {
        for (Row row : rows) {
            if (row.module() != module) continue;
            boolean match = setting == null ? row.type() == RowType.MODULE
                : setting.equals("bind") ? row.type() == RowType.KEYBIND
                : row.setting() != null && row.type() != RowType.CHANNEL && row.setting().name.equals(setting);
            if (match && fullyVisible(row)) return row;
        }
        return null;
    }

    // ------------------------------------------------------------------ geometry

    private void computeGeometry() {
        ww = Math.min(520, this.width - 16);
        wh = Math.min(300, this.height - 16);
        wx = (this.width - ww) / 2;
        wy = (this.height - wh) / 2;
        listX = wx + SIDEBAR;
        listW = (int) ((ww - SIDEBAR) * 0.42f);
        setX = listX + listW;
        setW = wx + ww - setX;
        bodyTop = wy + 30;
        bodyBottom = wy + wh - 8;
    }

    private int categoryY(int i) {
        return wy + 58 + i * 19;
    }

    private int hudButtonY() {
        return wy + wh - 44;
    }

    private boolean fullyVisible(Row r) {
        int top = r.pane() == Pane.LIST ? bodyTop : setRowsTop;
        return r.y() >= top && r.y() + r.h() <= bodyBottom;
    }

    private boolean over(Row r, int mx, int my) {
        int top = r.pane() == Pane.LIST ? bodyTop : setRowsTop;
        return mx >= r.x() && mx < r.x() + r.w() && my >= Math.max(r.y(), top) && my < Math.min(r.y() + r.h(), bodyBottom);
    }

    /** {x, y, width} of a slider track inside its row. */
    private static int[] track(Row r) {
        if (r.type() == RowType.CHANNEL) return new int[]{r.x() + 30, r.y() + r.h() / 2, r.w() - 42};
        return new int[]{r.x() + 10, r.y() + r.h() - 6, r.w() - 20};
    }

    // ------------------------------------------------------------------ layout

    private boolean matchesSearch(Module m) {
        if (search.isEmpty()) return true;
        String q = search.toLowerCase(Locale.ROOT);
        return m.name.toLowerCase(Locale.ROOT).contains(q) || m.description.toLowerCase(Locale.ROOT).contains(q);
    }

    private List<Module> listedModules() {
        List<Module> out = new ArrayList<>();
        for (Module m : ModuleManager.get().getModules()) {
            if (search.isEmpty() ? m.category == selectedCategory : matchesSearch(m)) out.add(m);
        }
        return out;
    }

    private RowType rowTypeOf(Setting<?> s) {
        Object v = s.get();
        if (s.color) return RowType.COLOR;
        if (v instanceof Boolean) return RowType.BOOL;
        if (v instanceof Number) return RowType.NUMBER;
        if (v instanceof Enum<?>) return RowType.ENUM;
        return null;
    }

    /** The "<name>-color" setting that belongs to switch {@code s}, if the module has one. */
    private static Setting<?> pairedColor(Module m, Setting<?> s) {
        for (Setting<?> o : m.getSettings()) if (o.color && o.name.equals(s.name + "-color")) return o;
        return null;
    }

    /** The switch a "<name>-color" setting belongs to, if there is one. */
    private static Setting<?> pairedSwitch(Module m, Setting<?> color) {
        if (!color.name.endsWith("-color")) return null;
        String base = color.name.substring(0, color.name.length() - 6);
        for (Setting<?> o : m.getSettings()) if (o.name.equals(base) && o.get() instanceof Boolean) return o;
        return null;
    }

    private List<FormattedCharSequence> descriptionLines() {
        if (selected == null || selected.description.isEmpty()) return List.of();
        List<FormattedCharSequence> lines = this.font.split(Component.literal(selected.description), setW - 24);
        return lines.size() > 2 ? lines.subList(0, 2) : lines;
    }

    private void layout() {
        rows.clear();

        // module list
        List<Module> mods = listedModules();
        int contentH = mods.size() * (MODULE_H + 2);
        int viewH = bodyBottom - bodyTop;
        listScrollTarget = Math.max(0, Math.min(listScrollTarget, Math.max(0, contentH - viewH)));
        int y = bodyTop - Math.round(listScroll);
        for (Module m : mods) {
            rows.add(new Row(Pane.LIST, RowType.MODULE, listX + 6, y, listW - 12, MODULE_H, m, null, -1, null));
            y += MODULE_H + 2;
        }

        // settings of the selected module
        setRowsTop = wy + 34 + descriptionLines().size() * 10 + 6;
        if (selected == null) return;
        int sx = setX + 6, sw = setW - 12;
        int sy = setRowsTop - Math.round(setScroll);
        int start = sy;
        for (Setting<?> s : selected.getSettings()) {
            RowType type = rowTypeOf(s);
            if (type == null) continue;
            if (type == RowType.COLOR && pairedSwitch(selected, s) != null) continue; // drawn inline with its switch
            if (!s.isVisible()) continue;
            Setting<?> pair = type == RowType.BOOL ? pairedColor(selected, s) : null;
            int h = type == RowType.NUMBER ? NUM_H : SET_H;
            rows.add(new Row(Pane.SETTINGS, type, sx, sy, sw, h, selected, s, -1, pair));
            sy += h + 2;
            Setting<?> colour = type == RowType.COLOR ? s : pair;
            if (colour != null && OPEN_COLORS.contains(colour)) {
                for (int c = 0; c < 4; c++) {
                    rows.add(new Row(Pane.SETTINGS, RowType.CHANNEL, sx, sy, sw, CHANNEL_H, selected, colour, c, null));
                    sy += CHANNEL_H;
                }
                sy += 2;
            }
        }
        rows.add(new Row(Pane.SETTINGS, RowType.KEYBIND, sx, sy, sw, SET_H, selected, null, -1, null));
        sy += SET_H + 2;
        int setContent = sy - start;
        setScrollTarget = Math.max(0, Math.min(setScrollTarget, Math.max(0, setContent - (bodyBottom - setRowsTop))));
    }

    // ------------------------------------------------------------------ drawing

    private float animate(Object key, float target, float speed, float dt) {
        float v = anim.getOrDefault(key, target);
        v += (target - v) * Math.min(1f, dt * speed);
        if (Math.abs(target - v) < 0.002f) v = target;
        anim.put(key, v);
        return v;
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor g, int mouseX, int mouseY, float delta) {
        long now = System.nanoTime();
        float dt = Math.min(0.1f, (now - lastFrame) / 1e9f);
        lastFrame = now;
        listScroll += (listScrollTarget - listScroll) * Math.min(1f, dt * 16f);
        setScroll += (setScrollTarget - setScroll) * Math.min(1f, dt * 16f);

        computeGeometry();
        layout();

        float t = Math.min(1f, (now - openedAt) / 220_000_000f);
        float ease = 1f - (1f - t) * (1f - t) * (1f - t);

        // dim the world softly
        g.fill(0, 0, this.width, this.height, Theme.alpha(0x99000000, ease));

        g.pose().pushMatrix();
        float scale = 0.96f + 0.04f * ease;
        g.pose().translate(this.width / 2f, this.height / 2f + (1f - ease) * 6f);
        g.pose().scale(scale, scale);
        g.pose().translate(-this.width / 2f, -this.height / 2f);

        drawWindow(g);
        drawSidebar(g, mouseX, mouseY, dt);
        drawList(g, mouseX, mouseY, dt);
        drawSettings(g, mouseX, mouseY, dt);

        g.pose().popMatrix();

        if (ease < 1f) g.fill(0, 0, this.width, this.height, Theme.alpha(0xFF0B0C10, (1f - ease) * 0.9f));
        hoveredSetting = null;
    }

    private void drawWindow(GuiGraphicsExtractor g) {
        Theme.softShadow(g, wx, wy, ww, wh, RADIUS, 1.4f);
        Theme.rounded(g, wx - 1, wy - 1, ww + 2, wh + 2, RADIUS + 1, 0xFF22252D);
        Theme.rounded(g, wx, wy, ww, wh, RADIUS, 0xFF15171C);
        // sidebar: a slightly darker strip with the same rounded left corners
        Theme.rounded(g, wx, wy, SIDEBAR + RADIUS, wh, RADIUS, 0xFF111216);
        g.fill(wx + SIDEBAR, wy, wx + SIDEBAR + RADIUS, wy + wh, 0xFF15171C);
        g.fill(wx + SIDEBAR, wy + 10, wx + SIDEBAR + 1, wy + wh - 10, 0x10FFFFFF);
        g.fill(setX, wy + 10, setX + 1, wy + wh - 10, 0x10FFFFFF);
    }

    private void drawSidebar(GuiGraphicsExtractor g, int mx, int my, float dt) {
        // logo
        int lx = wx + 12, ly = wy + 12;
        Theme.rounded(g, lx, ly - 1, 10, 10, 3, Theme.ACCENT);
        g.text(this.font, "N", lx + 2, ly, 0xFF15171C, false);
        g.text(this.font, "Nova", lx + 15, ly, Theme.TEXT, false);
        g.text(this.font, "Utils", lx + 15 + this.font.width("Nova") + 2, ly, Theme.TEXT_OFF, false);

        // search box
        int sx = wx + 8, sy = wy + 30, sw = SIDEBAR - 16;
        boolean typing = !search.isEmpty();
        Theme.rounded(g, sx, sy, sw, 16, 5, typing ? 0xFF1E2129 : 0xFF181A20);
        if (typing) {
            Theme.rounded(g, sx, sy + 15, sw, 1, 0, Theme.alpha(Theme.ACCENT, 0.7f));
        }
        g.text(this.font, "⌕", sx + 5, sy + 4, typing ? Theme.ACCENT : Theme.TEXT_OFF, false);
        String shown = typing ? search : "Search";
        if (typing && (Theme.time() % 1f) < 0.5f) shown += "|";
        String clipped = this.font.plainSubstrByWidth(shown, sw - 20);
        g.text(this.font, clipped, sx + 15, sy + 4, typing ? Theme.TEXT : Theme.TEXT_OFF, false);

        // categories
        for (Category c : Category.values()) {
            int y = categoryY(c.ordinal());
            boolean sel = search.isEmpty() && c == selectedCategory;
            boolean hover = mx >= wx + 6 && mx < wx + SIDEBAR - 6 && my >= y && my < y + 16;
            float h = animate("cat" + c.ordinal(), hover ? 1f : 0f, 14f, dt);
            float s = animate("catsel" + c.ordinal(), sel ? 1f : 0f, 12f, dt);
            if (s > 0.01f) Theme.rounded(g, wx + 6, y, SIDEBAR - 12, 16, 5, Theme.alpha(0x2A8B9CFF, s));
            else if (h > 0.01f) Theme.rounded(g, wx + 6, y, SIDEBAR - 12, 16, 5, Theme.alpha(0x10FFFFFF, h));
            if (s > 0.01f) Theme.rounded(g, wx + 6, y + 4, 2, 8, 1, Theme.alpha(Theme.ACCENT, s));
            int textColor = Theme.mix(Theme.mix(Theme.TEXT_DIM, Theme.TEXT, h * 0.6f), Theme.TEXT, s);
            g.text(this.font, icon(c), wx + 14, y + 4, Theme.mix(Theme.TEXT_OFF, Theme.ACCENT, Math.max(s, h * 0.5f)), false);
            g.text(this.font, title(c), wx + 27, y + 4, textColor, false);
            int on = 0;
            for (Module m : ModuleManager.get().getModules()) if (m.category == c && m.isEnabled()) on++;
            if (on > 0) {
                String n = String.valueOf(on);
                g.text(this.font, n, wx + SIDEBAR - 12 - this.font.width(n), y + 4, Theme.alpha(Theme.ACCENT, 0.8f), false);
            }
        }

        // Edit HUD + footer
        int by = hudButtonY();
        boolean hover = mx >= wx + 8 && mx < wx + SIDEBAR - 8 && my >= by && my < by + 15;
        float hb = animate("hudbtn", hover ? 1f : 0f, 14f, dt);
        Theme.rounded(g, wx + 8, by, SIDEBAR - 16, 15, 5, Theme.mix(0xFF1C1F26, 0xFF262A36, hb));
        String label = "Edit HUD";
        g.text(this.font, label, wx + SIDEBAR / 2 - this.font.width(label) / 2, by + 4, Theme.mix(Theme.TEXT_DIM, Theme.TEXT, hb), false);

        int total = 0, on = 0;
        for (Module m : ModuleManager.get().getModules()) {
            total++;
            if (m.isEnabled()) on++;
        }
        String footer = on + " of " + total + " on";
        g.text(this.font, footer, wx + SIDEBAR / 2 - this.font.width(footer) / 2, wy + wh - 20, Theme.TEXT_OFF, false);
    }

    private void drawList(GuiGraphicsExtractor g, int mx, int my, float dt) {
        String header = search.isEmpty() ? title(selectedCategory) : "Results";
        g.text(this.font, header, listX + 10, wy + 12, Theme.TEXT, false);
        List<Module> mods = listedModules();
        int on = 0;
        for (Module m : mods) if (m.isEnabled()) on++;
        String count = on + "/" + mods.size();
        g.text(this.font, count, listX + listW - 10 - this.font.width(count), wy + 12, Theme.TEXT_OFF, false);

        g.enableScissor(listX, bodyTop, listX + listW, bodyBottom);
        for (Row r : rows) {
            if (r.pane() != Pane.LIST) continue;
            if (r.y() + r.h() < bodyTop || r.y() > bodyBottom) continue;
            Module m = r.module();
            boolean hover = over(r, mx, my);
            float h = animate(m, hover ? 1f : 0f, 14f, dt);
            float sw = animate("sw" + m.name, m.isEnabled() ? 1f : 0f, 16f, dt);
            boolean isSel = m == selected;
            int bg = isSel ? 0xFF22252E : Theme.alpha(0x10FFFFFF, h);
            if (isSel || h > 0.01f) Theme.rounded(g, r.x(), r.y(), r.w(), r.h(), 5, bg);
            if (isSel) Theme.rounded(g, r.x(), r.y() + 5, 2, r.h() - 10, 1, Theme.ACCENT);

            int nameColor = Theme.mix(Theme.mix(Theme.TEXT_DIM, Theme.TEXT, h * 0.5f), Theme.TEXT, sw);
            // name on top, a one-line description (or the category, when searching) under it
            g.text(this.font, m.name, r.x() + 8, r.y() + 4, nameColor, false);
            String sub = search.isEmpty() ? m.description : title(m.category) + "  ·  " + m.description;
            int subW = r.w() - 40 - (m.getKeyBind() != -1 ? this.font.width(keyName(m.getKeyBind())) + 8 : 0);
            String shortSub = this.font.plainSubstrByWidth(sub, subW);
            if (shortSub.length() < sub.length()) shortSub = this.font.plainSubstrByWidth(sub, subW - this.font.width("…")) + "…";
            g.text(this.font, shortSub, r.x() + 8, r.y() + 14, Theme.alpha(Theme.TEXT_OFF, 0.9f), false);
            if (m.getKeyBind() != -1) {
                String key = keyName(m.getKeyBind());
                if (key.length() > 4) key = key.substring(0, 4);
                int kw = this.font.width(key);
                int kx = r.x() + r.w() - 26 - kw;
                Theme.rounded(g, kx - 3, r.y() + 3, kw + 6, 10, 3, 0xFF1C1E25);
                g.text(this.font, key, kx, r.y() + 4, Theme.TEXT_OFF, false);
            }
            Theme.toggle(g, r.x() + r.w() - 22, r.y() + 8, sw);
        }
        g.disableScissor();
        drawScrollbar(g, listX + listW - 4, bodyTop, bodyBottom, listScroll, mods.size() * (MODULE_H + 2));
    }

    private void drawScrollbar(GuiGraphicsExtractor g, int x, int top, int bottom, float scroll, int content) {
        int view = bottom - top;
        if (content <= view) return;
        int thumb = Math.max(16, view * view / content);
        int ty = top + (int) ((view - thumb) * (scroll / (content - view)));
        Theme.rounded(g, x, ty, 2, thumb, 1, 0x40FFFFFF);
    }

    private void drawSettings(GuiGraphicsExtractor g, int mx, int my, float dt) {
        if (selected == null) {
            String a = "No module selected", b = "Right-click a module", c = "to see its settings";
            int cy = (wy + wy + wh) / 2;
            g.text(this.font, a, setX + setW / 2 - this.font.width(a) / 2, cy - 14, Theme.TEXT_DIM, false);
            g.text(this.font, b, setX + setW / 2 - this.font.width(b) / 2, cy, Theme.TEXT_OFF, false);
            g.text(this.font, c, setX + setW / 2 - this.font.width(c) / 2, cy + 10, Theme.TEXT_OFF, false);
            return;
        }

        // header: name and its on/off switch (clickable), then the description
        g.text(this.font, selected.name, setX + 12, wy + 12, Theme.TEXT, false);
        float on = animate("hdr", selected.isEnabled() ? 1f : 0f, 16f, dt);
        int[] hs = headerSwitch();
        Theme.toggle(g, hs[0], hs[1], on);
        int dy = wy + 26;
        for (FormattedCharSequence line : descriptionLines()) {
            g.text(this.font, line, setX + 12, dy, Theme.TEXT_OFF, false);
            dy += 10;
        }
        g.fill(setX + 10, setRowsTop - 4, setX + setW - 10, setRowsTop - 3, 0x0CFFFFFF);

        g.enableScissor(setX, setRowsTop, setX + setW, bodyBottom);
        int content = 0;
        for (Row r : rows) {
            if (r.pane() != Pane.SETTINGS) continue;
            content = Math.max(content, r.y() + r.h() + Math.round(setScroll) - setRowsTop);
            if (r.y() + r.h() < setRowsTop || r.y() > bodyBottom) continue;
            boolean hover = over(r, mx, my);
            Object key = r.setting() != null ? (r.channel() >= 0 ? r.setting().name + "#" + r.channel() : r.setting()) : "bind";
            float h = animate(key, hover ? 1f : 0f, 14f, dt);
            if (h > 0.01f && r.type() != RowType.CHANNEL) Theme.rounded(g, r.x(), r.y(), r.w(), r.h(), 5, Theme.alpha(0x0EFFFFFF, h));
            if (hover && r.setting() != null && r.type() != RowType.CHANNEL) hoveredSetting = r.setting();
            switch (r.type()) {
                case BOOL -> drawBool(g, r, dt);
                case NUMBER -> drawNumber(g, r);
                case ENUM -> drawEnum(g, r, h);
                case COLOR -> drawColor(g, r);
                case CHANNEL -> drawChannel(g, r);
                case KEYBIND -> drawKeybind(g, r);
                default -> { }
            }
        }
        g.disableScissor();
        drawScrollbar(g, setX + setW - 5, setRowsTop, bodyBottom, setScroll, content);

        // hovered setting's description along the bottom edge
        if (hoveredSetting != null && !hoveredSetting.description.isEmpty()) {
            String d = this.font.plainSubstrByWidth(hoveredSetting.description, setW - 20);
            Theme.rounded(g, setX + 6, bodyBottom - 14, setW - 12, 13, 4, 0xF01B1D24);
            g.text(this.font, d, setX + 10, bodyBottom - 11, Theme.TEXT_DIM, false);
        }
    }

    private static String pretty(String settingName) {
        String s = settingName.replace('-', ' ');
        return Character.toUpperCase(s.charAt(0)) + s.substring(1);
    }

    private void drawBool(GuiGraphicsExtractor g, Row r, float dt) {
        @SuppressWarnings("unchecked")
        Setting<Boolean> s = (Setting<Boolean>) r.setting();
        float t = animate("b" + s.hashCode(), s.get() ? 1f : 0f, 16f, dt);
        g.text(this.font, pretty(s.name), r.x() + 8, r.y() + 6, Theme.mix(Theme.TEXT_DIM, Theme.TEXT, t), false);
        Theme.toggle(g, r.x() + r.w() - 22, r.y() + 5, t);
        if (r.pair() != null) {
            // its colour sits right next to the switch; click it for the R/G/B/A sliders
            int[] sw = swatch(r);
            int color = (Integer) r.pair().get();
            boolean open = OPEN_COLORS.contains(r.pair());
            Theme.rounded(g, sw[0] - 1, sw[1] - 1, sw[2] + 2, sw[3] + 2, 4, open ? Theme.ACCENT : 0xFF2A2D36);
            Theme.rounded(g, sw[0], sw[1], sw[2], sw[3], 3, 0xFF777777);
            g.fill(sw[0] + 2, sw[1] + 2, sw[0] + sw[2] / 2, sw[1] + sw[3] / 2, 0xFF555555);
            g.fill(sw[0] + sw[2] / 2, sw[1] + sw[3] / 2, sw[0] + sw[2] - 2, sw[1] + sw[3] - 2, 0xFF555555);
            Theme.rounded(g, sw[0], sw[1], sw[2], sw[3], 3, s.get() ? color : Theme.alpha(color, 0.35f));
        }
    }

    /** {x, y, w, h} of the inline colour swatch on a switch row. */
    private static int[] swatch(Row r) {
        return new int[]{r.x() + r.w() - 44, r.y() + 5, 16, 10};
    }

    /** {x, y} of the big on/off switch in the settings header. */
    private int[] headerSwitch() {
        return new int[]{setX + setW - 30, wy + 11};
    }

    private void drawNumber(GuiGraphicsExtractor g, Row r) {
        Setting<?> s = r.setting();
        double value = ((Number) s.get()).doubleValue();
        double min = Double.isFinite(s.min) ? s.min : 0, max = Double.isFinite(s.max) ? s.max : 1;
        float frac = max > min ? (float) ((value - min) / (max - min)) : 0f;
        String shown = s.get() instanceof Integer ? String.valueOf((int) value) : String.format(Locale.ROOT, "%.2f", value);
        g.text(this.font, pretty(s.name), r.x() + 8, r.y() + 4, Theme.TEXT_DIM, false);
        g.text(this.font, shown, r.x() + r.w() - 8 - this.font.width(shown), r.y() + 4, Theme.TEXT, false);
        int[] t = track(r);
        Theme.rounded(g, t[0], t[1] - 2, t[2], 4, 2, Theme.TRACK);
        int fill = Math.round(t[2] * frac);
        if (fill > 0) Theme.rounded(g, t[0], t[1] - 2, Math.max(4, fill), 4, 2, Theme.ACCENT);
        int kx = t[0] + fill;
        Theme.rounded(g, kx - 5, t[1] - 5, 11, 11, 5, 0x50000000);
        Theme.rounded(g, kx - 4, t[1] - 4, 9, 9, 4, 0xFFFFFFFF);
    }

    private static String enumLabel(Object e) {
        String v = ((Enum<?>) e).name().replace('_', ' ').toLowerCase(Locale.ROOT);
        return Character.toUpperCase(v.charAt(0)) + v.substring(1);
    }

    /**
     * Segments {x, width} for a choice with up to 3 options, laid out right-aligned in the row - or
     * null when it has more options or they don't fit (then it's a single pill you click through).
     */
    private int[][] segments(Row r) {
        Object[] options = r.setting().type.getEnumConstants();
        if (options == null || options.length > 3) return null;
        int[][] segs = new int[options.length][];
        int total = 0;
        for (int i = 0; i < options.length; i++) {
            int w = this.font.width(enumLabel(options[i])) + 10;
            segs[i] = new int[]{0, w};
            total += w;
        }
        int labelW = this.font.width(pretty(r.setting().name)) + 16;
        if (total + labelW > r.w() - 8) return null;
        int x = r.x() + r.w() - 6 - total;
        for (int[] s : segs) {
            s[0] = x;
            x += s[1];
        }
        return segs;
    }

    private void drawEnum(GuiGraphicsExtractor g, Row r, float hover) {
        Setting<?> s = r.setting();
        g.text(this.font, pretty(s.name), r.x() + 8, r.y() + 6, Theme.TEXT_DIM, false);
        int[][] segs = segments(r);
        if (segs != null) {
            Object[] options = s.type.getEnumConstants();
            int x0 = segs[0][0], x1 = segs[segs.length - 1][0] + segs[segs.length - 1][1];
            Theme.rounded(g, x0, r.y() + 3, x1 - x0, 14, 5, 0xFF1C1E25);
            for (int i = 0; i < options.length; i++) {
                boolean sel = options[i] == s.get();
                if (sel) Theme.rounded(g, segs[i][0] + 1, r.y() + 4, segs[i][1] - 2, 12, 4, 0xFF2D3242);
                String label = enumLabel(options[i]);
                g.text(this.font, label, segs[i][0] + segs[i][1] / 2 - this.font.width(label) / 2, r.y() + 6,
                    sel ? Theme.ACCENT : Theme.TEXT_OFF, false);
            }
            return;
        }
        String value = "‹  " + enumLabel(s.get()) + "  ›";
        int vw = this.font.width(value) + 12;
        int vx = r.x() + r.w() - 8 - vw;
        Theme.rounded(g, vx, r.y() + 3, vw, 14, 5, Theme.mix(0xFF1E2128, 0xFF272B36, hover));
        g.text(this.font, value, vx + 6, r.y() + 6, Theme.ACCENT, false);
    }

    private void drawColor(GuiGraphicsExtractor g, Row r) {
        Setting<?> s = r.setting();
        g.text(this.font, pretty(s.name), r.x() + 8, r.y() + 5, Theme.TEXT_DIM, false);
        int color = (Integer) s.get();
        int sx = r.x() + r.w() - 26, sy = r.y() + 3;
        Theme.rounded(g, sx - 1, sy - 1, 20, 14, 4, 0xFF2A2D36);
        Theme.rounded(g, sx, sy, 18, 12, 3, 0xFF777777);
        g.fill(sx + 2, sy + 2, sx + 9, sy + 6, 0xFF555555);
        g.fill(sx + 9, sy + 6, sx + 16, sy + 10, 0xFF555555);
        Theme.rounded(g, sx, sy, 18, 12, 3, color);
    }

    private void drawChannel(GuiGraphicsExtractor g, Row r) {
        int color = (Integer) r.setting().get();
        int ch = r.channel();
        int value = channelValue(color, ch);
        String name = switch (ch) { case 0 -> "R"; case 1 -> "G"; case 2 -> "B"; default -> "A"; };
        int bar = switch (ch) { case 0 -> 0xFFFF6B81; case 1 -> 0xFF6BE09A; case 2 -> 0xFF6BA6FF; default -> 0xFFC8CCD6; };
        g.text(this.font, name, r.x() + 16, r.y() + 4, Theme.TEXT_OFF, false);
        int[] t = track(r);
        Theme.rounded(g, t[0], t[1] - 1, t[2], 3, 1, Theme.TRACK);
        int fill = Math.round(t[2] * value / 255f);
        if (fill > 0) Theme.rounded(g, t[0], t[1] - 1, fill, 3, 1, bar);
        Theme.rounded(g, t[0] + fill - 3, t[1] - 3, 6, 6, 3, 0xFFFFFFFF);
        String v = String.valueOf(value);
        g.text(this.font, v, r.x() + r.w() - 6 - this.font.width(v), r.y() + 4, Theme.TEXT_OFF, false);
    }

    private void drawKeybind(GuiGraphicsExtractor g, Row r) {
        g.text(this.font, "Keybind", r.x() + 8, r.y() + 5, Theme.TEXT_DIM, false);
        boolean binding = bindingFor == r.module();
        String key = binding ? "Press a key or mouse button…" : keyName(r.module().getKeyBind());
        int kw = this.font.width(key) + 12;
        int kx = r.x() + r.w() - 8 - kw;
        int bg = binding ? Theme.alpha(Theme.ACCENT, 0.18f + 0.12f * Theme.pulse(6f)) : 0xFF1E2128;
        Theme.rounded(g, kx, r.y() + 3, kw, 12, 4, bg);
        g.text(this.font, key, kx + 6, r.y() + 5, binding ? Theme.ACCENT : Theme.TEXT_DIM, false);
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

    private static String icon(Category c) {
        return switch (c.name()) {
            case "RENDER" -> "◉";
            case "MOVEMENT" -> "➤";
            case "COMBAT" -> "⚔";
            case "PLAYER" -> "☺";
            case "WORLD" -> "⛏";
            default -> "✦";
        };
    }

    private static String title(Category c) {
        String n = c.name();
        return n.charAt(0) + n.substring(1).toLowerCase(Locale.ROOT);
    }

    private String keyName(int keyCode) {
        return dev.novautils.utils.Keybinds.name(keyCode);
    }

    // ------------------------------------------------------------------ input

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
        int mx = (int) event.x(), my = (int) event.y();
        int button = event.button();

        // waiting for a keybind: middle / side / extra mouse buttons bind; left / right click cancel
        // (binding those would toggle the module on every normal click)
        if (bindingFor != null) {
            if (button >= 2) {
                bindingFor.setKeyBind(dev.novautils.utils.Keybinds.mouse(button));
                bindingFor = null;
                ModuleManager.get().markDirty();
                return true;
            }
            bindingFor = null;
        }

        // sidebar
        for (Category c : Category.values()) {
            int y = categoryY(c.ordinal());
            if (mx >= wx + 6 && mx < wx + SIDEBAR - 6 && my >= y && my < y + 16) {
                selectedCategory = c;
                search = "";
                listScroll = listScrollTarget = 0;
                return true;
            }
        }
        if (selected != null) {
            int[] hs = headerSwitch();
            if (mx >= hs[0] - 2 && mx < hs[0] + 18 && my >= hs[1] - 2 && my < hs[1] + 11) {
                selected.toggle();
                ModuleManager.get().markDirty();
                return true;
            }
        }
        int by = hudButtonY();
        if (mx >= wx + 8 && mx < wx + SIDEBAR - 8 && my >= by && my < by + 15) {
            dev.novautils.utils.PlatformCompat.openScreen(net.minecraft.client.Minecraft.getInstance(), new HudEditorScreen(this));
            return true;
        }

        for (int i = rows.size() - 1; i >= 0; i--) {
            Row r = rows.get(i);
            if (!over(r, mx, my)) continue;

            if (r.pane() == Pane.LIST) {
                if (button == 1) {
                    setExpanded(r.module(), selected != r.module());
                } else if (button == 0) {
                    r.module().toggle();
                    ModuleManager.get().markDirty();
                }
                return true;
            }

            if (button == 2) {
                if (r.type() == RowType.KEYBIND) r.module().setKeyBind(-1);
                else if (r.setting() != null) r.setting().reset();
                ModuleManager.get().markDirty();
                return true;
            }
            switch (r.type()) {
                case BOOL -> {
                    int[] sw = swatch(r);
                    if (r.pair() != null && mx >= sw[0] - 2 && mx < sw[0] + sw[2] + 2) {
                        if (!OPEN_COLORS.remove(r.pair())) OPEN_COLORS.add(r.pair());
                        return true;
                    }
                    @SuppressWarnings("unchecked")
                    Setting<Boolean> s = (Setting<Boolean>) r.setting();
                    s.set(!s.get());
                    ModuleManager.get().markDirty();
                }
                case NUMBER, CHANNEL -> {
                    slidingSetting = r.setting();
                    slidingRow = r;
                    applySlider(mx);
                }
                case ENUM -> {
                    int[][] segs = segments(r);
                    Object[] options = r.setting().type.getEnumConstants();
                    boolean picked = false;
                    if (segs != null) {
                        for (int k = 0; k < segs.length; k++) {
                            if (mx >= segs[k][0] && mx < segs[k][0] + segs[k][1]) {
                                setEnum(r.setting(), options[k]);
                                picked = true;
                            }
                        }
                    }
                    if (!picked) cycleEnum(r.setting(), button == 1 ? -1 : 1);
                }
                case KEYBIND -> bindingFor = r.module();
                case COLOR -> {
                    if (!OPEN_COLORS.remove(r.setting())) OPEN_COLORS.add(r.setting());
                }
                default -> { }
            }
            return true;
        }

        bindingFor = null;
        return super.mouseClicked(event, doubleClick);
    }

    @Override
    public boolean mouseDragged(MouseButtonEvent event, double dx, double dy) {
        if (slidingSetting != null) {
            applySlider((int) event.x());
            return true;
        }
        return super.mouseDragged(event, dx, dy);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        if (mouseY >= bodyTop && mouseY < bodyBottom) {
            if (mouseX >= listX && mouseX < listX + listW) {
                listScrollTarget = Math.max(0, listScrollTarget - (float) scrollY * 22);
                return true;
            }
            if (mouseX >= setX && mouseX < setX + setW) {
                setScrollTarget = Math.max(0, setScrollTarget - (float) scrollY * 22);
                return true;
            }
        }
        return super.mouseScrolled(mouseX, mouseY, scrollX, scrollY);
    }

    @Override
    public boolean mouseReleased(MouseButtonEvent event) {
        if (slidingSetting != null) ModuleManager.get().markDirty();
        slidingSetting = null;
        slidingRow = null;
        return super.mouseReleased(event);
    }

    @SuppressWarnings("unchecked")
    private void applySlider(int mouseX) {
        if (slidingSetting == null || slidingRow == null) return;
        int[] t = track(slidingRow);
        double frac = Math.max(0, Math.min(1, (mouseX - t[0]) / (double) t[2]));

        if (slidingRow.type() == RowType.CHANNEL) {
            Setting<Integer> color = (Setting<Integer>) slidingSetting;
            color.set(withChannel(color.get(), slidingRow.channel(), (int) Math.round(frac * 255)));
            return;
        }
        double min = Double.isFinite(slidingSetting.min) ? slidingSetting.min : 0;
        double max = Double.isFinite(slidingSetting.max) ? slidingSetting.max : 1;
        double value = min + (max - min) * frac;
        if (slidingSetting.get() instanceof Integer) ((Setting<Integer>) slidingSetting).set((int) Math.round(value));
        else ((Setting<Double>) slidingSetting).set(Math.round(value * 100.0) / 100.0);
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private void setEnum(Setting<?> setting, Object value) {
        ((Setting) setting).set(value);
        ModuleManager.get().markDirty();
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
            listScroll = listScrollTarget = 0;
            return true;
        }
        // First ESC clears the search, the second one closes the menu.
        if (event.key() == InputConstants.KEY_ESCAPE && !search.isEmpty()) {
            search = "";
            listScroll = listScrollTarget = 0;
            return true;
        }
        return super.keyPressed(event);
    }

    @Override
    public boolean charTyped(CharacterEvent event) {
        if (bindingFor == null && event.isAllowedChatCharacter()) {
            search += event.codepointAsString();
            listScroll = listScrollTarget = 0;
            return true;
        }
        return super.charTyped(event);
    }

    @Override
    public void onClose() {
        ModuleManager.get().saveConfig();
        super.onClose();
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
