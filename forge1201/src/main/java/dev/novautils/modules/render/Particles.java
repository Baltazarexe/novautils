package dev.novautils.modules.render;

import dev.novautils.modules.Category;
import dev.novautils.modules.Module;
import dev.novautils.modules.ModuleManager;
import dev.novautils.settings.Setting;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.registries.BuiltInRegistries;

import java.util.concurrent.ThreadLocalRandom;

/**
 * Pick which particles are made and how many. Turning off smoke, drips or potion swirls (or
 * cutting everything to 30%) helps a lot in big modded bases. ParticleEngineMixin asks here.
 */
public class Particles extends Module {
    private final Setting<Integer> density = register(Setting.builder(Integer.class, "density")
        .description("Percent of particles kept (all kinds).").defaultValue(100).range(0, 100).build());
    private final Setting<Boolean> explosions = toggle("explosions", "Explosion clouds.");
    private final Setting<Boolean> smoke = toggle("smoke", "Smoke, campfire smoke, ash.");
    private final Setting<Boolean> potions = toggle("potion-swirls", "Effect swirls around mobs and players.");
    private final Setting<Boolean> weather = toggle("drips-rain", "Rain splashes, drips, falling particles, bubbles.");
    private final Setting<Boolean> combat = toggle("hit-effects", "Crits, sweeps, damage hearts.");
    private final Setting<Boolean> fire = toggle("flames", "Torch, furnace and lava flames.");
    private final Setting<Boolean> portal = toggle("portal", "Nether portal and ender particles.");
    private final Setting<Boolean> blocks = toggle("block-break", "Pieces flying off blocks you break or walk on.");
    private final Setting<Boolean> totem = toggle("totem", "Totem of undying burst.");
    private final Setting<Boolean> other = toggle("other", "Everything else, modded particles included.");

    public Particles() {
        super("Particles", "Choose which particles show and how many (good for FPS).", Category.RENDER);
    }

    private Setting<Boolean> toggle(String name, String description) {
        return register(Setting.builder(Boolean.class, name).description(description).defaultValue(true).build());
    }

    private static Particles active() {
        if (!dev.novautils.NovaUtils.modulesReady) return null;
        Particles p = ModuleManager.get().get(Particles.class);
        return p.isEnabled() ? p : null;
    }

    private boolean keepByDensity() {
        int d = density.get();
        return d >= 100 || (d > 0 && ThreadLocalRandom.current().nextInt(100) < d);
    }

    /** ParticleEngineMixin.createParticle: false = don't make this one. */
    public static boolean allow(ParticleOptions options) {
        Particles p = active();
        if (p == null) return true;
        var key = BuiltInRegistries.PARTICLE_TYPE.getKey(options.getType());
        String id = key == null ? "" : key.getPath();
        boolean on;
        if (id.contains("explosion")) on = p.explosions.get();
        else if (id.contains("smoke") || id.contains("campfire") || id.contains("ash")) on = p.smoke.get();
        else if (id.contains("effect") || id.equals("witch")) on = p.potions.get();
        else if (id.contains("rain") || id.contains("drip") || id.contains("falling_") || id.contains("landing_") || id.contains("splash") || id.contains("bubble")) on = p.weather.get();
        else if (id.contains("crit") || id.contains("sweep") || id.contains("damage_indicator") || id.contains("enchanted_hit")) on = p.combat.get();
        else if (id.contains("flame") || id.equals("lava")) on = p.fire.get();
        else if (id.contains("portal")) on = p.portal.get();
        else if (id.equals("block") || id.contains("dust_pillar") || id.equals("block_crumble")) on = p.blocks.get();
        else if (id.contains("totem")) on = p.totem.get();
        else on = p.other.get();
        return on && p.keepByDensity();
    }

    /** ParticleEngineMixin.add for block-break pieces, which skip createParticle. */
    public static boolean allowTerrain() {
        Particles p = active();
        return p == null || (p.blocks.get() && p.keepByDensity());
    }
}
