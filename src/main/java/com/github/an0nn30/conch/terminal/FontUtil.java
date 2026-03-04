package com.github.an0nn30.conch.terminal;

import java.awt.*;
import java.util.Set;

/**
 * Helpers for font availability — cached so getAvailableFontFamilyNames()
 * is only called once per JVM lifetime.
 */
public class FontUtil {

    private static final Set<String> AVAILABLE = Set.of(
            GraphicsEnvironment.getLocalGraphicsEnvironment().getAvailableFontFamilyNames());

    private static final String[] MONOSPACE_CANDIDATES = {
            "Menlo", "Monaco", "Courier New", "Courier"
    };

    /** True if the named font family is actually installed on this machine. */
    public static boolean isAvailable(String name) {
        return name != null && AVAILABLE.contains(name);
    }

    /** Returns the best available monospace font for the given size. */
    public static Font bestMonospace(int size) {
        for (String name : MONOSPACE_CANDIDATES) {
            if (AVAILABLE.contains(name)) {
                return new Font(name, Font.PLAIN, size);
            }
        }
        return new Font(Font.MONOSPACED, Font.PLAIN, size);
    }

    /** All known monospace fonts that happen to be installed, plus any extra name given. */
    public static String[] availableMonoFonts(String extra) {
        String[] known = {
                "Menlo", "Monaco", "SF Mono", "Courier New", "Courier",
                "Andale Mono", "JetBrains Mono", "Fira Code", "Source Code Pro",
                "Hack", "Inconsolata", "Roboto Mono", "Ubuntu Mono",
                "DejaVu Sans Mono", "Liberation Mono"
        };
        java.util.List<String> result = new java.util.ArrayList<>();
        for (String f : known) {
            if (AVAILABLE.contains(f)) result.add(f);
        }
        if (extra != null && !extra.isBlank() && !result.contains(extra)) {
            result.add(0, extra);   // user-configured font goes first even if unknown
        }
        if (result.isEmpty()) result.add(Font.MONOSPACED);
        return result.toArray(new String[0]);
    }

    private FontUtil() {}
}
