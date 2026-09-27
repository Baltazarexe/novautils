package dev.novautils.modules.render;

import dev.novautils.modules.Category;
import dev.novautils.modules.Module;
import dev.novautils.settings.Setting;

/** Forces the time of day you see (client side only). 0 = sunrise, 6000 = noon, 12000 = sunset, 18000 = midnight. */
public class TimeChanger extends Module {
    private final Setting<Integer> time = register(Setting.builder(Integer.class, "time")
        .description("Time of day in ticks (0-24000).")
        .defaultValue(6000).range(0, 24000).build());

    public TimeChanger() {
        super("TimeChanger", "Changes the time of day you see.", Category.RENDER);
    }

    @Override
    public void onTick() {
        if (mc.level != null) mc.level.setTimeFromServer(time.get());
    }
}
