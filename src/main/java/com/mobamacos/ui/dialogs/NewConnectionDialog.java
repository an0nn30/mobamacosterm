package com.mobamacos.ui.dialogs;

import com.mobamacos.config.ConfigManager;
import com.mobamacos.model.ServerEntry;
import com.mobamacos.model.ServerFolder;
import com.mobamacos.ssh.MoshTtyConnector;

import javax.swing.*;
import java.awt.*;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

public class NewConnectionDialog extends JDialog {

    private final ConfigManager      configManager;
    private final List<Runnable>     savedListeners = new ArrayList<>();

    // ── Basic fields ────────────────────────────────────────────────────────
    private JTextField     nameField;
    private JTextField     hostField;
    private JSpinner       portSpinner;
    private JTextField     userField;
    private JPasswordField passField;
    private JTextField     keyField;
    private JButton        keyBrowseButton;

    // ── Advanced section ────────────────────────────────────────────────────
    private JButton advancedToggle;
    private JPanel  advancedPanel;

    // Proxy (inside advanced)
    private JLabel            proxyHeaderLabel;
    private JComboBox<String> proxyTypeCombo;
    private JLabel            proxyValueLabel;
    private JTextField        proxyValueField;

    // Mosh (inside advanced)
    private JCheckBox         useMoshCheckbox;
    private JPanel            moshFieldsPanel;
    private JTextField        moshPortField;
    private JTextField        moshServerField;
    private JTextField        moshSshOptsField;
    private JComboBox<String> moshPredictCombo;
    private JTextField        moshExtraArgsField;
    private JTextField        moshSftpProxyCmdField;

    // ── Folder / callbacks ──────────────────────────────────────────────────
    private JComboBox<ServerFolder> folderCombo;
    private java.util.function.Consumer<ServerEntry> connectCallback;

    // ── Edit mode ───────────────────────────────────────────────────────────
    private ServerEntry  editTarget;       // non-null when editing an existing entry
    private ServerFolder editSourceFolder; // folder the entry currently lives in

    // -----------------------------------------------------------------------

    public NewConnectionDialog(Window parent, ConfigManager configManager) {
        this(parent, configManager, null);
    }

    public NewConnectionDialog(Window parent, ConfigManager configManager,
                               ServerFolder preselectedFolder) {
        super(parent, "New SSH Connection", ModalityType.APPLICATION_MODAL);
        this.configManager = configManager;
        initUI(preselectedFolder);
    }

    /** Edit-mode constructor — pre-populates all fields from {@code entry}. */
    public NewConnectionDialog(Window parent, ConfigManager configManager,
                               ServerEntry entry, ServerFolder sourceFolder) {
        super(parent, "Edit SSH Connection", ModalityType.APPLICATION_MODAL);
        this.configManager    = configManager;
        this.editTarget       = entry;
        this.editSourceFolder = sourceFolder;
        initUI(sourceFolder);
        populateFrom(entry);
    }

    public void addSavedListener(Runnable r) { savedListeners.add(r); }

    public void setConnectCallback(java.util.function.Consumer<ServerEntry> cb) {
        this.connectCallback = cb;
    }

    // -----------------------------------------------------------------------
    // UI construction
    // -----------------------------------------------------------------------

    private void initUI(ServerFolder preselectedFolder) {
        setResizable(false);

        JPanel content = new JPanel(new GridBagLayout());
        content.setBorder(BorderFactory.createEmptyBorder(20, 24, 12, 24));

        // Reusable constraint templates
        GridBagConstraints lc = labelConstraints();
        GridBagConstraints fc = fieldConstraints();
        GridBagConstraints wr = wideRowConstraints();

        int row = 0;

        // Session Name
        lc.gridy = row; fc.gridy = row++;
        content.add(label("Session Name:"), lc);
        nameField = new JTextField(22);
        content.add(nameField, fc);

        // Host
        lc.gridy = row; fc.gridy = row++;
        content.add(label("Host / IP:"), lc);
        hostField = new JTextField(22);
        content.add(hostField, fc);

        // Port
        lc.gridy = row; fc.gridy = row++;
        content.add(label("Port:"), lc);
        portSpinner = new JSpinner(new SpinnerNumberModel(22, 1, 65535, 1));
        portSpinner.setPreferredSize(new Dimension(80, portSpinner.getPreferredSize().height));
        JPanel portWrap = new JPanel(new FlowLayout(FlowLayout.LEFT, 0, 0));
        portWrap.add(portSpinner);
        content.add(portWrap, fc);

        // Username
        lc.gridy = row; fc.gridy = row++;
        content.add(label("Username:"), lc);
        userField = new JTextField(System.getProperty("user.name"), 22);
        content.add(userField, fc);

        // Password
        lc.gridy = row; fc.gridy = row++;
        content.add(label("Password:"), lc);
        passField = new JPasswordField(22);
        content.add(passField, fc);

        // Private Key
        lc.gridy = row; fc.gridy = row++;
        content.add(label("Private Key:"), lc);
        JPanel keyPanel = new JPanel(new BorderLayout(6, 0));
        keyField = new JTextField();
        keyBrowseButton = new JButton("Browse…");
        keyBrowseButton.addActionListener(e -> browseForKey());
        keyPanel.add(keyField, BorderLayout.CENTER);
        keyPanel.add(keyBrowseButton, BorderLayout.EAST);
        content.add(keyPanel, fc);

        // ── Advanced disclosure toggle ─────────────────────────────────────
        wr.insets = new Insets(12, 0, 2, 0);
        wr.gridy  = row++;
        advancedToggle = new JButton("▶  Advanced");
        advancedToggle.setBorderPainted(false);
        advancedToggle.setContentAreaFilled(false);
        advancedToggle.setFocusPainted(false);
        advancedToggle.setHorizontalAlignment(SwingConstants.LEFT);
        advancedToggle.setFont(advancedToggle.getFont().deriveFont(Font.BOLD, 11f));
        advancedToggle.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        content.add(advancedToggle, wr);

        // ── Advanced panel (collapsed by default) ─────────────────────────
        advancedPanel = buildAdvancedPanel();
        advancedPanel.setVisible(false);
        wr.insets = new Insets(0, 0, 0, 0);
        wr.gridy  = row++;
        content.add(advancedPanel, wr);

        advancedToggle.addActionListener(e -> {
            boolean expanded = !advancedPanel.isVisible();
            advancedPanel.setVisible(expanded);
            advancedToggle.setText((expanded ? "▼" : "▶") + "  Advanced");
            packAndCenter();
        });

        // ── Folder ────────────────────────────────────────────────────────
        wr.insets = new Insets(10, 0, 0, 0);
        wr.gridy  = row++;
        content.add(new JSeparator(), wr);

        lc = labelConstraints(); lc.insets = new Insets(6, 0, 0, 12);
        fc = fieldConstraints(); fc.insets = new Insets(6, 0, 0, 0);
        lc.gridy = row; fc.gridy = row;
        content.add(label("Folder:"), lc);
        folderCombo = new JComboBox<>();
        for (ServerFolder f : configManager.getConfig().getFolders()) folderCombo.addItem(f);
        if (preselectedFolder != null) folderCombo.setSelectedItem(preselectedFolder);
        content.add(folderCombo, fc);

        // ── Buttons ───────────────────────────────────────────────────────
        JPanel buttons      = new JPanel(new FlowLayout(FlowLayout.RIGHT, 8, 8));
        JButton cancel      = new JButton("Cancel");
        JButton save        = new JButton("Save");
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

        packAndCenter();
    }

    // -----------------------------------------------------------------------
    // Advanced panel
    // -----------------------------------------------------------------------

    private JPanel buildAdvancedPanel() {
        JPanel panel = new JPanel(new GridBagLayout());

        GridBagConstraints lc = labelConstraints();
        GridBagConstraints fc = fieldConstraints();
        GridBagConstraints wr = wideRowConstraints();

        int row = 0;

        // ── Proxy / Tunnel ────────────────────────────────────────────────
        wr.insets = new Insets(6, 0, 2, 0); wr.gridy = row++;
        panel.add(new JSeparator(), wr);

        wr.insets = new Insets(0, 0, 4, 0); wr.gridy = row++;
        proxyHeaderLabel = new JLabel("Proxy / Tunnel (optional)");
        proxyHeaderLabel.setFont(proxyHeaderLabel.getFont().deriveFont(Font.BOLD));
        panel.add(proxyHeaderLabel, wr);

        lc.gridy = row; fc.gridy = row++;
        panel.add(label("Proxy Type:"), lc);
        proxyTypeCombo = new JComboBox<>(new String[]{"None", "ProxyJump", "ProxyCommand"});
        panel.add(proxyTypeCombo, fc);

        lc.gridy = row; fc.gridy = row++;
        proxyValueLabel = label("Jump Host:");
        panel.add(proxyValueLabel, lc);
        proxyValueField = new JTextField(22);
        proxyValueField.setEnabled(false);
        panel.add(proxyValueField, fc);

        proxyTypeCombo.addActionListener(e -> updateProxyFields());

        // ── Mosh ─────────────────────────────────────────────────────────
        wr.insets = new Insets(8, 0, 2, 0); wr.gridy = row++;
        panel.add(new JSeparator(), wr);

        // Detect mosh availability
        boolean isWindows = System.getProperty("os.name", "").toLowerCase().contains("win");
        String moshBin   = null;
        String moshError = null;
        if (!isWindows) {
            try   { moshBin   = MoshTtyConnector.findMosh(); }
            catch (IOException ex) { moshError = ex.getMessage(); }
        }

        wr.insets = new Insets(2, 0, 2, 0); wr.gridy = row++;
        useMoshCheckbox = new JCheckBox("Use Mosh (requires mosh-server on remote host)");
        useMoshCheckbox.setEnabled(moshBin != null);
        if (isWindows) {
            useMoshCheckbox.setToolTipText("Mosh is not available on Windows.");
        } else if (moshError != null) {
            String html = "<html>"
                    + moshError.replace("&", "&amp;").replace("<", "&lt;")
                               .replace(">", "&gt;").replace("\n", "<br>")
                    + "</html>";
            useMoshCheckbox.setToolTipText(html);
        } else {
            useMoshCheckbox.setToolTipText(
                    "<html>Connect via Mosh for roaming support and resilience to packet loss.<br>"
                    + "mosh-server must be installed on the remote host.</html>");
        }
        panel.add(useMoshCheckbox, wr);

        // Mosh extra fields (revealed when checkbox is ticked)
        moshFieldsPanel = buildMoshFieldsPanel();
        moshFieldsPanel.setVisible(false);
        wr.insets = new Insets(0, 0, 4, 0); wr.gridy = row++;
        panel.add(moshFieldsPanel, wr);

        useMoshCheckbox.addActionListener(e -> updateMoshMode());

        return panel;
    }

    private JPanel buildMoshFieldsPanel() {
        JPanel p = new JPanel(new GridBagLayout());
        // Indent to signal these are sub-options of "Use Mosh"
        p.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createMatteBorder(0, 3, 0, 0,
                        UIManager.getColor("Component.accentColor") != null
                                ? UIManager.getColor("Component.accentColor")
                                : new Color(75, 110, 175)),
                BorderFactory.createEmptyBorder(4, 12, 4, 0)));

        GridBagConstraints lc = labelConstraints(); lc.insets = new Insets(3, 0, 3, 12);
        GridBagConstraints fc = fieldConstraints(); fc.insets = new Insets(3, 0, 3, 0);

        int row = 0;

        // SSH options passed to mosh's --ssh flag
        lc.gridy = row; fc.gridy = row++;
        p.add(label("SSH Opts:"), lc);
        moshSshOptsField = new JTextField();
        moshSshOptsField.putClientProperty("JTextField.placeholderText",
                "-i ~/.ssh/id_ed25519 -oProxyCommand=\"cloudflared access ssh --hostname host\"");
        moshSshOptsField.setToolTipText(
                "<html>Options appended to <code>ssh</code> when mosh bootstraps the connection.<br>"
                + "These become <code>--ssh=ssh &lt;value&gt;</code> on the mosh command line.<br>"
                + "Leave blank to use plain <code>ssh</code> with no extra options.</html>");
        p.add(moshSshOptsField, fc);

        // UDP port
        lc.gridy = row; fc.gridy = row++;
        p.add(label("UDP Port:"), lc);
        moshPortField = new JTextField();
        moshPortField.putClientProperty("JTextField.placeholderText", "60001  or  60001:60010");
        moshPortField.setToolTipText(
                "<html>Override the default UDP port range (60000–61000).<br>"
                + "Useful when only certain ports are open in the firewall.</html>");
        p.add(moshPortField, fc);

        // Remote mosh-server path
        lc.gridy = row; fc.gridy = row++;
        p.add(label("Server path:"), lc);
        moshServerField = new JTextField();
        moshServerField.putClientProperty("JTextField.placeholderText", "/usr/local/bin/mosh-server");
        moshServerField.setToolTipText(
                "<html>Path to mosh-server on the remote host.<br>"
                + "Leave blank to use mosh-server found in the remote PATH.</html>");
        p.add(moshServerField, fc);

        // Predict mode
        lc.gridy = row; fc.gridy = row++;
        p.add(label("Predict mode:"), lc);
        moshPredictCombo = new JComboBox<>(new String[]{"adaptive", "always", "never", "experimental"});
        moshPredictCombo.setToolTipText(
                "<html><b>adaptive</b> — predict when network latency is high (default)<br>"
                + "<b>always</b> — always show instant local echo<br>"
                + "<b>never</b> — no prediction; show only server-confirmed output<br>"
                + "<b>experimental</b> — predict all keystrokes including Ctrl sequences</html>");
        p.add(moshPredictCombo, fc);

        // Extra args
        lc.gridy = row; fc.gridy = row++;
        p.add(label("Extra args:"), lc);
        moshExtraArgsField = new JTextField();
        moshExtraArgsField.putClientProperty("JTextField.placeholderText",
                "--experimental-remote-ip=local  --no-init");
        moshExtraArgsField.setToolTipText(
                "<html>Raw flags appended verbatim to the mosh command.<br>"
                + "Space-separated, e.g. <code>--experimental-remote-ip=local --no-init</code></html>");
        p.add(moshExtraArgsField, fc);

        // SFTP proxy command — used only for the SSHJ file-transfer connection
        lc.gridy = row; fc.gridy = row++;
        JLabel sftpProxyLabel = label("SFTP Proxy:");
        sftpProxyLabel.setToolTipText(
                "<html>ProxyCommand for the <b>file-transfer</b> (SFTP) connection only.<br>"
                + "Mosh's UDP transport and the SFTP TCP connection are independent.<br>"
                + "Example: <code>cloudflared access ssh --hostname %h</code><br>"
                + "<code>%h</code> → host &nbsp; <code>%p</code> → port</html>");
        p.add(sftpProxyLabel, lc);
        moshSftpProxyCmdField = new JTextField();
        moshSftpProxyCmdField.putClientProperty("JTextField.placeholderText",
                "cloudflared access ssh --hostname %h");
        moshSftpProxyCmdField.setToolTipText(sftpProxyLabel.getToolTipText());
        p.add(moshSftpProxyCmdField, fc);

        return p;
    }

    // -----------------------------------------------------------------------
    // Event handlers
    // -----------------------------------------------------------------------

    /** Called whenever the "Use Mosh" checkbox changes. */
    private void updateMoshMode() {
        boolean mosh = useMoshCheckbox.isSelected();
        moshFieldsPanel.setVisible(mosh);

        // Disable SSH-specific fields that mosh bypasses
        passField.setEnabled(!mosh);
        keyField.setEnabled(!mosh);
        keyBrowseButton.setEnabled(!mosh);

        // Disable the proxy section — mosh uses SSH Opts instead
        proxyHeaderLabel.setEnabled(!mosh);
        proxyTypeCombo.setEnabled(!mosh);
        proxyValueLabel.setEnabled(!mosh);
        proxyValueField.setEnabled(!mosh && !"None".equals(proxyTypeCombo.getSelectedItem()));

        // Make sure Advanced is expanded so the user sees the SSH Opts field
        if (mosh && !advancedPanel.isVisible()) {
            advancedPanel.setVisible(true);
            advancedToggle.setText("▼  Advanced");
        }

        packAndCenter();
    }

    private void updateProxyFields() {
        String type    = (String) proxyTypeCombo.getSelectedItem();
        boolean mosh    = useMoshCheckbox != null && useMoshCheckbox.isSelected();
        boolean enabled = !mosh && !"None".equals(type);
        proxyValueField.setEnabled(enabled);

        if ("ProxyJump".equals(type)) {
            proxyValueLabel.setText("Jump Host:");
            proxyValueField.putClientProperty("JTextField.placeholderText",
                    "user@bastion.example.com  or  bastion:2222");
            proxyValueField.setToolTipText(
                    "<html>SSH jump host — equivalent to <code>ssh -J</code><br>"
                    + "Format: <code>[user@]host[:port]</code><br>"
                    + "The system <code>ssh</code> binary handles auth for the jump host.</html>");
        } else if ("ProxyCommand".equals(type)) {
            proxyValueLabel.setText("Command:");
            proxyValueField.putClientProperty("JTextField.placeholderText",
                    "ssh -W %h:%p bastion   or   cloudflared access ssh --hostname %h");
            proxyValueField.setToolTipText(
                    "<html>Arbitrary proxy command whose stdin/stdout become the SSH transport.<br>"
                    + "<code>%h</code> → target host &nbsp; <code>%p</code> → target port</html>");
        } else {
            proxyValueLabel.setText("Jump Host:");
            proxyValueField.putClientProperty("JTextField.placeholderText", "");
            proxyValueField.setToolTipText(null);
        }
        proxyValueField.repaint();
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

        ServerFolder selectedFolder = (ServerFolder) folderCombo.getSelectedItem();
        if (selectedFolder == null && !configManager.getConfig().getFolders().isEmpty()) {
            selectedFolder = configManager.getConfig().getFolders().get(0);
        }

        if (editTarget != null) {
            // ── Edit mode: update the existing entry in-place ──────────────
            applyFieldsTo(editTarget);
            // Move to a different folder if the user changed the selection
            if (selectedFolder != null && !selectedFolder.equals(editSourceFolder)) {
                if (editSourceFolder != null) editSourceFolder.getServers().remove(editTarget);
                if (!selectedFolder.getServers().contains(editTarget)) {
                    selectedFolder.getServers().add(editTarget);
                }
            }
            configManager.saveConfig();
            savedListeners.forEach(Runnable::run);
            if (andConnect) fireConnectRequest(editTarget);
        } else {
            // ── New entry ──────────────────────────────────────────────────
            ServerEntry entry = new ServerEntry();
            applyFieldsTo(entry);
            if (selectedFolder != null) selectedFolder.getServers().add(entry);
            configManager.saveConfig();
            savedListeners.forEach(Runnable::run);
            if (andConnect) fireConnectRequest(entry);
        }

        dispose();
    }

    private void fireConnectRequest(ServerEntry entry) {
        if (connectCallback != null) connectCallback.accept(entry);
    }

    // -----------------------------------------------------------------------
    // Edit-mode helpers
    // -----------------------------------------------------------------------

    /** Pre-fills all form fields from an existing entry and expands Advanced if needed. */
    private void populateFrom(ServerEntry e) {
        nameField.setText(e.getName());
        hostField.setText(e.getHost());
        portSpinner.setValue(e.getPort());
        userField.setText(e.getUsername());
        passField.setText(e.getPassword());
        keyField.setText(e.getPrivateKeyPath());

        boolean hasProxy = (e.getProxyJump() != null && !e.getProxyJump().isBlank())
                        || (e.getProxyCommand() != null && !e.getProxyCommand().isBlank());
        if (hasProxy) {
            if (e.getProxyJump() != null && !e.getProxyJump().isBlank()) {
                proxyTypeCombo.setSelectedItem("ProxyJump");
                proxyValueField.setText(e.getProxyJump());
            } else {
                proxyTypeCombo.setSelectedItem("ProxyCommand");
                proxyValueField.setText(e.getProxyCommand());
            }
            updateProxyFields();
        }

        if (e.isUseMosh()) {
            useMoshCheckbox.setSelected(true);
            moshSshOptsField.setText(e.getMoshSshOpts());
            moshPortField.setText(e.getMoshPort());
            moshServerField.setText(e.getMoshServerPath());
            moshPredictCombo.setSelectedItem(
                    e.getMoshPredictMode() != null ? e.getMoshPredictMode() : "adaptive");
            moshExtraArgsField.setText(e.getMoshExtraArgs());
            moshSftpProxyCmdField.setText(e.getMoshSftpProxyCommand());
        }

        if (hasProxy || e.isUseMosh()) {
            advancedPanel.setVisible(true);
            advancedToggle.setText("▼  Advanced");
        }

        updateMoshMode(); // apply enabled/disabled state and show moshFieldsPanel
        packAndCenter();
    }

    /** Writes the current form values into {@code entry} (used for both create and edit). */
    private void applyFieldsTo(ServerEntry entry) {
        entry.setName(nameField.getText().strip());
        entry.setHost(hostField.getText().strip());
        entry.setPort((Integer) portSpinner.getValue());
        entry.setUsername(userField.getText().strip());
        entry.setPassword(new String(passField.getPassword()));
        entry.setPrivateKeyPath(keyField.getText().strip());

        // Clear then re-apply proxy (so removing a proxy on edit works correctly)
        entry.setProxyJump("");
        entry.setProxyCommand("");
        String proxyType  = (String) proxyTypeCombo.getSelectedItem();
        String proxyValue = proxyValueField.getText().strip();
        if ("ProxyJump".equals(proxyType) && !proxyValue.isEmpty()) {
            entry.setProxyJump(proxyValue);
        } else if ("ProxyCommand".equals(proxyType) && !proxyValue.isEmpty()) {
            entry.setProxyCommand(proxyValue);
        }

        entry.setUseMosh(useMoshCheckbox.isSelected());
        if (useMoshCheckbox.isSelected()) {
            entry.setMoshSshOpts(moshSshOptsField.getText().strip());
            entry.setMoshPort(moshPortField.getText().strip());
            entry.setMoshServerPath(moshServerField.getText().strip());
            entry.setMoshPredictMode((String) moshPredictCombo.getSelectedItem());
            entry.setMoshExtraArgs(moshExtraArgsField.getText().strip());
            entry.setMoshSftpProxyCommand(moshSftpProxyCmdField.getText().strip());
        } else {
            entry.setMoshSshOpts("");
            entry.setMoshPort("");
            entry.setMoshServerPath("");
            entry.setMoshPredictMode("adaptive");
            entry.setMoshExtraArgs("");
            entry.setMoshSftpProxyCommand("");
        }
    }

    // -----------------------------------------------------------------------
    // Helpers
    // -----------------------------------------------------------------------

    /** Pack, enforce minimum width, then re-centre over parent. */
    private void packAndCenter() {
        pack();
        if (getWidth() < 520) setSize(520, getHeight());
        setLocationRelativeTo(getParent());
    }

    private static GridBagConstraints labelConstraints() {
        GridBagConstraints c = new GridBagConstraints();
        c.anchor = GridBagConstraints.WEST;
        c.insets = new Insets(6, 0, 6, 12);
        c.gridx  = 0;
        return c;
    }

    private static GridBagConstraints fieldConstraints() {
        GridBagConstraints c = new GridBagConstraints();
        c.fill    = GridBagConstraints.HORIZONTAL;
        c.weightx = 1.0;
        c.insets  = new Insets(6, 0, 6, 0);
        c.gridx   = 1;
        return c;
    }

    private static GridBagConstraints wideRowConstraints() {
        GridBagConstraints c = new GridBagConstraints();
        c.gridx     = 0;
        c.gridwidth = 2;
        c.fill      = GridBagConstraints.HORIZONTAL;
        c.anchor    = GridBagConstraints.WEST;
        return c;
    }

    private static JLabel label(String text) { return new JLabel(text); }
}
