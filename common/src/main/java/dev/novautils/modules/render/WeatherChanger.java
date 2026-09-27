package dev.novautils.modules.render;

import dev.novautils.modules.Category;
import dev.novautils.modules.Module;
import dev.novautils.settings.Setting;

/** Forces the weather you see (client side only). */
public class WeatherChanger extends Module {
    public enum Weather { CLEAR, RAIN, THUNDER }

    private final Setting<Weather> weather = register(Setting.builder(Weather.class, "weather")
        .description("Weather to display.")
        .defaultValue(Weather.CLEAR).build());

    public WeatherChanger() {
        super("WeatherChanger", "Changes the weather you see.", Category.RENDER);
    }

    @Override
    public void onTick() {
        if (mc.level == null) return;
        Weather w = weather.get();
        mc.level.setRainLevel(w == Weather.CLEAR ? 0f : 1f);
        mc.level.setThunderLevel(w == Weather.THUNDER ? 1f : 0f);
    }
}
