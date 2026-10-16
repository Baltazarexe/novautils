package dev.novautils.modules.render;

import dev.novautils.modules.Category;
import dev.novautils.modules.Module;
import dev.novautils.modules.ModuleManager;
import dev.novautils.settings.Setting;
import net.minecraft.tags.FluidTags;

/**
 * Fog and atmosphere: push the distance fog further out or remove it, tint it (the horizon and
 * the sky near it take the colour too) and see clearly under water and in lava.
 */
public class SkyFog extends Module {
    public enum Fog { NORMAL, FAR, NONE }

    private final Setting<Fog> fog = register(Setting.builder(Fog.class, "fog")
        .description("NORMAL, FAR (pushed out) or NONE.").defaultValue(Fog.FAR).build());
    private final Setting<Double> distance = register(Setting.builder(Double.class, "fog-distance")
        .description("How much further the fog starts (FAR).").defaultValue(2.0).range(1.0, 8.0)
        .visible(() -> fog.get() == Fog.FAR).build());
    private final Setting<Boolean> tint = register(Setting.builder(Boolean.class, "fog-color")
        .description("Use your own fog / horizon colour.").defaultValue(false).build());
    private final Setting<Integer> color = register(Setting.builder(Integer.class, "color")
        .description("Fog colour.").defaultValue(0xFFB48CFF).asColor().visible(tint::get).build());
    private final Setting<Boolean> clearWater = register(Setting.builder(Boolean.class, "clear-water")
        .description("See far under water.").defaultValue(true).build());
    private final Setting<Boolean> clearLava = register(Setting.builder(Boolean.class, "clear-lava")
        .description("See through lava.").defaultValue(false).build());

    public SkyFog() {
        super("SkyFog", "Push back or remove fog, tint it, clear water and lava.", Category.RENDER);
    }

    public static SkyFog active() {
        if (!dev.novautils.NovaUtils.modulesReady) return null;
        SkyFog s = ModuleManager.get().get(SkyFog.class);
        return s.isEnabled() ? s : null;
    }

    /** Multiplier for the distance fog start / end; infinite = none. */
    public float terrainScale() {
        return switch (fog.get()) {
            case NORMAL -> 1f;
            case FAR -> distance.get().floatValue();
            case NONE -> Float.POSITIVE_INFINITY;
        };
    }

    /** Multiplier for the fog you get inside water / lava. */
    public float fluidScale() {
        if (mc.player == null) return 1f;
        if (clearWater.get() && mc.player.isEyeInFluid(FluidTags.WATER)) return 12f;
        if (clearLava.get() && mc.player.isEyeInFluid(FluidTags.LAVA)) return 40f;
        return 1f;
    }

    /** ARGB fog colour, or 0 to keep the game's. */
    public int fogColor() {
        return tint.get() ? color.get() | 0xFF000000 : 0;
    }
}
