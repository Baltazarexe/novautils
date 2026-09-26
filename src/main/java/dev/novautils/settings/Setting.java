package dev.novautils.settings;

import java.util.function.Supplier;

/**
 * A single configurable value belonging to a module. Supports booleans, integers,
 * doubles, enums, ARGB colors (as int) and keybinds (as a GLFW key code, -1 = unbound).
 */
public class Setting<T> {
    public final String name;
    public final String description;
    public final T defaultValue;
    public final double min;
    public final double max;
    public final Class<T> type;

    private T value;
    private Supplier<Boolean> visiblePredicate = () -> true;

    private Setting(Builder<T> builder) {
        this.name = builder.name;
        this.description = builder.description;
        this.defaultValue = builder.defaultValue;
        this.value = builder.defaultValue;
        this.min = builder.min;
        this.max = builder.max;
        this.type = builder.type;
        if (builder.visible != null) this.visiblePredicate = builder.visible;
    }

    public T get() {
        return value;
    }

    public void set(T value) {
        this.value = value;
    }

    public void reset() {
        this.value = defaultValue;
    }

    public boolean isVisible() {
        return visiblePredicate.get();
    }

    @SuppressWarnings("unchecked")
    public static <T> Builder<T> builder(Class<T> type, String name) {
        return new Builder<>(type, name);
    }

    public static class Builder<T> {
        private final Class<T> type;
        private final String name;
        private String description = "";
        private T defaultValue;
        private double min = Double.NEGATIVE_INFINITY;
        private double max = Double.POSITIVE_INFINITY;
        private Supplier<Boolean> visible;

        public Builder(Class<T> type, String name) {
            this.type = type;
            this.name = name;
        }

        public Builder<T> description(String description) {
            this.description = description;
            return this;
        }

        public Builder<T> defaultValue(T value) {
            this.defaultValue = value;
            return this;
        }

        public Builder<T> range(double min, double max) {
            this.min = min;
            this.max = max;
            return this;
        }

        public Builder<T> visible(Supplier<Boolean> predicate) {
            this.visible = predicate;
            return this;
        }

        public Setting<T> build() {
            return new Setting<>(this);
        }
    }
}
