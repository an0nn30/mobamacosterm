package com.github.an0nn30.conch.ui;

import javax.swing.*;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.net.URL;
import java.util.HashMap;
import java.util.Map;

/**
 * Central cache for Paper icon theme resources bundled in
 * src/main/resources/icons/.
 *
 * Falls back to a blank 16×16 icon rather than null if a resource is missing,
 * so callers never need to null-check.
 */
public final class AppIcons {

    // -----------------------------------------------------------------------
    // Icon names — add new ones here as constants
    // -----------------------------------------------------------------------

    public static final String FOLDER         = "folder";
    public static final String FOLDER_OPEN   = "folder-open";
    public static final String SERVER        = "server";
    public static final String NETWORK_SERVER = "network-server";
    public static final String FILE          = "file";
    public static final String TERMINAL      = "terminal";
    public static final String TAB_SESSIONS  = "tab-sessions";
    public static final String TAB_FILES     = "tab-files";
    public static final String TAB_TOOLS     = "tab-tools";
    public static final String TAB_MACROS    = "tab-macros";
    public static final String GO_UP           = "go-up";
    public static final String GO_HOME         = "go-home";
    public static final String REFRESH         = "refresh";
    public static final String TRANSFER_DOWN   = "go-down";   // download to local
    public static final String TRANSFER_UP     = "go-up";     // upload to remote (same arrow, opposite context)

    // -----------------------------------------------------------------------
    // Cache
    // -----------------------------------------------------------------------

    private static final Map<String, ImageIcon> CACHE = new HashMap<>();

    private AppIcons() {}

    /**
     * Returns a 16×16 {@link ImageIcon} for the given name.
     * The name maps to {@code /icons/<name>.png} in the classpath.
     */
    public static ImageIcon get(String name) {
        return CACHE.computeIfAbsent(name, AppIcons::load);
    }

    /**
     * Returns the icon scaled to {@code size × size} pixels.
     * Useful for HiDPI tab buttons or larger toolbar icons.
     */
    public static ImageIcon get(String name, int size) {
        String key = name + "@" + size;
        return CACHE.computeIfAbsent(key, k -> scale(get(name), size));
    }

    // -----------------------------------------------------------------------
    // Internals
    // -----------------------------------------------------------------------

    private static ImageIcon load(String name) {
        URL url = AppIcons.class.getResource("/icons/" + name + ".png");
        if (url != null) return new ImageIcon(url);
        // Fallback: transparent 16×16
        return new ImageIcon(blank(16));
    }

    private static ImageIcon scale(ImageIcon src, int size) {
        Image scaled = src.getImage()
                .getScaledInstance(size, size, Image.SCALE_SMOOTH);
        return new ImageIcon(scaled);
    }

    private static Image blank(int size) {
        return new BufferedImage(size, size, BufferedImage.TYPE_INT_ARGB);
    }
}
