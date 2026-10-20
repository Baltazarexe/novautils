package dev.novautils.gui;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.FontDescription;
import net.minecraft.network.chat.Style;
import net.minecraft.resources.Identifier;

/**
 * The menus' smooth TTF font (Poppins, assets/novautils/font/poppins.json) instead of the blocky
 * pixel font. Glyphs Poppins lacks (the arrows and search icon) fall back to the default font via
 * the "reference" provider in the JSON, so nothing renders blank.
 */
public final class UiFont {
    private UiFont() {}

    private static final Style STYLE = Style.EMPTY.withFont(
        new FontDescription.Resource(Identifier.fromNamespaceAndPath("novautils", "poppins")));

    public static Component of(String s) {
        return Component.literal(s).withStyle(STYLE);
    }

    public static int width(String s) {
        return Minecraft.getInstance().font.width(of(s));
    }

    public static void text(GuiGraphicsExtractor g, String s, int x, int y, int color) {
        g.text(Minecraft.getInstance().font, of(s), x, y, color, false);
    }

    public static void text(GuiGraphicsExtractor g, String s, int x, int y, int color, boolean shadow) {
        g.text(Minecraft.getInstance().font, of(s), x, y, color, shadow);
    }

    /** Longest prefix of {@code s} that fits in {@code px} (same idea as Font.plainSubstrByWidth). */
    public static String plainSubstrByWidth(String s, int px) {
        if (width(s) <= px) return s;
        int lo = 0, hi = s.length();
        while (lo < hi) {
            int mid = (lo + hi + 1) / 2;
            if (width(s.substring(0, mid)) <= px) lo = mid; else hi = mid - 1;
        }
        return s.substring(0, lo);
    }

    /** Word-wraps {@code s} into lines no wider than {@code px}. */
    public static java.util.List<String> split(String s, int px) {
        java.util.List<String> lines = new java.util.ArrayList<>();
        StringBuilder cur = new StringBuilder();
        for (String word : s.split(" ")) {
            String test = cur.length() == 0 ? word : cur + " " + word;
            if (cur.length() > 0 && width(test) > px) {
                lines.add(cur.toString());
                cur = new StringBuilder(word);
            } else {
                cur = new StringBuilder(test);
            }
        }
        if (cur.length() > 0) lines.add(cur.toString());
        return lines;
    }
}
