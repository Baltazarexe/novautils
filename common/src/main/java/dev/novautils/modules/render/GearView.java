package dev.novautils.modules.render;

import dev.novautils.modules.Category;
import dev.novautils.modules.Module;
import dev.novautils.settings.Setting;
import dev.novautils.utils.Friends;
import dev.novautils.utils.ScreenSpace;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * What other players are wearing, above their heads: helmet, chestplate, leggings, boots and the
 * items in both hands as icons (with the durability bar), the enchantments above each one, and
 * optionally a line per item with the full names. Shrinks with distance like a name tag.
 */
public class GearView extends Module {
    public enum Names { SHORT, FULL, OFF }

    private final Setting<Boolean> mobs = register(Setting.builder(Boolean.class, "mobs")
        .description("Also mobs wearing armour or holding something.").defaultValue(false).build());

    private final Setting<Names> names = register(Setting.builder(Names.class, "enchantments")
        .description("SHORT: P4, S5 above each icon. FULL: a line per item (Sharp V, KB II). OFF: none.")
        .defaultValue(Names.SHORT).build());

    private final Setting<Boolean> durability = register(Setting.builder(Boolean.class, "durability-percent")
        .description("Durability in % under each piece (the bar is always there).").defaultValue(false).build());

    private final Setting<Boolean> offhand = register(Setting.builder(Boolean.class, "offhand")
        .description("Include the item in the other hand.").defaultValue(true).build());

    private final Setting<Double> scale = register(Setting.builder(Double.class, "scale")
        .description("Size up close.").defaultValue(1.0).range(0.5, 2.0).build());

    private final Setting<Integer> range = register(Setting.builder(Integer.class, "range")
        .description("Only this close.").defaultValue(48).range(8, 128).build());

    private static final Map<String, String[]> ABBR = Map.ofEntries(
        Map.entry("protection", new String[]{"P", "Prot"}), Map.entry("fire_protection", new String[]{"FP", "Fire Prot"}),
        Map.entry("feather_falling", new String[]{"FF", "Feather"}), Map.entry("blast_protection", new String[]{"BP", "Blast Prot"}),
        Map.entry("projectile_protection", new String[]{"PP", "Proj Prot"}), Map.entry("respiration", new String[]{"R", "Resp"}),
        Map.entry("aqua_affinity", new String[]{"AA", "Aqua"}), Map.entry("thorns", new String[]{"T", "Thorns"}),
        Map.entry("depth_strider", new String[]{"DS", "Depth"}), Map.entry("frost_walker", new String[]{"FW", "Frost"}),
        Map.entry("soul_speed", new String[]{"SS", "Soul Speed"}), Map.entry("swift_sneak", new String[]{"SwS", "Swift Sneak"}),
        Map.entry("sharpness", new String[]{"S", "Sharp"}), Map.entry("smite", new String[]{"Sm", "Smite"}),
        Map.entry("bane_of_arthropods", new String[]{"BoA", "Bane"}), Map.entry("knockback", new String[]{"KB", "KB"}),
        Map.entry("fire_aspect", new String[]{"FA", "Fire Aspect"}), Map.entry("looting", new String[]{"L", "Looting"}),
        Map.entry("sweeping_edge", new String[]{"SE", "Sweeping"}), Map.entry("efficiency", new String[]{"E", "Eff"}),
        Map.entry("silk_touch", new String[]{"ST", "Silk"}), Map.entry("unbreaking", new String[]{"U", "Unb"}),
        Map.entry("fortune", new String[]{"F", "Fortune"}), Map.entry("power", new String[]{"Pw", "Power"}),
        Map.entry("punch", new String[]{"Pu", "Punch"}), Map.entry("flame", new String[]{"Fl", "Flame"}),
        Map.entry("infinity", new String[]{"Inf", "Infinity"}), Map.entry("mending", new String[]{"M", "Mending"}),
        Map.entry("vanishing_curse", new String[]{"VC", "Vanishing"}), Map.entry("binding_curse", new String[]{"BC", "Binding"}),
        Map.entry("loyalty", new String[]{"Loy", "Loyalty"}), Map.entry("riptide", new String[]{"Rip", "Riptide"}),
        Map.entry("channeling", new String[]{"Ch", "Channeling"}), Map.entry("impaling", new String[]{"Imp", "Impaling"}),
        Map.entry("multishot", new String[]{"MS", "Multishot"}), Map.entry("quick_charge", new String[]{"QC", "Quick Charge"}),
        Map.entry("piercing", new String[]{"Pi", "Piercing"}), Map.entry("density", new String[]{"D", "Density"}),
        Map.entry("breach", new String[]{"Br", "Breach"}), Map.entry("wind_burst", new String[]{"WB", "Wind Burst"}),
        Map.entry("lunge", new String[]{"Lu", "Lunge"}));

    /** Levels worth highlighting (max-ish): pink instead of lavender. */
    private static final Map<String, Integer> STRONG = Map.of("protection", 4, "sharpness", 5, "power", 5,
        "knockback", 2, "fire_aspect", 2, "punch", 2, "efficiency", 5, "mending", 1, "unbreaking", 3);

    private static final String[] ROMAN = {"", "I", "II", "III", "IV", "V", "VI", "VII", "VIII", "IX", "X"};

    private record Ench(String id, int level) {}

    public GearView() {
        super("GearView", "Armour, held items and enchantments above other players' heads.", Category.RENDER);
    }

    /** Enchantments on a stack as (path, level). */
    private static List<Ench> enchants(ItemStack stack) {
        List<Ench> out = new ArrayList<>();
        for (var e : stack.getEnchantments().entrySet()) {
            String name = e.getKey().getRegisteredName();
            out.add(new Ench(name.substring(name.indexOf(':') + 1), e.getIntValue()));
        }
        return out;
    }

    private static String roman(int level) {
        return level >= 1 && level < ROMAN.length ? ROMAN[level] : String.valueOf(level);
    }

    private static String label(Ench e, boolean full) {
        String[] a = ABBR.get(e.id());
        if (full) return (a != null ? a[1] : e.id().replace('_', ' ')) + " " + roman(e.level());
        return (a != null ? a[0] : e.id().substring(0, Math.min(3, e.id().length()))) + e.level();
    }

    private static int color(Ench e) {
        Integer s = STRONG.get(e.id());
        return s != null && e.level() >= s ? 0xFFFF66FF : 0xFFC8C8FF;
    }

    private boolean wanted(Entity e) {
        if (e == mc.player || !(e instanceof LivingEntity l) || !l.isAlive() || e.isInvisible() || e instanceof ArmorStand) return false;
        if (mc.player.distanceTo(e) > range.get()) return false;
        return e instanceof Player || mobs.get();
    }

    private List<ItemStack> gear(LivingEntity e) {
        List<ItemStack> out = new ArrayList<>();
        for (EquipmentSlot slot : new EquipmentSlot[]{EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET, EquipmentSlot.MAINHAND, EquipmentSlot.OFFHAND}) {
            if (slot == EquipmentSlot.OFFHAND && !offhand.get()) continue;
            ItemStack s = e.getItemBySlot(slot);
            if (!s.isEmpty()) out.add(s);
        }
        return out;
    }

    @Override
    public void onRenderHud(GuiGraphicsExtractor g, DeltaTracker delta) {
        if (mc.level == null || mc.player == null || !ScreenSpace.begin()) return;
        float partial = delta.getGameTimeDeltaPartialTick(false);
        for (Entity e : mc.level.entitiesForRendering()) {
            if (!wanted(e)) continue;
            LivingEntity living = (LivingEntity) e;
            List<ItemStack> items = gear(living);
            if (items.isEmpty()) continue;

            // just above the name tag
            Vec3 anchor = e.getPosition(partial).add(0, e.getBbHeight() + 0.5, 0);
            double[] p = ScreenSpace.project(anchor);
            if (p == null || p[0] < -100 || p[1] < -100 || p[0] > g.guiWidth() + 100 || p[1] > g.guiHeight() + 100) continue;

            float dist = mc.player.distanceTo(e);
            float s = (float) (scale.get() * Math.max(0.45, Math.min(1.0, 7.0 / Math.max(1.0, dist))));
            Names mode = names.get();
            // each column is as wide as its icon, its enchantment tags or its percentage
            int[] cols = new int[items.size()];
            int width = 0;
            for (int i = 0; i < items.size(); i++) {
                int w = 16;
                ItemStack st = items.get(i);
                if (mode == Names.SHORT) for (Ench en : enchants(st)) w = Math.max(w, mc.font.width(label(en, false)));
                if (durability.get() && st.isDamageableItem()) w = Math.max(w, mc.font.width("100%"));
                cols[i] = w + 3;
                width += cols[i];
            }
            width -= 3;

            g.pose().pushMatrix();
            g.pose().translate((float) p[0], (float) p[1] - 14 * s);
            g.pose().scale(s, s);
            int left = -width / 2, top = -18;

            // full names: one line per enchanted item, stacked above the icons
            int lineY = top - 3;
            if (mode == Names.FULL) {
                for (int i = items.size() - 1; i >= 0; i--) {
                    List<Ench> en = enchants(items.get(i));
                    if (en.isEmpty()) continue;
                    StringBuilder sb = new StringBuilder(items.get(i).getHoverName().getString()).append(": ");
                    int c = 0xFFC8C8FF;
                    for (int k = 0; k < en.size(); k++) {
                        if (k > 0) sb.append(", ");
                        sb.append(label(en.get(k), true));
                        if (color(en.get(k)) != 0xFFC8C8FF) c = color(en.get(k));
                    }
                    String t = sb.toString();
                    int tw = mc.font.width(t);
                    lineY -= 10;
                    g.fill(-tw / 2 - 2, lineY - 1, tw / 2 + 2, lineY + 9, 0x78000000);
                    g.text(mc.font, t, -tw / 2, lineY, c, true);
                }
            }

            int cx = left;
            for (int i = 0; i < items.size(); i++) {
                ItemStack stack = items.get(i);
                int x = cx + (cols[i] - 3 - 16) / 2;
                cx += cols[i];
                g.item(stack, x, top);
                g.itemDecorations(mc.font, stack, x, top);

                if (mode == Names.SHORT) {
                    List<Ench> en = enchants(stack);
                    for (int k = 0; k < Math.min(4, en.size()); k++) {
                        String t = label(en.get(k), false);
                        g.text(mc.font, t, x + 8 - mc.font.width(t) / 2, top - 9 - k * 8, color(en.get(k)), true);
                    }
                }
                if (durability.get() && stack.isDamageableItem()) {
                    int pct = Math.round(100f * (stack.getMaxDamage() - stack.getDamageValue()) / stack.getMaxDamage());
                    String t = pct + "%";
                    int c = pct > 60 ? 0xFF66E07A : pct > 25 ? 0xFFFFD050 : 0xFFFF5A5A;
                    g.text(mc.font, t, x + 8 - mc.font.width(t) / 2, top + 18, c, true);
                }
            }
            if (e instanceof Player pl && Friends.isFriend(pl)) g.fill(left - 2, top + 17, left + width + 2, top + 18, 0xFF55E6FF);
            g.pose().popMatrix();
        }
    }
}
