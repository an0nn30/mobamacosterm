package com.mobamacos.ui;

import com.mobamacos.config.ConfigManager;
import com.mobamacos.model.AppConfig;
import com.mobamacos.model.ServerEntry;
import com.mobamacos.model.ServerFolder;
import com.mobamacos.ssh.TunnelManager;
import com.mobamacos.theme.ThemeManager;
import com.mobamacos.ui.dialogs.ResumeSessionsDialog;
import com.mobamacos.ui.files.FileTransferPanel;

import javax.swing.*;
import java.awt.*;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.util.ArrayList;
import java.util.List;

public class MainWindow extends JFrame {

    private final ConfigManager     configManager;
    private final SidePanel         sidePanel;       // left  — Files / Tools / Macros
    private final SessionTabPane    sessionTabPane;  // centre — terminals
    private final SessionsPanel     sessionsPanel;   // right  — server tree
    private final FileTransferPanel filePanel;

    public MainWindow(ConfigManager configManager, ThemeManager themeManager) {
        super("MobaMacOS Terminal");
        this.configManager = configManager;

        setDefaultCloseOperation(DO_NOTHING_ON_CLOSE);
        setSize(1400, 900);
        setLocationRelativeTo(null);

        // Build sub-components
        sessionTabPane = new SessionTabPane(configManager);
        filePanel      = new FileTransferPanel();
        sessionTabPane.addSessionListener(filePanel);

        sidePanel    = new SidePanel(filePanel);
        sessionsPanel = new SessionsPanel(configManager, sessionTabPane);

        // Menu bar
        TunnelManager tunnelManager = new TunnelManager();
        MenuBarBuilder menuBuilder = new MenuBarBuilder(this, configManager, themeManager, tunnelManager);
        menuBuilder.setSessionsPanel(sessionsPanel);
        setJMenuBar(menuBuilder.build());

        // Layout: [left panel] | [terminals] | [sessions]
        JSplitPane rightSplit = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT, sessionTabPane, sessionsPanel);
        rightSplit.setResizeWeight(1.0);          // terminals take all extra space
        rightSplit.setDividerSize(4);
        rightSplit.setContinuousLayout(true);
        rightSplit.setDividerLocation(1400 - 240 - 260 - 8); // right panel starts ~240 from edge

        JSplitPane mainSplit = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT, sidePanel, rightSplit);
        mainSplit.setDividerLocation(260);
        mainSplit.setDividerSize(4);
        mainSplit.setContinuousLayout(true);

        add(mainSplit, BorderLayout.CENTER);

        addWindowListener(new WindowAdapter() {
            @Override public void windowClosing(WindowEvent e) { saveSessionsAndExit(); }
        });
    }

    /** Must be called after the window is visible. */
    public void handleStartup() {
        AppConfig cfg = configManager.getConfig();
        List<String> lastKeys = cfg.getLastSessionKeys();

        if (!lastKeys.isEmpty() && !cfg.isDontAskResume()) {
            ResumeSessionsDialog dlg = new ResumeSessionsDialog(this, lastKeys);
            dlg.setVisible(true);

            if (dlg.isDontAskAgain()) {
                cfg.setDontAskResume(true);
                configManager.saveConfig();
            }

            if (dlg.isConfirmed()) {
                List<String> selected = dlg.getSelectedKeys();
                resumeSessions(selected);
                if (!selected.isEmpty()) return;
            }
        } else if (!lastKeys.isEmpty() && cfg.isDontAskResume()) {
            resumeSessions(lastKeys);
            if (!lastKeys.isEmpty()) return;
        }

        sessionTabPane.openLocalTerminal();
    }

    // -----------------------------------------------------------------------

    private void resumeSessions(List<String> keys) {
        for (String key : keys) {
            ServerEntry entry = findServerByKey(key);
            if (entry != null) sessionTabPane.openSession(entry);
        }
        if (sessionTabPane.getLiveSessionKeys().isEmpty() && tabbedPaneIsEmpty()) {
            sessionTabPane.openLocalTerminal();
        }
    }

    private boolean tabbedPaneIsEmpty() {
        for (Component c : sessionTabPane.getComponents()) {
            if (c instanceof JTabbedPane tp) return tp.getTabCount() == 0;
        }
        return true;
    }

    private ServerEntry findServerByKey(String key) {
        for (ServerFolder folder : configManager.getConfig().getFolders()) {
            for (ServerEntry server : folder.getServers()) {
                String k = server.getUsername() + "@" + server.getHost() + ":" + server.getPort();
                if (k.equals(key)) return server;
            }
        }
        return null;
    }

    private void saveSessionsAndExit() {
        List<String> keys = new ArrayList<>(sessionTabPane.getLiveSessionKeys());
        configManager.getConfig().setLastSessionKeys(keys);
        configManager.saveConfig();
        filePanel.dispose();
        System.exit(0);
    }

    // -----------------------------------------------------------------------

    public SidePanel      getSidePanel()      { return sidePanel; }
    public SessionsPanel  getSessionsPanel()  { return sessionsPanel; }
    public SessionTabPane getSessionTabPane() { return sessionTabPane; }
}
