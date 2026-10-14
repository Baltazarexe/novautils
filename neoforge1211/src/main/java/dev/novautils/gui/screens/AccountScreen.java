package dev.novautils.gui.screens;

import dev.novautils.gui.Theme;
import dev.novautils.utils.Accounts;
import dev.novautils.utils.PlatformCompat;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * Account manager: the launcher account plus any offline nicks you added. Click one to play as it
 * (no restart), "x" removes a nick. Offline nicks only work on servers in offline mode.
 */
public class AccountScreen extends Screen {
    private static final int ROW_H = 22, MAX_ROWS = 7;

    private final Screen parent;
    private EditBox nameBox;
    private String message = "";
    private int messageColor = Theme.TEXT_DIM;

    public AccountScreen(Screen parent) {
        super(Component.literal("Accounts"));
        this.parent = parent;
    }

    @Override
    protected void init() {
        Accounts.launcherUser();
        int w = 220, x = this.width / 2 - w / 2;
        int y = 44;

        List<String> nicks = new ArrayList<>(Accounts.offline());
        // the launcher account first, then the offline nicks
        String launcherName = Accounts.launcherUser().getName();
        addRenderableWidget(Button.builder(label(launcherName + "  (launcher)", Accounts.usingLauncher()), b -> {
            Accounts.useLauncher();
            say("Playing as " + launcherName, 0xFF66DD77);
        }).bounds(x, y, w, 20).build());
        y += ROW_H;
        for (int i = 0; i < Math.min(MAX_ROWS, nicks.size()); i++) {
            String nick = nicks.get(i);
            boolean active = !Accounts.usingLauncher() && Accounts.current().equals(nick);
            addRenderableWidget(Button.builder(label(nick + "  (offline)", active), b -> {
                Accounts.useOffline(nick);
                say("Playing as " + nick + " (offline servers only)", 0xFF66DD77);
            }).bounds(x, y, w - 24, 20).build());
            addRenderableWidget(Button.builder(Component.literal("x"), b -> {
                if (!Accounts.usingLauncher() && Accounts.current().equals(nick)) Accounts.useLauncher();
                Accounts.removeOffline(nick);
                rebuildWidgets();
            }).bounds(x + w - 20, y, 20, 20).build());
            y += ROW_H;
        }

        int by = this.height - 58;
        nameBox = new EditBox(this.font, x, by, w - 84, 20, Component.literal("Nick"));
        nameBox.setMaxLength(16);
        nameBox.setHint(Component.literal("offline nick..."));
        addRenderableWidget(nameBox);
        addRenderableWidget(Button.builder(Component.literal("Add offline"), b -> {
            String n = nameBox.getValue().trim();
            if (!Accounts.validName(n)) {
                say("3-16 letters, numbers or _", 0xFFFF7A7A);
            } else if (!Accounts.addOffline(n)) {
                say(n + " is already on the list", 0xFFFFD84A);
            } else {
                rebuildWidgets();
                say("Added " + n + " - click it to play as it", 0xFF66DD77);
            }
        }).bounds(x + w - 80, by, 80, 20).build());
        addRenderableWidget(Button.builder(Component.literal("Done"), b -> onClose())
            .bounds(x, this.height - 30, w, 20).build());
    }

    private static Component label(String text, boolean active) {
        return Component.literal((active ? "> " : "") + text);
    }

    private void say(String text, int color) {
        message = text;
        messageColor = color;
        rebuildWidgets();
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float delta) {
        renderBackground(g, mouseX, mouseY, delta);
        super.render(g, mouseX, mouseY, delta);
        String title = "Accounts";
        g.drawString(this.font, title, this.width / 2 - this.font.width(title) / 2, 14, Theme.TEXT, true);
        String now = "Playing as " + Accounts.current();
        g.drawString(this.font, now, this.width / 2 - this.font.width(now) / 2, 28, Theme.ACCENT, true);
        if (!message.isEmpty()) {
            g.drawString(this.font, message, this.width / 2 - this.font.width(message) / 2, this.height - 74, messageColor, true);
        }
    }

    @Override
    public void onClose() {
        PlatformCompat.openScreen(this.minecraft, parent);
    }
}
