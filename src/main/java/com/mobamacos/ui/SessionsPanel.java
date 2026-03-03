package com.mobamacos.ui;

import com.jediterm.terminal.ui.JediTermWidget;
import com.mobamacos.config.ConfigManager;
import com.mobamacos.model.ServerEntry;
import com.mobamacos.model.ServerFolder;
import com.mobamacos.ui.dialogs.NewConnectionDialog;

import javax.swing.*;
import javax.swing.text.JTextComponent;
import javax.swing.tree.*;
import java.awt.*;
import java.awt.datatransfer.*;
import java.awt.event.*;
import java.util.stream.Collectors;

/**
 * Right-side sessions panel: search bar, server tree, New Connection button.
 * Has its own vertical tab bar so future right-side tabs can be added.
 */
public class SessionsPanel extends JPanel {

    private final ConfigManager  configManager;
    private final SessionTabPane sessionTabPane;

    private JTree                  treeView;
    private DefaultMutableTreeNode root;
    private DefaultTreeModel       treeModel;
    private String                 currentFilter = "";
    private JTextField             searchField;

    public SessionsPanel(ConfigManager configManager, SessionTabPane sessionTabPane) {
        this.configManager  = configManager;
        this.sessionTabPane = sessionTabPane;
        initUI();
    }

    // -----------------------------------------------------------------------
    // Public API
    // -----------------------------------------------------------------------

    public void refresh() { applyFilter(currentFilter); }

    // -----------------------------------------------------------------------
    // Build UI
    // -----------------------------------------------------------------------

    private void initUI() {
        setLayout(new BorderLayout());
        setPreferredSize(new Dimension(240, 0));
        setBorder(BorderFactory.createMatteBorder(0, 1, 0, 0,
                UIManager.getColor("Separator.foreground") != null
                        ? UIManager.getColor("Separator.foreground") : Color.LIGHT_GRAY));

        // Header label
        JLabel header = new JLabel("Sessions");
        header.setFont(header.getFont().deriveFont(Font.BOLD, 11f));
        header.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createMatteBorder(0, 0, 1, 0,
                        UIManager.getColor("Separator.foreground") != null
                                ? UIManager.getColor("Separator.foreground") : Color.LIGHT_GRAY),
                BorderFactory.createEmptyBorder(5, 8, 5, 8)));
        header.setIcon(AppIcons.get(AppIcons.TAB_SESSIONS));
        header.setIconTextGap(6);

        // Search bar
        searchField = new JTextField();
        searchField.putClientProperty("JTextField.placeholderText", "Quick connect…");
        searchField.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createMatteBorder(0, 0, 1, 0,
                        UIManager.getColor("Separator.foreground") != null
                                ? UIManager.getColor("Separator.foreground") : Color.LIGHT_GRAY),
                BorderFactory.createEmptyBorder(4, 6, 4, 6)));
        searchField.getDocument().addDocumentListener(new javax.swing.event.DocumentListener() {
            public void insertUpdate(javax.swing.event.DocumentEvent e)  { applyFilter(searchField.getText()); }
            public void removeUpdate(javax.swing.event.DocumentEvent e)  { applyFilter(searchField.getText()); }
            public void changedUpdate(javax.swing.event.DocumentEvent e) { applyFilter(searchField.getText()); }
        });
        searchField.addActionListener(e -> {
            ServerEntry first = firstVisibleServer();
            if (first != null) {
                searchField.setText("");
                sessionTabPane.openSession(first);
            }
        });

        // Tree
        root      = new DefaultMutableTreeNode("root");
        treeModel = new DefaultTreeModel(root);
        treeView  = new JTree(treeModel);
        treeView.setRootVisible(false);
        treeView.setShowsRootHandles(true);
        treeView.setRowHeight(24);
        treeView.setCellRenderer(new ServerTreeRenderer());
        treeView.setDragEnabled(true);
        treeView.setDropMode(DropMode.ON);
        treeView.setTransferHandler(new ServerTreeTransferHandler());

        UIManager.put("Tree.leafIcon",   new ImageIcon());
        UIManager.put("Tree.openIcon",   new ImageIcon());
        UIManager.put("Tree.closedIcon", new ImageIcon());

        treeView.addMouseListener(new MouseAdapter() {
            @Override public void mouseClicked(MouseEvent e)  { if (e.getClickCount() == 2) handleDoubleClick(e); }
            @Override public void mousePressed(MouseEvent e)  { maybeShowPopup(e); }
            @Override public void mouseReleased(MouseEvent e) { maybeShowPopup(e); }
        });

        JScrollPane scroll = new JScrollPane(treeView);
        scroll.setBorder(BorderFactory.createEmptyBorder());

        // New connection button
        JButton newBtn = new JButton("+ New Connection", AppIcons.get(AppIcons.SERVER));
        newBtn.setFocusPainted(false);
        newBtn.setBorderPainted(false);
        newBtn.setContentAreaFilled(false);
        newBtn.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        newBtn.setBorder(BorderFactory.createEmptyBorder(8, 10, 10, 12));
        newBtn.setHorizontalAlignment(SwingConstants.LEFT);
        newBtn.setIconTextGap(6);
        newBtn.addActionListener(e -> openNewConnectionDialog(null));

        JPanel top = new JPanel(new BorderLayout());
        top.add(header,      BorderLayout.NORTH);
        top.add(searchField, BorderLayout.SOUTH);

        add(top,    BorderLayout.NORTH);
        add(scroll, BorderLayout.CENTER);
        add(newBtn, BorderLayout.SOUTH);

        registerSlashShortcut();
        refresh();
    }

    // -----------------------------------------------------------------------
    // Filter
    // -----------------------------------------------------------------------

    private void applyFilter(String filter) {
        currentFilter = filter == null ? "" : filter.strip();
        root.removeAllChildren();
        for (ServerFolder folder : configManager.getConfig().getFolders()) {
            DefaultMutableTreeNode folderNode = new DefaultMutableTreeNode(folder);
            for (ServerEntry server : folder.getServers()) {
                if (currentFilter.isEmpty()
                        || server.getName().toLowerCase().contains(currentFilter.toLowerCase())
                        || server.getHost().toLowerCase().contains(currentFilter.toLowerCase())) {
                    folderNode.add(new DefaultMutableTreeNode(server));
                }
            }
            if (folderNode.getChildCount() > 0 || currentFilter.isEmpty()) {
                root.add(folderNode);
            }
        }
        treeModel.reload();
        expandAll();
    }

    // -----------------------------------------------------------------------
    // Mouse handlers
    // -----------------------------------------------------------------------

    private void handleDoubleClick(MouseEvent e) {
        TreePath path = treeView.getPathForLocation(e.getX(), e.getY());
        if (path == null) return;
        DefaultMutableTreeNode node = (DefaultMutableTreeNode) path.getLastPathComponent();
        if (node.getUserObject() instanceof ServerEntry server) {
            sessionTabPane.openSession(server);
        }
    }

    private void maybeShowPopup(MouseEvent e) {
        if (!e.isPopupTrigger()) return;
        TreePath path = treeView.getPathForLocation(e.getX(), e.getY());
        if (path != null) treeView.setSelectionPath(path);
        buildContextMenu(path).show(treeView, e.getX(), e.getY());
    }

    private JPopupMenu buildContextMenu(TreePath path) {
        JPopupMenu menu = new JPopupMenu();

        if (path != null) {
            DefaultMutableTreeNode node = (DefaultMutableTreeNode) path.getLastPathComponent();
            Object obj = node.getUserObject();

            if (obj instanceof ServerEntry server) {
                ServerFolder sourceFolder = node.getParent() instanceof DefaultMutableTreeNode pn
                        && pn.getUserObject() instanceof ServerFolder sf ? sf : null;

                JMenuItem connect = new JMenuItem("Connect", AppIcons.get(AppIcons.SERVER));
                connect.addActionListener(e -> sessionTabPane.openSession(server));
                menu.add(connect);
                menu.addSeparator();
                JMenuItem move = new JMenuItem("Move to Folder\u2026");
                move.addActionListener(e -> promptMoveServer(server, sourceFolder));
                menu.add(move);
                menu.addSeparator();
                JMenuItem delete = new JMenuItem("Delete");
                delete.addActionListener(e -> deleteServer(server));
                menu.add(delete);

            } else if (obj instanceof ServerFolder folder) {
                JMenuItem addServer = new JMenuItem("Add Server to Folder\u2026", AppIcons.get(AppIcons.SERVER));
                addServer.addActionListener(e -> openNewConnectionDialog(folder));
                menu.add(addServer);
                JMenuItem rename = new JMenuItem("Rename Folder\u2026", AppIcons.get(AppIcons.FOLDER));
                rename.addActionListener(e -> renameFolder(folder));
                menu.add(rename);
                if (!"SSH Config".equals(folder.getName())) {
                    menu.addSeparator();
                    JMenuItem del = new JMenuItem("Delete Folder");
                    del.addActionListener(e -> deleteFolder(folder));
                    menu.add(del);
                }
            }
        } else {
            JMenuItem nc = new JMenuItem("New Connection\u2026", AppIcons.get(AppIcons.SERVER));
            nc.addActionListener(e -> openNewConnectionDialog(null));
            menu.add(nc);
            JMenuItem nf = new JMenuItem("New Folder\u2026", AppIcons.get(AppIcons.FOLDER));
            nf.addActionListener(e -> newFolder());
            menu.add(nf);
        }
        return menu;
    }

    // -----------------------------------------------------------------------
    // Actions
    // -----------------------------------------------------------------------

    private void openNewConnectionDialog(ServerFolder preselected) {
        Window parent = SwingUtilities.getWindowAncestor(this);
        NewConnectionDialog dlg = new NewConnectionDialog(parent, configManager, preselected);
        dlg.addSavedListener(this::refresh);
        dlg.setConnectCallback(sessionTabPane::openSession);
        dlg.setVisible(true);
    }

    private void newFolder() {
        String name = JOptionPane.showInputDialog(this, "Folder name:", "New Folder", JOptionPane.PLAIN_MESSAGE);
        if (name != null && !name.isBlank()) {
            configManager.getConfig().getFolders().add(new ServerFolder(name.strip()));
            configManager.saveConfig();
            refresh();
        }
    }

    private void renameFolder(ServerFolder folder) {
        String name = (String) JOptionPane.showInputDialog(this, "Rename folder:", "Rename",
                JOptionPane.PLAIN_MESSAGE, null, null, folder.getName());
        if (name != null && !name.isBlank()) {
            folder.setName(name.strip());
            configManager.saveConfig();
            refresh();
        }
    }

    private void deleteFolder(ServerFolder folder) {
        if (JOptionPane.showConfirmDialog(this,
                "Delete folder \"" + folder.getName() + "\" and all its servers?",
                "Confirm", JOptionPane.YES_NO_OPTION) == JOptionPane.YES_OPTION) {
            configManager.getConfig().getFolders().remove(folder);
            configManager.saveConfig();
            refresh();
        }
    }

    private void deleteServer(ServerEntry server) {
        if (JOptionPane.showConfirmDialog(this,
                "Delete \"" + server.getName() + "\"?",
                "Confirm", JOptionPane.YES_NO_OPTION) == JOptionPane.YES_OPTION) {
            for (ServerFolder f : configManager.getConfig().getFolders()) f.getServers().remove(server);
            configManager.saveConfig();
            refresh();
        }
    }

    private void promptMoveServer(ServerEntry server, ServerFolder sourceFolder) {
        java.util.List<ServerFolder> targets = configManager.getConfig().getFolders().stream()
                .filter(f -> !"SSH Config".equals(f.getName()) && !f.equals(sourceFolder))
                .collect(Collectors.toList());

        if (targets.isEmpty()) {
            JOptionPane.showMessageDialog(this,
                    "No other folders exist. Create a folder first.",
                    "Move to Folder", JOptionPane.INFORMATION_MESSAGE);
            return;
        }

        ServerFolder dest = (ServerFolder) JOptionPane.showInputDialog(
                this, "Move \"" + server.getName() + "\" to:", "Move to Folder",
                JOptionPane.PLAIN_MESSAGE, AppIcons.get(AppIcons.FOLDER),
                targets.toArray(), targets.get(0));

        if (dest != null) moveToFolder(server, sourceFolder, dest);
    }

    private void moveToFolder(ServerEntry server, ServerFolder source, ServerFolder dest) {
        if (dest == null || dest.equals(source)) return;

        ServerEntry toAdd;
        if (source != null && "SSH Config".equals(source.getName())) {
            toAdd = new ServerEntry(server.getName(), server.getHost(),
                    server.getPort(), server.getUsername());
            toAdd.setPrivateKeyPath(server.getPrivateKeyPath());
            toAdd.setPassword(server.getPassword());
            toAdd.setProxyCommand(server.getProxyCommand());
        } else {
            toAdd = server;
            if (source != null) source.getServers().remove(server);
        }

        if (!dest.getServers().contains(toAdd)) dest.getServers().add(toAdd);
        configManager.saveConfig();
        refresh();
    }

    private void expandAll() {
        for (int i = 0; i < treeView.getRowCount(); i++) treeView.expandRow(i);
    }

    private ServerEntry firstVisibleServer() {
        for (int i = 0; i < root.getChildCount(); i++) {
            DefaultMutableTreeNode folderNode = (DefaultMutableTreeNode) root.getChildAt(i);
            for (int j = 0; j < folderNode.getChildCount(); j++) {
                DefaultMutableTreeNode child = (DefaultMutableTreeNode) folderNode.getChildAt(j);
                if (child.getUserObject() instanceof ServerEntry s) return s;
            }
        }
        return null;
    }

    private void registerSlashShortcut() {
        KeyboardFocusManager.getCurrentKeyboardFocusManager()
                .addKeyEventDispatcher(e -> {
                    if (e.getID() != KeyEvent.KEY_TYPED || e.getKeyChar() != '/') return false;
                    Component focused = KeyboardFocusManager
                            .getCurrentKeyboardFocusManager().getFocusOwner();
                    if (focused == null)                   return false;
                    if (focused instanceof JTextComponent) return false;
                    Component c = focused;
                    while (c != null) {
                        if (c instanceof JediTermWidget) return false;
                        c = c.getParent();
                    }
                    if (!SwingUtilities.isDescendingFrom(focused,
                            SwingUtilities.getWindowAncestor(SessionsPanel.this))) return false;
                    SwingUtilities.invokeLater(() -> {
                        searchField.requestFocusInWindow();
                        searchField.selectAll();
                    });
                    return true;
                });
    }

    // -----------------------------------------------------------------------
    // Tree renderer
    // -----------------------------------------------------------------------

    private static class ServerTreeRenderer extends DefaultTreeCellRenderer {
        @Override
        public Component getTreeCellRendererComponent(JTree tree, Object value,
                boolean selected, boolean expanded, boolean leaf, int row, boolean hasFocus) {
            super.getTreeCellRendererComponent(tree, value, selected, expanded, leaf, row, hasFocus);
            if (!(value instanceof DefaultMutableTreeNode node)) return this;
            Object obj = node.getUserObject();
            if (obj instanceof ServerFolder folder) {
                setText(folder.getName());
                boolean isSshConfig = "SSH Config".equals(folder.getName());
                setIcon(AppIcons.get(isSshConfig ? AppIcons.NETWORK_SERVER
                                                 : (expanded ? AppIcons.FOLDER_OPEN : AppIcons.FOLDER)));
            } else if (obj instanceof ServerEntry server) {
                setText("<html>" + server.getName()
                        + "&nbsp;<font color='gray'>"
                        + server.getUsername() + "@" + server.getHost()
                        + (server.getPort() != 22 ? ":" + server.getPort() : "")
                        + "</font></html>");
                setIcon(AppIcons.get(AppIcons.SERVER));
            }
            return this;
        }
    }

    // -----------------------------------------------------------------------
    // Drag-and-drop
    // -----------------------------------------------------------------------

    private record ServerDragData(ServerEntry entry, ServerFolder sourceFolder) {}

    private class ServerTreeTransferHandler extends TransferHandler {

        private static final DataFlavor FLAVOR =
                new DataFlavor(ServerDragData.class, "Server Entry");

        @Override public int getSourceActions(JComponent c) { return COPY_OR_MOVE; }

        @Override
        protected Transferable createTransferable(JComponent c) {
            TreePath path = treeView.getSelectionPath();
            if (path == null) return null;
            DefaultMutableTreeNode node = (DefaultMutableTreeNode) path.getLastPathComponent();
            if (!(node.getUserObject() instanceof ServerEntry server)) return null;
            DefaultMutableTreeNode parentNode = (DefaultMutableTreeNode) node.getParent();
            if (!(parentNode.getUserObject() instanceof ServerFolder folder)) return null;
            ServerDragData payload = new ServerDragData(server, folder);
            return new Transferable() {
                @Override public DataFlavor[] getTransferDataFlavors() { return new DataFlavor[]{FLAVOR}; }
                @Override public boolean isDataFlavorSupported(DataFlavor f) { return FLAVOR.equals(f); }
                @Override public Object getTransferData(DataFlavor f) throws UnsupportedFlavorException {
                    if (!FLAVOR.equals(f)) throw new UnsupportedFlavorException(f);
                    return payload;
                }
            };
        }

        @Override
        protected void exportDone(JComponent source, Transferable data, int action) {
            if (action != MOVE) return;
            try {
                ServerDragData d = (ServerDragData) data.getTransferData(FLAVOR);
                if ("SSH Config".equals(d.sourceFolder().getName())) return;
                d.sourceFolder().getServers().remove(d.entry());
                configManager.saveConfig();
                refresh();
            } catch (Exception ignored) {}
        }

        @Override
        public boolean canImport(TransferSupport support) {
            if (!support.isDrop() || !support.isDataFlavorSupported(FLAVOR)) return false;
            JTree.DropLocation dl = (JTree.DropLocation) support.getDropLocation();
            if (dl.getPath() == null) return false;
            DefaultMutableTreeNode node = (DefaultMutableTreeNode) dl.getPath().getLastPathComponent();
            if (!(node.getUserObject() instanceof ServerFolder dest)) return false;
            return !"SSH Config".equals(dest.getName());
        }

        @Override
        public boolean importData(TransferSupport support) {
            if (!canImport(support)) return false;
            try {
                ServerDragData d = (ServerDragData) support.getTransferable().getTransferData(FLAVOR);
                JTree.DropLocation dl = (JTree.DropLocation) support.getDropLocation();
                DefaultMutableTreeNode targetNode = (DefaultMutableTreeNode) dl.getPath().getLastPathComponent();
                moveToFolder(d.entry(), d.sourceFolder(), (ServerFolder) targetNode.getUserObject());
                return true;
            } catch (Exception e) { return false; }
        }
    }
}
