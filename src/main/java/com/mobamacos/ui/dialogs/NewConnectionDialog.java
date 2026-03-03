package com.mobamacos.ui.dialogs;

import com.mobamacos.config.ConfigManager;
import com.mobamacos.model.ServerEntry;
import com.mobamacos.model.ServerFolder;

import javax.swing.*;
import java.awt.*;
import java.util.ArrayList;
import java.util.List;

public class NewConnectionDialog extends JDialog {

    private final ConfigManager configManager;
    private final List<Runnable> savedListeners = new ArrayList<>();

    private JTextField    nameField;
    private JTextField    hostField;
    private JSpinner      portSpinner;
    private JTextField    userField;
    private JPasswordField passField;
    private JTextField    keyField;
    private JComboBox<ServerFolder> folderCombo;

    public NewConnectionDialog(Window parent, ConfigManager configManager) {
        this(parent, configManager, null);
    }

    public NewConnectionDialog(Window parent, ConfigManager configManager,
                               ServerFolder preselectedFolder) {
        super(parent, "New SSH Connection", ModalityType.APPLICATION_MODAL);
        this.configManager = configManager;
        initUI(preselectedFolder);
    }

    public void addSavedListener(Runnable r) { savedListeners.add(r); }

    // -----------------------------------------------------------------------

    private void initUI(ServerFolder preselectedFolder) {
        setSize(520, 440);
        setLocationRelativeTo(getParent());
        setResizable(false);

        JPanel content = new JPanel(new GridBagLayout());
        content.setBorder(BorderFactory.createEmptyBorder(20, 24, 12, 24));

        GridBagConstraints lc = new GridBagConstraints();
        lc.anchor = GridBagConstraints.WEST;
        lc.insets = new Insets(6, 0, 6, 12);
        lc.gridx  = 0;

        GridBagConstraints fc = new GridBagConstraints();
        fc.fill    = GridBagConstraints.HORIZONTAL;
        fc.weightx = 1.0;
        fc.insets  = new Insets(6, 0, 6, 0);
        fc.gridx   = 1;

        int row = 0;

        lc.gridy = row; fc.gridy = row++;
        content.add(label("Session Name:"), lc);
        nameField = new JTextField(22);
        content.add(nameField, fc);

        lc.gridy = row; fc.gridy = row++;
        content.add(label("Host / IP:"), lc);
        hostField = new JTextField(22);
        content.add(hostField, fc);

        lc.gridy = row; fc.gridy = row++;
        content.add(label("Port:"), lc);
        portSpinner = new JSpinner(new SpinnerNumberModel(22, 1, 65535, 1));
        portSpinner.setPreferredSize(new Dimension(80, portSpinner.getPreferredSize().height));
        JPanel portWrap = new JPanel(new FlowLayout(FlowLayout.LEFT, 0, 0));
        portWrap.add(portSpinner);
        content.add(portWrap, fc);

        lc.gridy = row; fc.gridy = row++;
        content.add(label("Username:"), lc);
        userField = new JTextField(System.getProperty("user.name"), 22);
        content.add(userField, fc);

        lc.gridy = row; fc.gridy = row++;
        content.add(label("Password:"), lc);
        passField = new JPasswordField(22);
        content.add(passField, fc);

        lc.gridy = row; fc.gridy = row++;
        content.add(label("Private Key:"), lc);
        JPanel keyPanel = new JPanel(new BorderLayout(6, 0));
        keyField = new JTextField();
        JButton browse = new JButton("Browse…");
        browse.addActionListener(e -> browseForKey());
        keyPanel.add(keyField, BorderLayout.CENTER);
        keyPanel.add(browse, BorderLayout.EAST);
        content.add(keyPanel, fc);

        lc.gridy = row; fc.gridy = row;
        content.add(label("Folder:"), lc);
        folderCombo = new JComboBox<>();
        for (ServerFolder f : configManager.getConfig().getFolders()) {
            folderCombo.addItem(f);
        }
        if (preselectedFolder != null) folderCombo.setSelectedItem(preselectedFolder);
        content.add(folderCombo, fc);

        // Buttons
        JPanel buttons = new JPanel(new FlowLayout(FlowLayout.RIGHT, 8, 8));
        JButton cancel  = new JButton("Cancel");
        JButton save    = new JButton("Save");
        JButton saveConnect = new JButton("Save & Connect");
        saveConnect.setDefaultCapable(true);

        cancel.addActionListener(e -> dispose());
        save.addActionListener(e -> doSave(false));
        saveConnect.addActionListener(e -> doSave(true));

        buttons.add(cancel);
        buttons.add(save);
        buttons.add(saveConnect);

        setLayout(new BorderLayout());
        add(content, BorderLayout.CENTER);
        add(buttons, BorderLayout.SOUTH);
        getRootPane().setDefaultButton(saveConnect);
    }

    private void browseForKey() {
        JFileChooser fc = new JFileChooser(System.getProperty("user.home") + "/.ssh");
        fc.setDialogTitle("Select Private Key");
        if (fc.showOpenDialog(this) == JFileChooser.APPROVE_OPTION) {
            keyField.setText(fc.getSelectedFile().getAbsolutePath());
        }
    }

    private void doSave(boolean andConnect) {
        String name = nameField.getText().strip();
        String host = hostField.getText().strip();
        String user = userField.getText().strip();

        if (name.isEmpty() || host.isEmpty() || user.isEmpty()) {
            JOptionPane.showMessageDialog(this,
                    "Session name, host, and username are required.",
                    "Validation", JOptionPane.WARNING_MESSAGE);
            return;
        }

        ServerEntry entry = new ServerEntry();
        entry.setName(name);
        entry.setHost(host);
        entry.setPort((Integer) portSpinner.getValue());
        entry.setUsername(user);
        entry.setPassword(new String(passField.getPassword()));
        entry.setPrivateKeyPath(keyField.getText().strip());

        ServerFolder folder = (ServerFolder) folderCombo.getSelectedItem();
        if (folder == null && !configManager.getConfig().getFolders().isEmpty()) {
            folder = configManager.getConfig().getFolders().get(0);
        }
        if (folder != null) {
            folder.getServers().add(entry);
        }
        configManager.saveConfig();

        savedListeners.forEach(Runnable::run);

        if (andConnect) {
            // Signal to open a session — callers can also listen via savedListeners
            // and then grab the last-added entry.  SessionTabPane wires this up.
            fireConnectRequest(entry);
        }
        dispose();
    }

    // Optional connect callback — set by SessionTabPane
    private java.util.function.Consumer<ServerEntry> connectCallback;

    public void setConnectCallback(java.util.function.Consumer<ServerEntry> cb) {
        this.connectCallback = cb;
    }

    private void fireConnectRequest(ServerEntry entry) {
        if (connectCallback != null) connectCallback.accept(entry);
    }

    // ---- helpers -------------------------------------------------------

    private static JLabel label(String text) {
        return new JLabel(text);
    }
}
