package com.github.an0nn30.conch.ui.dialogs;

import com.github.an0nn30.conch.config.ConfigManager;
import com.github.an0nn30.conch.model.TunnelConfig;
import com.github.an0nn30.conch.ssh.TunnelManager;

import javax.swing.*;
import javax.swing.table.DefaultTableCellRenderer;
import javax.swing.table.DefaultTableModel;
import java.awt.*;
import java.util.ArrayList;
import java.util.List;

public class TunnelManagerDialog extends JDialog {

    private final ConfigManager configManager;
    private final TunnelManager tunnelManager;

    private final DefaultTableModel tableModel;
    private final JTable            table;
    private       Timer             refreshTimer;

    private static final String[] COLUMNS =
            { "Status", "Label", "Local Port", "Remote", "Via" };

    public TunnelManagerDialog(Window owner,
                               ConfigManager configManager,
                               TunnelManager tunnelManager) {
        super(owner, "SSH Tunnels", ModalityType.MODELESS);
        this.configManager = configManager;
        this.tunnelManager = tunnelManager;

        setSize(660, 360);
        setMinimumSize(new Dimension(500, 260));
        setLocationRelativeTo(owner);

        tableModel = new DefaultTableModel(COLUMNS, 0) {
            @Override public boolean isCellEditable(int r, int c) { return false; }
        };
        table = buildTable();

        JScrollPane scroll = new JScrollPane(table);

        JButton newBtn   = new JButton("New Tunnel\u2026");
        JButton stopBtn  = new JButton("Stop");
        JButton closeBtn = new JButton("Close");

        newBtn.addActionListener(e  -> openNewTunnelDialog());
        stopBtn.addActionListener(e -> stopSelected());
        closeBtn.addActionListener(e -> dispose());

        JPanel buttons = new JPanel(new FlowLayout(FlowLayout.RIGHT, 8, 8));
        buttons.add(newBtn);
        buttons.add(stopBtn);
        buttons.add(closeBtn);

        JLabel hint = new JLabel(
                "  Tunnels forward localhost:localPort to remoteHost:remotePort via the SSH server.");
        hint.setFont(hint.getFont().deriveFont(Font.ITALIC, 11f));

        getContentPane().setLayout(new BorderLayout());
        getContentPane().add(hint,    BorderLayout.NORTH);
        getContentPane().add(scroll,  BorderLayout.CENTER);
        getContentPane().add(buttons, BorderLayout.SOUTH);

        refreshTable();

        // Refresh every 3 s so dropped tunnels are reflected in the Status column
        refreshTimer = new Timer(3000, e -> refreshTable());
        refreshTimer.start();
        addWindowListener(new java.awt.event.WindowAdapter() {
            @Override public void windowClosed(java.awt.event.WindowEvent e) {
                refreshTimer.stop();
            }
        });
    }

    // -----------------------------------------------------------------------

    private JTable buildTable() {
        JTable t = new JTable(tableModel);
        t.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        t.setRowHeight(22);
        t.getTableHeader().setReorderingAllowed(false);

        // Column widths
        t.getColumnModel().getColumn(0).setPreferredWidth(70);
        t.getColumnModel().getColumn(0).setMaxWidth(80);
        t.getColumnModel().getColumn(2).setPreferredWidth(80);
        t.getColumnModel().getColumn(2).setMaxWidth(90);

        // Color the Status column
        t.getColumnModel().getColumn(0).setCellRenderer(new DefaultTableCellRenderer() {
            private static final Color GREEN = new Color(60, 180, 60);
            private static final Color RED   = new Color(200, 60, 60);

            @Override
            public Component getTableCellRendererComponent(
                    JTable tbl, Object val, boolean sel, boolean focus, int row, int col) {
                super.getTableCellRendererComponent(tbl, val, sel, focus, row, col);
                boolean active = "Active".equals(val);
                if (!sel) setForeground(active ? GREEN : RED);
                setText(active ? "\u25cf Active" : "\u25cf Stopped");
                return this;
            }
        });

        return t;
    }

    // -----------------------------------------------------------------------

    private void refreshTable() {
        int selectedRow = table.getSelectedRow();
        tableModel.setRowCount(0);

        for (TunnelManager.ActiveTunnel tunnel : tunnelManager.getTunnels()) {
            TunnelConfig cfg = tunnel.getConfig();
            tableModel.addRow(new Object[]{
                    tunnel.isRunning() ? "Active" : "Stopped",
                    cfg.getLabel(),
                    cfg.getLocalPort(),
                    cfg.getRemoteHost() + ":" + cfg.getRemotePort(),
                    cfg.getServer().getName()
            });
        }

        if (selectedRow >= 0 && selectedRow < tableModel.getRowCount()) {
            table.getSelectionModel().setSelectionInterval(selectedRow, selectedRow);
        }
    }

    private void stopSelected() {
        int row = table.getSelectedRow();
        if (row < 0) return;
        List<TunnelManager.ActiveTunnel> snapshot = new ArrayList<>(tunnelManager.getTunnels());
        if (row >= snapshot.size()) return;
        tunnelManager.stop(snapshot.get(row));
        refreshTable();
    }

    private void openNewTunnelDialog() {
        NewTunnelDialog dlg = new NewTunnelDialog(this, configManager, tunnelManager);
        dlg.setOnConnected(this::refreshTable);
        dlg.setVisible(true);
    }
}
