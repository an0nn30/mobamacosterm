package com.github.an0nn30.conch;

import com.formdev.flatlaf.FlatIntelliJLaf;
import com.github.an0nn30.conch.config.ConfigManager;
import com.github.an0nn30.conch.theme.ThemeManager;
import com.github.an0nn30.conch.ui.MainWindow;

import javax.swing.*;
import java.awt.*;

public class Main {
    public static void main(String[] args) {
        // macOS-specific tweaks
        System.setProperty("com.apple.mrj.application.apple.menu.about.name", "Conch");
        System.setProperty("apple.awt.application.name", "Conch");

        // Set macOS dock icon
        try {
            java.net.URL iconUrl = Main.class.getResource("/conch.png");
            if (iconUrl != null && Taskbar.isTaskbarSupported()) {
                Taskbar.getTaskbar().setIconImage(new ImageIcon(iconUrl).getImage());
            }
        } catch (Exception ignored) {}

        SwingUtilities.invokeLater(() -> {
            try {
                ConfigManager configManager = new ConfigManager();
                ThemeManager  themeManager  = new ThemeManager(configManager);
                themeManager.applyTheme();

                // Tab appearance tweaks (after L&F is applied)
                applyTabStyle();

                MainWindow window = new MainWindow(configManager, themeManager);
                window.setVisible(true);
                window.handleStartup();   // must be after setVisible

            } catch (Exception e) {
                e.printStackTrace();
                JOptionPane.showMessageDialog(null,
                        "Fatal startup error:\n" + e.getMessage(),
                        "Conch", JOptionPane.ERROR_MESSAGE);
            }
        });
    }

    private static void applyTabStyle() {
        // Make tabs look closer to MobaXterm — flat, slightly taller, no painted border on content
        UIManager.put("TabbedPane.tabHeight", 30);
        UIManager.put("TabbedPane.tabInsets", new Insets(2, 10, 2, 4));
        UIManager.put("TabbedPane.selectedBackground",
                UIManager.getColor("Panel.background"));
        UIManager.put("TabbedPane.showTabSeparators", true);
        UIManager.put("TabbedPane.tabSeparatorsFullHeight", false);
    }
}
