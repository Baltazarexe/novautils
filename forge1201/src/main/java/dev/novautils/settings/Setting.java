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
    /** Integer settings flagged as colours are edited with an ARGB picker instead of a slider. */
    public final boolean color;

    private T value;
    private Supplier<Boolean> visiblePredicate = () -> true;
    private java.util.function.Consumer<T> onChange;

    private Setting(Builder<T> builder) {
        this.name = builder.name;
        this.description = builder.description;
        this.defaultValue = builder.defaultValue;
        this.value = builder.defaultValue;
        this.min = builder.min;
        this.max = builder.max;
        this.type = builder.type;
        this.color = builder.color;
        if (builder.visible != null) this.visiblePredicate = builder.visible;
        this.onChange = builder.onChange;
    }

    public T get() {
        return value;
    }

    public void set(T value) {
        this.value = value;
        if (onChange != null) onChange.accept(value);
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
        private boolean color;
        private java.util.function.Consumer<T> onChange;

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

        /** Marks an Integer setting as an ARGB colour. */
        public Builder<T> asColor() {
            this.color = true;
            return this;
        }

        public Builder<T> visible(Supplier<Boolean> predicate) {
            this.visible = predicate;
            return this;
        }

        /** Called after every set() (menu clicks, commands, config loading). */
        public Builder<T> onChange(java.util.function.Consumer<T> listener) {
            this.onChange = listener;
            return this;
        }

        public Setting<T> build() {
            return new Setting<>(this);
        }
    }
}
