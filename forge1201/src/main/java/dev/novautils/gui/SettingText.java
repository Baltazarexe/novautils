package dev.novautils.gui;

import dev.novautils.settings.Setting;

import java.util.Locale;

/** Readable bits of a setting shared by both menus: its default (and range) for the tooltips. */
public final class SettingText {
    private SettingText() {}

    /** 4.5, 0.15, 128: no trailing zeros. */
    public static String number(double v, boolean integer) {
        if (integer) return String.valueOf((int) Math.round(v));
        String t = String.format(Locale.ROOT, "%.2f", v);
        while (t.endsWith("0")) t = t.substring(0, t.length() - 1);
        return t.endsWith(".") ? t.substring(0, t.length() - 1) : t;
    }

    /** "default 4.5 · 1–6", "default on", "default Solid", "default #FF5A5A". */
    public static String defaultText(Setting<?> s) {
        Object d = s.defaultValue;
        if (d == null) return "";
        if (s.color) return String.format(Locale.ROOT, "default #%06X", ((Integer) d) & 0xFFFFFF);
        if (d instanceof Boolean b) return "default " + (b ? "on" : "off");
        if (d instanceof Enum<?> e) {
            String v = e.name().replace('_', ' ').toLowerCase(Locale.ROOT);
            return "default " + Character.toUpperCase(v.charAt(0)) + v.substring(1);
        }
        if (d instanceof Number n) {
            boolean integer = d instanceof Integer;
            String text = "default " + number(n.doubleValue(), integer);
            if (Double.isFinite(s.min) && Double.isFinite(s.max)) text += "  ·  " + number(s.min, integer) + "–" + number(s.max, integer);
            return text;
        }
        return "";
    }

    /** Whether a setting is changed from its default (the small dot in the menus). */
    public static boolean changed(Setting<?> s) {
        return !java.util.Objects.equals(s.get(), s.defaultValue);
    }
}
