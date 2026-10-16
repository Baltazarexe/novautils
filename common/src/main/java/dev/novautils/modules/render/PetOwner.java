package dev.novautils.modules.render;

import dev.novautils.gui.Theme;
import dev.novautils.modules.Category;
import dev.novautils.modules.Module;
import dev.novautils.settings.Setting;
import dev.novautils.utils.ScreenSpace;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.OwnableEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/** Shows who owns tamed wolves, cats, parrots, horses, llamas... in a small tag above them. */
public class PetOwner extends Module {
    private final Setting<Integer> range = register(Setting.builder(Integer.class, "range")
        .description("Only pets this close.").defaultValue(32).range(4, 128).build());
    private final Setting<Boolean> mine = register(Setting.builder(Boolean.class, "show-mine")
        .description("Also tag your own pets.").defaultValue(true).build());

    private final Map<UUID, String> names = new HashMap<>();

    public PetOwner() {
        super("PetOwner", "Shows the owner of tamed animals.", Category.RENDER);
    }

    private static UUID ownerId(OwnableEntity o) {
        var ref = o.getOwnerReference();
        return ref == null ? null : ref.getUUID();
    }

    /** Owner name: the player if loaded, else the tab list, else the start of the UUID. */
    private String name(UUID id) {
        String cached = names.get(id);
        if (cached != null) return cached;
        Player p = mc.level.getPlayerByUUID(id);
        String n = null;
        if (p != null) n = p.getName().getString();
        else if (mc.getConnection() != null) {
            var info = mc.getConnection().getPlayerInfo(id);
            if (info != null) n = info.getProfile().name();
        }
        if (n == null) return id.toString().substring(0, 8);
        names.put(id, n);
        return n;
    }

    @Override
    public void onTick() {
        if (mc.level != null && mc.level.getGameTime() % 200 == 0) names.clear();
    }

    @Override
    public void onRenderHud(GuiGraphicsExtractor g, DeltaTracker delta) {
        if (mc.level == null || mc.player == null || !ScreenSpace.begin()) return;
        float partial = delta.getGameTimeDeltaPartialTick(false);
        for (Entity e : mc.level.entitiesForRendering()) {
            if (!(e instanceof OwnableEntity o) || mc.player.distanceTo(e) > range.get()) continue;
            UUID id = ownerId(o);
            if (id == null || (!mine.get() && id.equals(mc.player.getUUID()))) continue;
            Vec3 top = e.getPosition(partial).add(0, e.getBbHeight() + 0.25, 0);
            double[] p = ScreenSpace.project(top);
            if (p == null) continue;
            boolean yours = id.equals(mc.player.getUUID());
            String t = name(id);
            int w = mc.font.width(t) + 12, x = (int) p[0] - w / 2, y = (int) p[1] - 12;
            Theme.rounded(g, x, y, w, 11, 4, 0xB80D0E12);
            Theme.border(g, x, y, w, 11, 0x1EFFFFFF);
            g.fill(x + 4, y + 4, x + 7, y + 7, yours ? 0xFF66E07A : 0xFFFFC94A);
            g.text(mc.font, t, x + 9, y + 2, 0xFFEDEFF5, false);
        }
    }
}
