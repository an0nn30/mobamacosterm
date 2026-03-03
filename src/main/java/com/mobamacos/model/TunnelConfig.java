package com.mobamacos.model;

/**
 * Immutable configuration for a local SSH port-forward tunnel:
 *   localhost:localPort  →  remoteHost:remotePort  (via server's SSH connection)
 */
public class TunnelConfig {

    private final ServerEntry server;
    private final int         localPort;
    private final String      remoteHost;
    private final int         remotePort;
    private final String      label;

    public TunnelConfig(ServerEntry server,
                        int localPort, String remoteHost, int remotePort,
                        String label) {
        this.server     = server;
        this.localPort  = localPort;
        this.remoteHost = remoteHost;
        this.remotePort = remotePort;
        this.label = (label != null && !label.isBlank())
                ? label.strip()
                : ":" + localPort + " \u2192 " + remoteHost + ":" + remotePort;
    }

    public ServerEntry getServer()     { return server; }
    public int         getLocalPort()  { return localPort; }
    public String      getRemoteHost() { return remoteHost; }
    public int         getRemotePort() { return remotePort; }
    public String      getLabel()      { return label; }
}
