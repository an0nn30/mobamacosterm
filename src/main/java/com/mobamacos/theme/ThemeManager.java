package com.mobamacos.theme;

import com.formdev.flatlaf.*;
import com.mobamacos.config.ConfigManager;

import javax.swing.*;

public class ThemeManager {
    public static final String DARK     = "dark";
    public static final String LIGHT    = "light";
    public static final String INTELLIJ = "intellij";
    public static final String DARCULA  = "darcula";

    private final ConfigManager configManager;

    public ThemeManager(ConfigManager configManager) {
        this.configManager = configManager;
    }

    public void applyTheme() {
        String theme = configManager.getConfig().getTheme();
        try {
            switch (theme) {
                case LIGHT    -> FlatLightLaf.setup();
                case INTELLIJ -> FlatIntelliJLaf.setup();
                case DARCULA  -> FlatDarculaLaf.setup();
                default       -> FlatDarkLaf.setup();
            }
        } catch (Exception e) {
            try {
                UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());
            } catch (Exception ex) {
                ex.printStackTrace();
            }
        }
    }

    public void changeTheme(String theme) {
        configManager.getConfig().setTheme(theme);
        configManager.saveConfig();
        applyTheme();
        for (java.awt.Window w : java.awt.Window.getWindows()) {
            SwingUtilities.updateComponentTreeUI(w);
        }
    }
}
