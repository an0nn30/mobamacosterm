package com.github.an0nn30.conch.ssh;

import com.github.an0nn30.conch.model.TunnelConfig;
import net.schmizz.sshj.SSHClient;
import net.schmizz.sshj.connection.channel.direct.LocalPortForwarder;
import net.schmizz.sshj.connection.channel.direct.Parameters;

import java.io.IOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

public class TunnelManager {

    private final SshSessionManager          sessionManager = new SshSessionManager();
    private final List<ActiveTunnel>         tunnels        = new CopyOnWriteArrayList<>();

    /**
     * Opens a local port-forward tunnel and returns immediately.
     * The tunnel runs on a daemon thread until {@link ActiveTunnel#stop()} is called
     * or the SSH connection drops.
     *
     * @throws Exception if the SSH connection or port binding fails
     */
    public ActiveTunnel start(TunnelConfig config) throws Exception {
        SSHClient ssh = sessionManager.connectAndAuth(config.getServer());

        Parameters params = new Parameters(
                "127.0.0.1", config.getLocalPort(),
                config.getRemoteHost(), config.getRemotePort());

        ServerSocket ss = new ServerSocket();
        ss.setReuseAddress(true);
        ss.bind(new InetSocketAddress(
                InetAddress.getByName("127.0.0.1"), config.getLocalPort()));

        LocalPortForwarder forwarder = ssh.newLocalPortForwarder(params, ss);

        ActiveTunnel tunnel = new ActiveTunnel(config, ssh, ss, forwarder);
        tunnels.add(tunnel);
        return tunnel;
    }

    /** Stops the tunnel and removes it from the managed list. */
    public void stop(ActiveTunnel tunnel) {
        tunnel.stop();
        tunnels.remove(tunnel);
    }

    /** Returns an unmodifiable snapshot of all managed tunnels (including dropped ones). */
    public List<ActiveTunnel> getTunnels() {
        return Collections.unmodifiableList(tunnels);
    }

    // -----------------------------------------------------------------------

    public static class ActiveTunnel {

        private final TunnelConfig config;
        private final SSHClient    ssh;
        private final ServerSocket serverSocket;
        private volatile boolean   stopped = false;

        ActiveTunnel(TunnelConfig config, SSHClient ssh,
                     ServerSocket serverSocket, LocalPortForwarder forwarder) {
            this.config       = config;
            this.ssh          = ssh;
            this.serverSocket = serverSocket;

            Thread t = new Thread(() -> {
                try {
                    forwarder.listen();
                } catch (Exception e) {
                    if (!stopped) {
                        System.err.println("Tunnel \"" + config.getLabel()
                                + "\" dropped: " + e.getMessage());
                    }
                }
                stopped = true;
            }, "tunnel-" + config.getLocalPort());
            t.setDaemon(true);
            t.start();
        }

        void stop() {
            stopped = true;
            try { serverSocket.close(); } catch (IOException ignored) {}
            try { ssh.disconnect();    } catch (IOException ignored) {}
        }

        public boolean     isRunning() { return !stopped && ssh.isConnected(); }
        public TunnelConfig getConfig() { return config; }
    }
}
