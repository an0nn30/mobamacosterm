package com.github.an0nn30.conch.ui.files;

import com.github.an0nn30.conch.model.ServerEntry;
import com.github.an0nn30.conch.ui.SessionTabPane;

import javax.swing.*;
import java.awt.*;

/**
 * Split-pane file browser:
 *   top    — remote filesystem (synced with the active SSH session's CWD)
 *   bottom — local filesystem (always available)
 *
 * Implements {@link SessionTabPane.SessionListener} so it auto-connects
 * whenever the user switches to a different SSH session tab, and tracks
 * the terminal's working directory via OSC 7 escape sequences.
 */
public class FileTransferPanel extends JPanel implements SessionTabPane.SessionListener {

    private final FileBrowserPanel remotePanel;
    private final FileBrowserPanel localPanel;

    private ServerEntry        connectedServer;
    private RemoteFileProvider remoteProvider;

    public FileTransferPanel() {
        setLayout(new BorderLayout());

        remotePanel = new FileBrowserPanel("Remote");
        localPanel  = new FileBrowserPanel("Local");

        localPanel.setProvider(new LocalFileProvider());

        // Link panels so each transfer button knows the destination
        remotePanel.setTransferTarget(localPanel);
        localPanel.setTransferTarget(remotePanel);

        JSplitPane split = new JSplitPane(JSplitPane.VERTICAL_SPLIT, remotePanel, localPanel);
        split.setResizeWeight(0.5);
        split.setDividerSize(5);
        split.setContinuousLayout(true);

        add(split, BorderLayout.CENTER);

        remotePanel.clearProvider("Select an SSH session to browse remote files");
    }

    // -----------------------------------------------------------------------
    // SessionListener — called by SessionTabPane when the active tab changes
    // -----------------------------------------------------------------------

    @Override
    public void onSessionActivated(ServerEntry server) {
        if (server.equals(connectedServer)) return;   // already connected
        connectToServer(server);
    }

    /**
     * Called whenever the active terminal's shell reports a new working directory
     * (via OSC 7 escape sequence).  Navigates the remote file browser to that path.
     */
    @Override
    public void onCwdChanged(String absolutePath) {
        if (remotePanel.provider == null) return;
        remotePanel.navigate(absolutePath);
    }

    /**
     * Called when a local terminal tab becomes active.
     * Shows a local file browser in the remote (top) panel so it tracks the shell CWD.
     */
    @Override
    public void onLocalSessionActivated() {
        if (remotePanel.provider instanceof LocalFileProvider) return; // already set up
        connectedServer = null;
        if (remoteProvider != null) {
            try { remoteProvider.close(); } catch (Exception ignored) {}
            remoteProvider = null;
        }
        remotePanel.setProvider(new LocalFileProvider());
        localPanel.updateTransferButtonState();
    }

    @Override
    public void onSessionDeactivated() {
        // keep old connection alive; user may switch back
    }

    // -----------------------------------------------------------------------
    // Connect / disconnect
    // -----------------------------------------------------------------------

    private void connectToServer(ServerEntry server) {
        remotePanel.clearProvider("Connecting to " + server.getHost() + "\u2026");

        new SwingWorker<RemoteFileProvider, Void>() {
            @Override
            protected RemoteFileProvider doInBackground() throws Exception {
                if (remoteProvider != null) {
                    try { remoteProvider.close(); } catch (Exception ignored) {}
                }
                return new RemoteFileProvider(server);
            }

            @Override
            protected void done() {
                try {
                    remoteProvider  = get();
                    connectedServer = server;
                    remotePanel.setProvider(remoteProvider);
                    // Local panel's upload button can now reach the remote provider
                    localPanel.updateTransferButtonState();
                } catch (Exception e) {
                    Throwable cause = e.getCause() != null ? e.getCause() : e;
                    remotePanel.clearProvider("Connection failed: " + cause.getMessage());
                }
            }
        }.execute();
    }

    /** Called by MainWindow on app close to cleanly shut down SFTP. */
    public void dispose() {
        if (remoteProvider != null) {
            try { remoteProvider.close(); } catch (Exception ignored) {}
        }
    }
}
