package com.mobamacos.ui.files;

import com.mobamacos.ui.AppIcons;

import javax.swing.*;
import javax.swing.table.DefaultTableCellRenderer;
import javax.swing.table.DefaultTableModel;
import java.awt.*;
import java.awt.datatransfer.*;
import java.awt.event.*;
import java.io.File;
import java.io.IOException;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

/**
 * Generic file browser panel used for both the local and remote halves.
 * Supports keyboard navigation, double-click to enter directories, and
 * full drag-and-drop (export + import) with the other panel or Finder.
 *
 * Call {@link #setTransferTarget(FileBrowserPanel)} to wire the opposite panel
 * so the transfer button can send selected files across.
 */
public class FileBrowserPanel extends JPanel {

    // -----------------------------------------------------------------------
    // DnD flavors (shared between panels in the same JVM)
    // -----------------------------------------------------------------------

    static final DataFlavor REMOTE_FLAVOR = new DataFlavor(RemotePathData.class, "Remote File Paths");

    record RemotePathData(List<String> paths, FileProvider provider) {}

    // -----------------------------------------------------------------------
    // State (package-private so DnD handlers can access them)
    // -----------------------------------------------------------------------

    FileProvider      provider;
    String            currentPath = "";
    FileBrowserPanel  transferTarget;   // the opposite panel

    // -----------------------------------------------------------------------
    // UI
    // -----------------------------------------------------------------------

    private final JTextField       pathField;
    private final JTable           fileTable;
    private final DefaultTableModel tableModel;
    private final JLabel           statusLabel;
    private final JProgressBar     progressBar;
    private final JLabel           headerLabel;
    private final JButton          transferButton;

    private static final DateTimeFormatter DATE_FMT =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm").withZone(ZoneId.systemDefault());

    public FileBrowserPanel(String headerTitle) {
        setLayout(new BorderLayout());

        // ---- Header / toolbar ---------------------------------------------
        headerLabel = new JLabel(headerTitle);
        headerLabel.setFont(headerLabel.getFont().deriveFont(Font.BOLD, 11f));
        headerLabel.setBorder(BorderFactory.createEmptyBorder(0, 4, 0, 8));

        pathField = new JTextField();
        pathField.setFont(pathField.getFont().deriveFont(11f));
        pathField.addActionListener(e -> navigate(pathField.getText().strip()));

        JButton upBtn   = iconButton(AppIcons.GO_UP,   "Parent directory");
        JButton homeBtn = iconButton(AppIcons.GO_HOME, "Home directory");
        JButton refBtn  = iconButton(AppIcons.REFRESH, "Refresh");

        upBtn.addActionListener(e -> goUp());
        homeBtn.addActionListener(e -> { if (provider != null) navigate(provider.getHomePath()); });
        refBtn.addActionListener(e -> refresh());

        // Transfer button — direction icon set in setProvider()
        transferButton = iconButton(AppIcons.TRANSFER_DOWN, "Transfer selected files");
        transferButton.setEnabled(false);
        transferButton.addActionListener(e -> doTransferSelected());

        // Row 1: panel name (left) + transfer button (right)
        JPanel titleBar = new JPanel(new BorderLayout(4, 0));
        titleBar.setBorder(BorderFactory.createEmptyBorder(3, 4, 1, 4));
        titleBar.add(headerLabel,   BorderLayout.WEST);
        titleBar.add(transferButton, BorderLayout.EAST);

        // Row 2: nav buttons (left) + path field (center)
        JPanel navButtons = new JPanel(new FlowLayout(FlowLayout.LEFT, 2, 0));
        navButtons.add(upBtn);
        navButtons.add(homeBtn);
        navButtons.add(refBtn);

        JPanel navBar = new JPanel(new BorderLayout(4, 0));
        navBar.setBorder(BorderFactory.createEmptyBorder(1, 4, 3, 4));
        navBar.add(navButtons, BorderLayout.WEST);
        navBar.add(pathField,  BorderLayout.CENTER);

        JPanel toolbar = new JPanel(new BorderLayout());
        toolbar.add(titleBar, BorderLayout.NORTH);
        toolbar.add(navBar,   BorderLayout.SOUTH);

        // ---- File table ---------------------------------------------------
        tableModel = new DefaultTableModel(new String[]{"Name", "Size", "Modified"}, 0) {
            @Override public boolean isCellEditable(int r, int c) { return false; }
            @Override public Class<?> getColumnClass(int c) { return c == 0 ? FileEntry.class : String.class; }
        };
        fileTable = new JTable(tableModel);
        fileTable.setSelectionMode(ListSelectionModel.MULTIPLE_INTERVAL_SELECTION);
        fileTable.setShowGrid(false);
        fileTable.setIntercellSpacing(new Dimension(0, 0));
        fileTable.setRowHeight(20);
        fileTable.getTableHeader().setReorderingAllowed(false);
        fileTable.getColumnModel().getColumn(0).setPreferredWidth(200);
        fileTable.getColumnModel().getColumn(1).setPreferredWidth(70);
        fileTable.getColumnModel().getColumn(1).setMaxWidth(90);
        fileTable.getColumnModel().getColumn(2).setPreferredWidth(120);
        fileTable.getColumnModel().getColumn(2).setMaxWidth(140);

        // Name cell renderer — Paper icons
        fileTable.getColumnModel().getColumn(0).setCellRenderer(new DefaultTableCellRenderer() {
            @Override
            public Component getTableCellRendererComponent(JTable t, Object val,
                    boolean sel, boolean focus, int row, int col) {
                super.getTableCellRendererComponent(t, val, sel, focus, row, col);
                if (val instanceof FileEntry e) {
                    setText(e.name());
                    setIcon(AppIcons.get(e.isDirectory() ? AppIcons.FOLDER : AppIcons.FILE));
                }
                return this;
            }
        });

        // Double-click: enter directory
        fileTable.addMouseListener(new MouseAdapter() {
            @Override public void mouseClicked(MouseEvent e) {
                if (e.getClickCount() == 2) activateSelected();
            }
            @Override public void mousePressed(MouseEvent e)  { if (e.isPopupTrigger()) showContextMenu(e); }
            @Override public void mouseReleased(MouseEvent e) { if (e.isPopupTrigger()) showContextMenu(e); }
        });

        // Keyboard: Enter = activate, Backspace = go up
        fileTable.addKeyListener(new KeyAdapter() {
            @Override public void keyPressed(KeyEvent e) {
                if (e.getKeyCode() == KeyEvent.VK_ENTER)     activateSelected();
                if (e.getKeyCode() == KeyEvent.VK_BACK_SPACE) goUp();
            }
        });

        // Enable/disable transfer button based on selection
        fileTable.getSelectionModel().addListSelectionListener(e -> {
            if (!e.getValueIsAdjusting()) updateTransferButtonState();
        });

        // Drag & drop
        fileTable.setDragEnabled(true);
        fileTable.setDropMode(DropMode.ON);
        fileTable.setTransferHandler(new FileDndHandler(this));

        // ---- Status bar ---------------------------------------------------
        statusLabel = new JLabel(" ");
        statusLabel.setFont(statusLabel.getFont().deriveFont(10f));

        progressBar = new JProgressBar(0, 100);
        progressBar.setPreferredSize(new Dimension(150, 12));
        progressBar.setVisible(false);
        progressBar.setStringPainted(false);

        JPanel statusBar = new JPanel(new BorderLayout(6, 0));
        statusBar.setBorder(BorderFactory.createEmptyBorder(2, 6, 2, 6));
        statusBar.add(statusLabel, BorderLayout.CENTER);
        statusBar.add(progressBar, BorderLayout.EAST);

        // ---- Assemble -----------------------------------------------------
        JPanel body = new JPanel(new BorderLayout());
        body.add(toolbar,                       BorderLayout.NORTH);
        body.add(new JScrollPane(fileTable),    BorderLayout.CENTER);
        body.add(statusBar,                     BorderLayout.SOUTH);

        add(body, BorderLayout.CENTER);

        showDisconnected();
    }

    // -----------------------------------------------------------------------
    // Public API
    // -----------------------------------------------------------------------

    public void setProvider(FileProvider provider) {
        this.provider = provider;
        headerLabel.setText(provider.getDisplayName());
        // Swap the transfer button icon: ↓ for remote (download), ↑ for local (upload)
        String iconName = provider.isRemote() ? AppIcons.TRANSFER_DOWN : AppIcons.TRANSFER_UP;
        String tooltip  = provider.isRemote()
                ? "Download selected to local folder"
                : "Upload selected to remote folder";
        transferButton.setIcon(AppIcons.get(iconName));
        transferButton.setToolTipText(tooltip);
        updateTransferButtonState();
        navigate(provider.getHomePath());
    }

    public void clearProvider(String reason) {
        this.provider = null;
        tableModel.setRowCount(0);
        pathField.setText("");
        statusLabel.setText(reason);
        progressBar.setVisible(false);
        headerLabel.setText("Remote");
        transferButton.setEnabled(false);
    }

    /**
     * Links this panel to the opposite panel so the transfer button
     * knows where to send files.
     */
    public void setTransferTarget(FileBrowserPanel target) {
        this.transferTarget = target;
        updateTransferButtonState();
    }

    // -----------------------------------------------------------------------
    // Navigation
    // -----------------------------------------------------------------------

    void navigate(String path) {
        if (provider == null) return;
        currentPath = path;
        pathField.setText(path);
        refresh();
    }

    void goUp() {
        if (provider == null || currentPath.isEmpty()) return;
        String sep = provider.getSeparator();
        int idx = currentPath.lastIndexOf(sep);
        if (idx > 0)      navigate(currentPath.substring(0, idx));
        else if (idx == 0) navigate(sep);   // already at root
    }

    void refresh() {
        if (provider == null) return;
        statusLabel.setText("Loading\u2026");
        new SwingWorker<List<FileEntry>, Void>() {
            @Override protected List<FileEntry> doInBackground() throws Exception {
                return provider.list(currentPath);
            }
            @Override protected void done() {
                try {
                    tableModel.setRowCount(0);
                    for (FileEntry e : get()) {
                        tableModel.addRow(new Object[]{
                                e,
                                e.displaySize(),
                                e.modified() != null ? DATE_FMT.format(e.modified()) : ""
                        });
                    }
                    statusLabel.setText(tableModel.getRowCount() + " items");
                } catch (Exception ex) {
                    statusLabel.setText("Error: " + ex.getMessage());
                }
            }
        }.execute();
    }

    // -----------------------------------------------------------------------
    // Transfer button action
    // -----------------------------------------------------------------------

    private void doTransferSelected() {
        if (provider == null) return;
        if (transferTarget == null || transferTarget.provider == null) {
            statusLabel.setText("No target panel connected");
            return;
        }
        List<FileEntry> selected = getSelectedEntries();
        if (selected.isEmpty()) return;

        boolean downloadMode = provider.isRemote();   // remote→local; else local→remote

        FileProvider.ProgressListener progressListener = (fileName, transferred, total) ->
                SwingUtilities.invokeLater(() -> updateProgress(fileName, transferred, total));

        transferButton.setEnabled(false);
        progressBar.setValue(0);
        progressBar.setIndeterminate(true);
        progressBar.setVisible(true);

        new SwingWorker<Void, Void>() {
            @Override
            protected Void doInBackground() throws Exception {
                if (downloadMode) {
                    // Remote → Local
                    File localDir = new File(transferTarget.currentPath);
                    for (FileEntry entry : selected) {
                        String remotePath = currentPath + "/" + entry.name();
                        provider.download(remotePath, localDir, progressListener);
                    }
                } else {
                    // Local → Remote
                    for (FileEntry entry : selected) {
                        File localFile = new File(currentPath, entry.name());
                        transferTarget.provider.upload(localFile, transferTarget.currentPath, progressListener);
                    }
                }
                return null;
            }

            @Override
            protected void done() {
                hideProgress();
                updateTransferButtonState();
                transferTarget.refresh();
                try {
                    get();
                } catch (Exception ex) {
                    Throwable cause = ex.getCause() != null ? ex.getCause() : ex;
                    showError("Transfer failed: " + cause.getMessage());
                }
            }
        }.execute();
    }

    private void updateProgress(String fileName, long transferred, long total) {
        if (total > 0) {
            int pct = (int) (100L * transferred / total);
            progressBar.setIndeterminate(false);
            progressBar.setValue(pct);
            statusLabel.setText(fileName + "  " + formatBytes(transferred) + " / " + formatBytes(total));
        } else {
            progressBar.setIndeterminate(true);
            statusLabel.setText("Transferring " + fileName + "\u2026");
        }
        progressBar.setVisible(true);
    }

    private void hideProgress() {
        progressBar.setVisible(false);
        statusLabel.setText(tableModel.getRowCount() + " items");
    }

    void updateTransferButtonState() {
        boolean hasProvider = provider != null;
        boolean hasTarget   = transferTarget != null && transferTarget.provider != null;
        boolean hasSelection = fileTable.getSelectedRowCount() > 0;
        transferButton.setEnabled(hasProvider && hasTarget && hasSelection);
    }

    // -----------------------------------------------------------------------
    // Transfers (called by DnD handler)
    // -----------------------------------------------------------------------

    /** Upload local files into this panel's current remote directory. */
    void receiveLocalFiles(List<File> files) {
        if (provider == null) return;
        for (File f : files) {
            statusLabel.setText("Uploading " + f.getName() + "\u2026");
            new SwingWorker<Void, Void>() {
                @Override protected Void doInBackground() throws Exception {
                    provider.upload(f, currentPath);
                    return null;
                }
                @Override protected void done() {
                    try { get(); refresh(); }
                    catch (Exception ex) { showError("Upload failed: " + ex.getMessage()); }
                }
            }.execute();
        }
    }

    /** Download remote files from the other panel into this panel's current local directory. */
    void receiveRemoteFiles(RemotePathData data) {
        if (provider == null) return;
        File destDir = new File(currentPath);
        for (String remotePath : data.paths()) {
            statusLabel.setText("Downloading " + remotePath.substring(remotePath.lastIndexOf('/') + 1) + "\u2026");
            new SwingWorker<Void, Void>() {
                @Override protected Void doInBackground() throws Exception {
                    data.provider().download(remotePath, destDir);
                    return null;
                }
                @Override protected void done() {
                    try { get(); refresh(); }
                    catch (Exception ex) { showError("Download failed: " + ex.getMessage()); }
                }
            }.execute();
        }
    }

    // -----------------------------------------------------------------------
    // Internals
    // -----------------------------------------------------------------------

    List<FileEntry> getSelectedEntries() {
        List<FileEntry> result = new ArrayList<>();
        for (int row : fileTable.getSelectedRows()) {
            Object val = tableModel.getValueAt(row, 0);
            if (val instanceof FileEntry e) result.add(e);
        }
        return result;
    }

    private void activateSelected() {
        List<FileEntry> sel = getSelectedEntries();
        if (sel.size() == 1 && sel.get(0).isDirectory()) {
            navigate(currentPath + provider.getSeparator() + sel.get(0).name());
        }
    }

    private void showContextMenu(MouseEvent e) {
        int row = fileTable.rowAtPoint(e.getPoint());
        if (row >= 0) fileTable.setRowSelectionInterval(row, row);

        JPopupMenu menu = new JPopupMenu();
        List<FileEntry> sel = getSelectedEntries();

        if (provider != null && !sel.isEmpty()) {
            if (!provider.isRemote()) {
                JMenuItem open = new JMenuItem("Open");
                open.addActionListener(ev -> activateSelected());
                menu.add(open);
            } else {
                JMenuItem dl = new JMenuItem("Download to local folder\u2026");
                dl.addActionListener(ev -> promptDownload(sel));
                menu.add(dl);
            }
        }

        if (provider != null) {
            menu.addSeparator();
            JMenuItem newFolder = new JMenuItem("New Folder\u2026");
            newFolder.addActionListener(ev -> promptNewFolder());
            menu.add(newFolder);
        }

        menu.show(fileTable, e.getX(), e.getY());
    }

    private void promptDownload(List<FileEntry> entries) {
        JFileChooser fc = new JFileChooser();
        fc.setFileSelectionMode(JFileChooser.DIRECTORIES_ONLY);
        fc.setDialogTitle("Download to\u2026");
        if (fc.showOpenDialog(this) != JFileChooser.APPROVE_OPTION) return;
        File dest = fc.getSelectedFile();
        for (FileEntry e : entries) {
            String remotePath = currentPath + "/" + e.name();
            new SwingWorker<Void, Void>() {
                @Override protected Void doInBackground() throws Exception {
                    provider.download(remotePath, dest);
                    return null;
                }
                @Override protected void done() {
                    try { get(); statusLabel.setText("Downloaded " + e.name()); }
                    catch (Exception ex) { showError("Download failed: " + ex.getMessage()); }
                }
            }.execute();
        }
    }

    private void promptNewFolder() {
        String name = JOptionPane.showInputDialog(this, "Folder name:", "New Folder",
                JOptionPane.PLAIN_MESSAGE);
        if (name == null || name.isBlank()) return;
        String newPath = currentPath + provider.getSeparator() + name.strip();
        new SwingWorker<Void, Void>() {
            @Override protected Void doInBackground() throws Exception {
                provider.mkdir(newPath);
                return null;
            }
            @Override protected void done() {
                try { get(); refresh(); }
                catch (Exception ex) { showError("mkdir failed: " + ex.getMessage()); }
            }
        }.execute();
    }

    private void showDisconnected() {
        statusLabel.setText("Not connected");
        progressBar.setVisible(false);
    }

    void showError(String msg) {
        JOptionPane.showMessageDialog(this, msg, "File Transfer Error", JOptionPane.ERROR_MESSAGE);
    }

    private static String formatBytes(long bytes) {
        if (bytes < 1024L)               return bytes + " B";
        if (bytes < 1024L * 1024)        return String.format("%.1f KB", bytes / 1024.0);
        if (bytes < 1024L * 1024 * 1024) return String.format("%.1f MB", bytes / (1024.0 * 1024));
        return String.format("%.1f GB", bytes / (1024.0 * 1024 * 1024));
    }

    private static JButton iconButton(String iconName, String tooltip) {
        JButton b = new JButton(AppIcons.get(iconName));
        b.setToolTipText(tooltip);
        b.setFocusPainted(false);
        b.setBorderPainted(false);
        b.setContentAreaFilled(false);
        b.setMargin(new Insets(2, 2, 2, 2));
        b.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        return b;
    }

    // -----------------------------------------------------------------------
    // DnD handler (inner class keeps everything together)
    // -----------------------------------------------------------------------

    private static class FileDndHandler extends TransferHandler {

        private final FileBrowserPanel panel;

        FileDndHandler(FileBrowserPanel panel) { this.panel = panel; }

        // ---- Export -------------------------------------------------------

        @Override
        public int getSourceActions(JComponent c) { return COPY; }

        @Override
        protected Transferable createTransferable(JComponent c) {
            if (panel.provider == null) return null;
            List<FileEntry> selected = panel.getSelectedEntries();
            if (selected.isEmpty()) return null;

            if (!panel.provider.isRemote()) {
                List<File> files = selected.stream()
                        .map(e -> new File(panel.currentPath, e.name()))
                        .toList();
                return new FileListTransferable(files);
            } else {
                List<String> paths = selected.stream()
                        .map(e -> panel.currentPath + "/" + e.name())
                        .toList();
                return new RemoteTransferable(paths, panel.provider);
            }
        }

        // ---- Import -------------------------------------------------------

        @Override
        public boolean canImport(TransferSupport support) {
            if (panel.provider == null) return false;
            return support.isDataFlavorSupported(DataFlavor.javaFileListFlavor)
                    || support.isDataFlavorSupported(REMOTE_FLAVOR);
        }

        @Override
        public boolean importData(TransferSupport support) {
            if (panel.provider == null) return false;
            try {
                if (support.isDataFlavorSupported(DataFlavor.javaFileListFlavor)) {
                    @SuppressWarnings("unchecked")
                    List<File> files = (List<File>) support.getTransferable()
                            .getTransferData(DataFlavor.javaFileListFlavor);
                    panel.receiveLocalFiles(files);
                    return true;
                }
                if (support.isDataFlavorSupported(REMOTE_FLAVOR)) {
                    RemotePathData data = (RemotePathData) support.getTransferable()
                            .getTransferData(REMOTE_FLAVOR);
                    panel.receiveRemoteFiles(data);
                    return true;
                }
            } catch (Exception ex) {
                panel.showError("Drop failed: " + ex.getMessage());
            }
            return false;
        }
    }

    // -----------------------------------------------------------------------
    // Transferable implementations
    // -----------------------------------------------------------------------

    static class FileListTransferable implements Transferable {
        private final List<File> files;
        FileListTransferable(List<File> files) { this.files = files; }
        @Override public DataFlavor[] getTransferDataFlavors() { return new DataFlavor[]{DataFlavor.javaFileListFlavor}; }
        @Override public boolean isDataFlavorSupported(DataFlavor f) { return DataFlavor.javaFileListFlavor.equals(f); }
        @Override public Object getTransferData(DataFlavor f) throws UnsupportedFlavorException {
            if (!DataFlavor.javaFileListFlavor.equals(f)) throw new UnsupportedFlavorException(f);
            return files;
        }
    }

    static class RemoteTransferable implements Transferable {
        private final List<String>  paths;
        private final FileProvider  provider;
        RemoteTransferable(List<String> paths, FileProvider provider) {
            this.paths = paths; this.provider = provider;
        }
        @Override public DataFlavor[] getTransferDataFlavors() { return new DataFlavor[]{REMOTE_FLAVOR}; }
        @Override public boolean isDataFlavorSupported(DataFlavor f) { return REMOTE_FLAVOR.equals(f); }
        @Override public Object getTransferData(DataFlavor f) throws UnsupportedFlavorException {
            if (!REMOTE_FLAVOR.equals(f)) throw new UnsupportedFlavorException(f);
            return new RemotePathData(paths, provider);
        }
    }
}
