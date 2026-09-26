package dev.novautils.gui.screens;

import dev.novautils.modules.Category;
import dev.novautils.modules.Module;
import dev.novautils.modules.ModuleManager;
import dev.novautils.settings.Setting;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.util.CommonColors;

/**
 * The in-game config menu (replaces having to open a browser page). Open it with the keybind
 * registered in {@link dev.novautils.NovaUtilsClient} (Right Shift by default, rebindable in
 * Controls like any other key).
 */
public class ClickGuiScreen extends Screen {
    private static final int SIDEBAR_WIDTH = 130;
    private static final int PANEL_WIDTH = 220;
    private static final int ROW_HEIGHT = 20;
    private static final int TOP = 30;

    private int page = 0;
    private Category selectedCategory = Category.RENDER;
    private Module selectedModule = null;
    private Setting<?> awaitingKeyBindFor = null;
    private Module awaitingModuleKeyBind = null;

    public ClickGuiScreen() {
        super(Component.literal("NovaUtils"));
    }

    @Override
    protected void init() {
        rebuild();
    }

    private void rebuild() {
        clearWidgets();

        int tabW = Math.max(40, Math.min(70, (this.width - 16) / Category.values().length - 4));
        int tabX = 8;
        for (Category category : Category.values()) {
            Button tab = Button.builder(Component.literal(category.name()), b -> {
                selectedCategory = category;
                selectedModule = null;
                page = 0;
                rebuild();
            }).pos(tabX, 6).size(tabW, 18).build();
            addRenderableWidget(tab);
            tabX += tabW + 4;
        }

        java.util.List<Module> inCategory = new java.util.ArrayList<>();
        for (Module m : ModuleManager.get().getModules()) {
            if (m.category == selectedCategory) inCategory.add(m);
        }
        int rowsPerPage = Math.max(3, (this.height - TOP - 30) / ROW_HEIGHT);
        int pageCount = Math.max(1, (inCategory.size() + rowsPerPage - 1) / rowsPerPage);
        page = Math.min(page, pageCount - 1);

        if (pageCount > 1) {
            int navY = this.height - 22;
            addRenderableWidget(Button.builder(Component.literal("<"), b -> {
                page = Math.max(0, page - 1);
                rebuild();
            }).pos(8, navY).size(24, 18).build());
            addRenderableWidget(Button.builder(Component.literal((page + 1) + "/" + pageCount), b -> {}).pos(36, navY).size(SIDEBAR_WIDTH - 64, 18).build());
            addRenderableWidget(Button.builder(Component.literal(">"), b -> {
                page = Math.min(pageCount - 1, page + 1);
                rebuild();
            }).pos(8 + SIDEBAR_WIDTH - 24, navY).size(24, 18).build());
        }

        int y = TOP;
        int from = page * rowsPerPage;
        int to = Math.min(inCategory.size(), from + rowsPerPage);
        for (Module module : inCategory.subList(from, to)) {
            String state = module.isEnabled() ? "ON" : "OFF";
            Button toggle = Button.builder(Component.literal(module.name + " [" + state + "]"), b -> {
                module.toggle();
                ModuleManager.get().saveConfig();
                rebuild();
            }).pos(8, y).size(SIDEBAR_WIDTH, ROW_HEIGHT - 2).build();
            addRenderableWidget(toggle);

            final Module rowModule = module;
            Button select = Button.builder(Component.literal(module == selectedModule ? "▲" : "▼"), b -> {
                selectedModule = (selectedModule == rowModule) ? null : rowModule;
                rebuild();
            }).pos(8 + SIDEBAR_WIDTH + 4, y).size(18, ROW_HEIGHT - 2).build();
            addRenderableWidget(select);

            y += ROW_HEIGHT;
        }

        if (selectedModule != null) {
            buildSettingsPanel(selectedModule);
        }
    }

    private void buildSettingsPanel(Module module) {
        int panelX = 8 + SIDEBAR_WIDTH + 30;
        int y = TOP;

        Button bindButton = Button.builder(
            Component.literal(awaitingModuleKeyBind == module ? "Press any key..." : "Keybind: " + keyName(module.getKeyBind())),
            b -> {
                awaitingModuleKeyBind = module;
                rebuild();
            }
        ).pos(panelX, y).size(PANEL_WIDTH, ROW_HEIGHT - 2).build();
        addRenderableWidget(bindButton);
        y += ROW_HEIGHT + 4;

        for (Setting<?> setting : module.getSettings()) {
            if (!setting.isVisible()) continue;
            y = buildSettingRow(setting, panelX, y);
        }
    }

    private int buildSettingRow(Setting<?> setting, int x, int y) {
        Object value = setting.get();

        if (value instanceof Boolean bool) {
            Button b = Button.builder(Component.literal(setting.name + ": " + (bool ? "On" : "Off")), btn -> {
                @SuppressWarnings("unchecked")
                Setting<Boolean> s = (Setting<Boolean>) setting;
                s.set(!bool);
                ModuleManager.get().saveConfig();
                rebuild();
            }).pos(x, y).size(PANEL_WIDTH, ROW_HEIGHT - 2).build();
            addRenderableWidget(b);
            return y + ROW_HEIGHT;
        }

        if (value instanceof Integer intValue) {
            addStepperRow(setting, x, y, setting.name + ": " + intValue,
                () -> stepInt(setting, -1), () -> stepInt(setting, 1));
            return y + ROW_HEIGHT;
        }

        if (value instanceof Double doubleValue) {
            double step = (setting.max - setting.min) / 20.0;
            if (!Double.isFinite(step) || step <= 0) step = 0.05;
            double finalStep = step;
            addStepperRow(setting, x, y, setting.name + ": " + String.format("%.2f", doubleValue),
                () -> stepDouble(setting, -finalStep), () -> stepDouble(setting, finalStep));
            return y + ROW_HEIGHT;
        }

        if (value instanceof Enum<?> e) {
            Button b = Button.builder(Component.literal(setting.name + ": " + e.name()), btn -> {
                cycleEnum(setting);
                ModuleManager.get().saveConfig();
                rebuild();
            }).pos(x, y).size(PANEL_WIDTH, ROW_HEIGHT - 2).build();
            addRenderableWidget(b);
            return y + ROW_HEIGHT;
        }

        return y;
    }

    private void addStepperRow(Setting<?> setting, int x, int y, String label, Runnable onMinus, Runnable onPlus) {
        Button minus = Button.builder(Component.literal("-"), b -> {
            onMinus.run();
            ModuleManager.get().saveConfig();
            rebuild();
        }).pos(x, y).size(20, ROW_HEIGHT - 2).build();
        addRenderableWidget(minus);

        Button label_ = Button.builder(Component.literal(label), b -> {}).pos(x + 22, y).size(PANEL_WIDTH - 44, ROW_HEIGHT - 2).build();
        label_.active = false;
        addRenderableWidget(label_);

        Button plus = Button.builder(Component.literal("+"), b -> {
            onPlus.run();
            ModuleManager.get().saveConfig();
            rebuild();
        }).pos(x + PANEL_WIDTH - 20, y).size(20, ROW_HEIGHT - 2).build();
        addRenderableWidget(plus);
    }

    @SuppressWarnings("unchecked")
    private void stepInt(Setting<?> setting, int amount) {
        Setting<Integer> s = (Setting<Integer>) setting;
        int next = s.get() + amount;
        next = (int) Math.max(setting.min, Math.min(setting.max, next));
        s.set(next);
    }

    @SuppressWarnings("unchecked")
    private void stepDouble(Setting<?> setting, double amount) {
        Setting<Double> s = (Setting<Double>) setting;
        double next = s.get() + amount;
        next = Math.max(setting.min, Math.min(setting.max, next));
        s.set(Math.round(next * 100.0) / 100.0);
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private void cycleEnum(Setting<?> setting) {
        Object[] constants = setting.type.getEnumConstants();
        if (constants == null || constants.length == 0) return;
        Object current = setting.get();
        int index = 0;
        for (int i = 0; i < constants.length; i++) {
            if (constants[i] == current) {
                index = i;
                break;
            }
        }
        Object next = constants[(index + 1) % constants.length];
        ((Setting) setting).set(next);
    }

    private String keyName(int keyCode) {
        if (keyCode == -1) return "none";
        return com.mojang.blaze3d.platform.InputConstants.Type.KEYBOARD.getOrCreate(keyCode).getDisplayName().getString();
    }

    @Override
    public boolean keyPressed(net.minecraft.client.input.KeyEvent event) {
        if (awaitingModuleKeyBind != null) {
            awaitingModuleKeyBind.setKeyBind(event.key());
            awaitingModuleKeyBind = null;
            ModuleManager.get().saveConfig();
            rebuild();
            return true;
        }
        return super.keyPressed(event);
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float delta) {

        graphics.fill(0, 0, this.width, 28, 0xC0000000);
        graphics.fill(0, 28, SIDEBAR_WIDTH + 30, this.height, 0x80000000);
        if (selectedModule != null) {
            graphics.fill(SIDEBAR_WIDTH + 30, 28, SIDEBAR_WIDTH + 60 + PANEL_WIDTH, this.height, 0x60000000);
        }

        super.extractRenderState(graphics, mouseX, mouseY, delta);

        if (selectedModule != null) {
            graphics.text(this.font, selectedModule.description, SIDEBAR_WIDTH + 30, this.height - 16, CommonColors.LIGHT_GRAY);
        }
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
