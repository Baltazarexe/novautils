package dev.novautils.gui.screens;

import dev.novautils.gui.Theme;
import dev.novautils.gui.UiFont;
import dev.novautils.modules.ModuleManager;
import dev.novautils.modules.render.Search;
import dev.novautils.utils.PlatformCompat;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * The Search list in the menu: type to find any block or mob (modded ones too), click a result to
 * add it, see a preview (the block as a big item, the mob as a turning model), change colours and
 * remove entries. "Add typed" takes the text as is, so tags like #c:ores work too.
 */
public class SearchEditorScreen extends Screen {
    private static final int ROW_H = 20;
    /** Rows that fit between the search box and the Done button (3..8). */
    private int rows = 8;

    private final Screen parent;
    private boolean mobs;
    private EditBox query;
    private int page, listPage;
    private String message = "";
    private int messageColor = Theme.TEXT_DIM;

    /** What the preview box shows: hovered result or entry, else the last one clicked. */
    private String previewId;
    private boolean previewMob;
    private final List<Object[]> resultRows = new ArrayList<>();   // {Button, id}
    private final List<Object[]> listRows = new ArrayList<>();     // {Button, Search.Custom}
    private final Map<String, LivingEntity> previewCache = new HashMap<>();

    public SearchEditorScreen(Screen parent) {
        super(Component.literal("Search list"));
        this.parent = parent;
    }

    private Search search() {
        return ModuleManager.get().get(Search.class);
    }

    @Override
    protected void init() {
        resultRows.clear();
        listRows.clear();
        int col = Math.min(210, (this.width - 60) / 3);
        int lx = this.width / 2 - col * 3 / 2 - 10, rx = lx + col + 10, px = rx + col + 10;
        int top = 40;
        rows = Math.max(3, Math.min(8, (this.height - top - 24 - 56) / ROW_H));

        addRenderableWidget(Button.builder(Component.literal((mobs ? "" : "> ") + "Blocks"), b -> { mobs = false; page = 0; listPage = 0; query.setValue(""); rebuildWidgets(); })
            .bounds(lx, 16, col / 2 - 2, 18).build());
        addRenderableWidget(Button.builder(Component.literal((mobs ? "> " : "") + "Mobs"), b -> { mobs = true; page = 0; listPage = 0; query.setValue(""); rebuildWidgets(); })
            .bounds(lx + col / 2 + 2, 16, col / 2 - 2, 18).build());

        String old = query != null ? query.getValue() : "";
        query = new EditBox(this.font, lx, top, col - 66, 18, Component.literal("Search"));
        query.setMaxLength(80);
        query.setValue(old);
        query.setHint(Component.literal(mobs ? "creeper, ender..." : "diamond, iridium, #c:ores..."));
        query.setResponder(s -> {
            page = 0;
            rebuildWidgets();
        });
        addRenderableWidget(query);
        setInitialFocus(query);
        addRenderableWidget(Button.builder(Component.literal("Add typed"), b -> add(query.getValue().trim().toLowerCase(Locale.ROOT)))
            .bounds(lx + col - 62, top, 62, 18).build());

        // results
        List<String> found = matches(query.getValue().trim().toLowerCase(Locale.ROOT));
        int pages = Math.max(1, (found.size() + rows - 1) / rows);
        page = Math.min(page, pages - 1);
        int y = top + 24;
        for (int i = page * rows; i < Math.min(found.size(), (page + 1) * rows); i++) {
            String id = found.get(i);
            Button b = Button.builder(Component.literal("     " + shortName(id)), btn -> add(id)).bounds(lx, y, col, ROW_H - 2).build();
            addRenderableWidget(b);
            resultRows.add(new Object[]{b, id});
            y += ROW_H;
        }
        int navY = top + 24 + rows * ROW_H + 2;
        addRenderableWidget(Button.builder(Component.literal("<"), b -> { page = Math.max(0, page - 1); rebuildWidgets(); }).bounds(lx, navY, 20, 18).build());
        addRenderableWidget(Button.builder(Component.literal(">"), b -> { page++; rebuildWidgets(); }).bounds(lx + col - 20, navY, 20, 18).build());
        addRenderableWidget(Button.builder(Component.literal(mobs ? "Add the mob I look at" : "Add the block I look at"), b -> {
            String id = mobs ? search().lookedAtEntity() : search().lookedAtBlock();
            if (id == null) say(mobs ? "No mob under the crosshair" : "No block under the crosshair", 0xFFFF7A7A);
            else add(id);
        }).bounds(lx + 24, navY, col - 48, 18).build());

        // your list (this tab only)
        List<Search.Custom> mine = new ArrayList<>();
        for (Search.Custom c : search().customsView()) if (c.entity == mobs) mine.add(c);
        int lpages = Math.max(1, (mine.size() + rows - 1) / rows);
        listPage = Math.min(listPage, lpages - 1);
        y = top + 24;
        for (int i = listPage * rows; i < Math.min(mine.size(), (listPage + 1) * rows); i++) {
            Search.Custom c = mine.get(i);
            Button b = Button.builder(Component.literal("     " + shortName(c.id)), btn -> { previewId = c.id; previewMob = c.entity; })
                .bounds(rx, y, col - 46, ROW_H - 2).build();
            addRenderableWidget(b);
            listRows.add(new Object[]{b, c});
            addRenderableWidget(Button.builder(Component.literal("■"), btn -> { search().cycleColor(c); rebuildWidgets(); })
                .bounds(rx + col - 44, y, 20, ROW_H - 2).build());
            addRenderableWidget(Button.builder(Component.literal("x"), btn -> {
                search().removeCustom(c.id);
                say("Removed " + shortName(c.id), Theme.TEXT_DIM);
            }).bounds(rx + col - 22, y, 22, ROW_H - 2).build());
            y += ROW_H;
        }
        addRenderableWidget(Button.builder(Component.literal("<"), b -> { listPage = Math.max(0, listPage - 1); rebuildWidgets(); }).bounds(rx, navY, 20, 18).build());
        addRenderableWidget(Button.builder(Component.literal(">"), b -> { listPage++; rebuildWidgets(); }).bounds(rx + col - 20, navY, 20, 18).build());

        addRenderableWidget(Button.builder(Component.literal("Done"), b -> onClose())
            .bounds(this.width / 2 - 60, this.height - 26, 120, 20).build());
    }

    /** Registry ids (blocks or mobs) whose id or name contains the text; everything when it's empty. */
    private List<String> matches(String q) {
        List<String> out = new ArrayList<>();
        if (q.startsWith("#")) return out;
        if (mobs) {
            for (EntityType<?> t : BuiltInRegistries.ENTITY_TYPE) {
                String id = BuiltInRegistries.ENTITY_TYPE.getKey(t).toString();
                if (q.isEmpty() || id.contains(q) || t.getDescription().getString().toLowerCase(Locale.ROOT).contains(q)) out.add(id);
            }
        } else {
            for (Block b : BuiltInRegistries.BLOCK) {
                String id = BuiltInRegistries.BLOCK.getKey(b).toString();
                if (id.endsWith(":air")) continue;
                if (q.isEmpty() || id.contains(q) || b.getName().getString().toLowerCase(Locale.ROOT).contains(q)) out.add(id);
            }
        }
        // exact and vanilla-ish first, then alphabetical
        out.sort((a, b) -> {
            boolean ea = a.endsWith(":" + q), eb = b.endsWith(":" + q);
            if (ea != eb) return ea ? -1 : 1;
            return a.compareTo(b);
        });
        return out;
    }

    private void add(String id) {
        if (id == null || id.isEmpty()) return;
        String err = mobs ? search().addEntity(id, null) : search().addCustom(id, null);
        previewId = id.contains(":") || id.startsWith("#") ? id : "minecraft:" + id;
        previewMob = mobs;
        if (err != null) say(err, 0xFFFF7A7A);
        else say("Added " + shortName(id) + " - Search is on", 0xFF66DD77);
    }

    private void say(String text, int color) {
        message = text;
        messageColor = color;
        rebuildWidgets();
    }

    private static String shortName(String id) {
        return id.startsWith("minecraft:") ? id.substring(10) : id;
    }

    // ------------------------------------------------------------------ icons and preview

    private static ItemStack blockIcon(String id) {
        var rl = net.minecraft.resources.Identifier.tryParse(id);
        if (rl == null) return ItemStack.EMPTY;
        return BuiltInRegistries.BLOCK.getOptional(rl).map(b -> new ItemStack(b)).orElse(ItemStack.EMPTY);
    }

    private static ItemStack eggIcon(String id) {
        var rl = net.minecraft.resources.Identifier.tryParse(id);
        if (rl == null) return ItemStack.EMPTY;
        return BuiltInRegistries.ENTITY_TYPE.getOptional(rl).flatMap(t -> net.minecraft.world.item.SpawnEggItem.byId(t)).map(h -> new ItemStack(h)).orElse(ItemStack.EMPTY);
    }

    private LivingEntity previewEntity(String id) {
        if (previewCache.containsKey(id)) return previewCache.get(id);
        LivingEntity living = null;
        try {
            var rl = net.minecraft.resources.Identifier.tryParse(id);
            if (rl != null && this.minecraft.level != null) {
                var type = BuiltInRegistries.ENTITY_TYPE.getOptional(rl).orElse(null);
                if (type != null) {
                    var e = type.create(this.minecraft.level, net.minecraft.world.entity.EntitySpawnReason.LOAD);
                    // rendering reads the id; give it one nothing else uses
                    if (e != null) e.setId(-100000 - previewCache.size());
                    if (e instanceof LivingEntity l) living = l;
                }
            }
        } catch (Throwable ignored) {
            // some modded mobs can't be made outside the world
        }
        previewCache.put(id, living);
        return living;
    }

    private void icon(GuiGraphicsExtractor g, String id, boolean mob, int x, int y) {
        ItemStack s = mob ? eggIcon(id) : blockIcon(id);
        if (!s.isEmpty()) g.item(s, x, y);
        else UiFont.text(g, id.startsWith("#") ? "#" : "?", x + 5, y + 4, Theme.TEXT_DIM, false);
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor g, int mouseX, int mouseY, float delta) {
        super.extractRenderState(g, mouseX, mouseY, delta);
        int col = Math.min(210, (this.width - 60) / 3);
        int lx = this.width / 2 - col * 3 / 2 - 10, rx = lx + col + 10, px = rx + col + 10;
        int top = 40;

        UiFont.text(g, "Search list", rx, 16, Theme.TEXT, true);
        if (!message.isEmpty()) UiFont.text(g, this.font.plainSubstrByWidth(message, col * 2), rx, 27, messageColor, false);
        else UiFont.text(g, mobs ? "Mobs: box + tracer through walls" : "Blocks: highlighted in loaded chunks", rx, 27, Theme.TEXT_DIM, false);
        UiFont.text(g, "Your list", rx, top + 5, Theme.accent(), true);

        String hoverId = null;
        boolean hoverMob = mobs;
        for (Object[] r : resultRows) {
            Button b = (Button) r[0];
            icon(g, (String) r[1], mobs, b.getX() + 2, b.getY() + 1);
            if (b.isHovered()) hoverId = (String) r[1];
        }
        for (Object[] r : listRows) {
            Button b = (Button) r[0];
            Search.Custom c = (Search.Custom) r[1];
            icon(g, c.id, c.entity, b.getX() + 2, b.getY() + 1);
            // colour swatch on the colour button
            g.fill(b.getX() + b.getWidth() + 7, b.getY() + 5, b.getX() + b.getWidth() + 15, b.getY() + 13, c.color | 0xFF000000);
            if (b.isHovered()) {
                hoverId = c.id;
                hoverMob = c.entity;
            }
        }
        if (hoverId == null) {
            hoverId = previewId;
            hoverMob = previewMob;
        }

        // preview box
        int pw = col, ph = rows * ROW_H + 20;
        Theme.rounded(g, px, top, pw, ph, 6, 0xC0101216);
        UiFont.text(g, "Preview", px + 8, top + 6, Theme.accent(), true);
        if (hoverId != null) {
            String name = shortName(hoverId);
            UiFont.text(g, this.font.plainSubstrByWidth(name, pw - 16), px + 8, top + ph - 14, Theme.TEXT, false);
            int cx = px + pw / 2, cy = top + ph / 2;
            if (hoverMob) {
                LivingEntity e = hoverId.startsWith("#") ? null : previewEntity(hoverId);
                if (e != null) {
                    net.minecraft.client.gui.screens.inventory.InventoryScreen.extractEntityInInventoryFollowsMouse(g,
                        px + 10, top + 20, px + pw - 10, top + ph - 20, 30, 0.0625f, mouseX, mouseY, e);
                } else {
                    icon(g, hoverId, true, cx - 8, cy - 8);
                }
            } else {
                ItemStack s = blockIcon(hoverId);
                if (!s.isEmpty()) {
                    g.pose().pushMatrix();
                    g.pose().translate(cx - 24, cy - 24);
                    g.pose().scale(3f, 3f);
                    g.item(s, 0, 0);
                    g.pose().popMatrix();
                } else {
                    UiFont.text(g, hoverId.startsWith("#") ? "tag: every block in it" : "no item for this block", px + 8, cy, Theme.TEXT_DIM, false);
                }
            }
        } else {
            UiFont.text(g, "hover a block or mob", px + 8, top + ph / 2, Theme.TEXT_DIM, false);
        }

    }

    @Override
    public void onClose() {
        PlatformCompat.openScreen(this.minecraft, parent);
    }
}
