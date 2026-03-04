package com.github.an0nn30.conch.ui;

import com.github.an0nn30.conch.config.ConfigManager;
import com.github.an0nn30.conch.ssh.TunnelManager;
import com.github.an0nn30.conch.theme.ThemeManager;
import com.github.an0nn30.conch.ui.dialogs.NewConnectionDialog;
import com.github.an0nn30.conch.ui.dialogs.PreferencesDialog;
import com.github.an0nn30.conch.ui.dialogs.TunnelManagerDialog;

import javax.swing.*;
import java.awt.*;
import java.awt.event.KeyEvent;

public class MenuBarBuilder {

    private final Window        parent;
    private final ConfigManager configManager;
    private final ThemeManager  themeManager;
    private final TunnelManager tunnelManager;
    private       SessionsPanel sessionsPanel;   // set after construction

    public MenuBarBuilder(Window parent, ConfigManager configManager,
                          ThemeManager themeManager, TunnelManager tunnelManager) {
        this.parent        = parent;
        this.configManager = configManager;
        this.themeManager  = themeManager;
        this.tunnelManager = tunnelManager;
    }

    public void setSessionsPanel(SessionsPanel p) { this.sessionsPanel = p; }

    public JMenuBar build() {
        JMenuBar bar = new JMenuBar();
        bar.add(fileMenu());
        bar.add(sessionsMenu());
        bar.add(toolsMenu());
        bar.add(viewMenu());
        bar.add(helpMenu());
        return bar;
    }

    // -----------------------------------------------------------------------

    private JMenu fileMenu() {
        JMenu m = new JMenu("File");
        m.setMnemonic(KeyEvent.VK_F);

        JMenuItem newConn = item("New Connection…", KeyEvent.VK_N,
                KeyStroke.getKeyStroke(KeyEvent.VK_N, Toolkit.getDefaultToolkit().getMenuShortcutKeyMaskEx()));
        newConn.addActionListener(e -> showNewConnectionDialog(null));
        m.add(newConn);

        JMenuItem newFolder = item("New Folder…", KeyEvent.VK_F, null);
        newFolder.addActionListener(e -> promptNewFolder());
        m.add(newFolder);

        m.addSeparator();

        JMenuItem exit = item("Quit Conch", KeyEvent.VK_Q,
                KeyStroke.getKeyStroke(KeyEvent.VK_Q, Toolkit.getDefaultToolkit().getMenuShortcutKeyMaskEx()));
        exit.addActionListener(e -> System.exit(0));
        m.add(exit);

        return m;
    }

    private JMenu sessionsMenu() {
        JMenu m = new JMenu("Sessions");

        JMenuItem newSsh = item("New SSH Session…", 0,
                KeyStroke.getKeyStroke(KeyEvent.VK_T, Toolkit.getDefaultToolkit().getMenuShortcutKeyMaskEx()));
        newSsh.addActionListener(e -> showNewConnectionDialog(null));
        m.add(newSsh);

        return m;
    }

    private JMenu toolsMenu() {
        JMenu m = new JMenu("Tools");

        JMenuItem tunnels = item("SSH Tunnels\u2026", 0,
                KeyStroke.getKeyStroke(KeyEvent.VK_T,
                        Toolkit.getDefaultToolkit().getMenuShortcutKeyMaskEx()
                                | java.awt.event.InputEvent.SHIFT_DOWN_MASK));
        tunnels.addActionListener(e ->
                new TunnelManagerDialog(parent, configManager, tunnelManager).setVisible(true));
        m.add(tunnels);

        return m;
    }

    private JMenu viewMenu() {
        JMenu m = new JMenu("View");

        JMenuItem prefs = item("Preferences…", 0,
                KeyStroke.getKeyStroke(KeyEvent.VK_COMMA,
                        Toolkit.getDefaultToolkit().getMenuShortcutKeyMaskEx()));
        prefs.addActionListener(e -> showPreferences());
        m.add(prefs);

        return m;
    }

    private void showPreferences() {
        PreferencesDialog dlg = new PreferencesDialog(parent, configManager, themeManager);
        dlg.setVisible(true);
    }

    private JMenu helpMenu() {
        JMenu m = new JMenu("Help");
        JMenuItem about = item("About Conch", 0, null);
        about.addActionListener(e ->
                JOptionPane.showMessageDialog(parent,
                        "<html><b>Conch</b><br>Version 0.2<br><br>" +
                        "A cross-platform SSH client<br>" +
                        "built with JediTerm + SSHJ + FlatLaf.</html>",
                        "About", JOptionPane.INFORMATION_MESSAGE));
        m.add(about);
        return m;
    }

    // -----------------------------------------------------------------------

    private void showNewConnectionDialog(com.github.an0nn30.conch.model.ServerFolder folder) {
        NewConnectionDialog dlg = new NewConnectionDialog(parent, configManager, folder);
        if (sessionsPanel != null) {
            dlg.addSavedListener(sessionsPanel::refresh);
        }
        // Wire connect callback through the main window's tab pane
        if (parent instanceof MainWindow mw) {
            dlg.setConnectCallback(mw.getSessionTabPane()::openSession);
        }
        dlg.setVisible(true);
    }

    private void promptNewFolder() {
        String name = JOptionPane.showInputDialog(parent, "Folder name:", "New Folder",
                JOptionPane.PLAIN_MESSAGE);
        if (name != null && !name.isBlank()) {
            configManager.getConfig().getFolders().add(
                    new com.github.an0nn30.conch.model.ServerFolder(name.strip()));
            configManager.saveConfig();
            if (sessionsPanel != null) sessionsPanel.refresh();
        }
    }

    private static JMenuItem item(String text, int mnemonic, KeyStroke accel) {
        JMenuItem mi = new JMenuItem(text);
        if (mnemonic != 0) mi.setMnemonic(mnemonic);
        if (accel != null) mi.setAccelerator(accel);
        return mi;
    }
}
