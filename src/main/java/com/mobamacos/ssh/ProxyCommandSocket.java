package com.mobamacos.ssh;

import java.io.*;
import java.net.Socket;
import java.net.SocketAddress;

/**
 * A Socket that, when connect() is called, spawns a ProxyCommand process
 * (e.g. "cloudflared access ssh --hostname %h") and uses its stdin/stdout
 * as the SSH transport — identical to how OpenSSH handles ProxyCommand.
 *
 * Usage in SSHJ:
 * <pre>
 *   ssh.setSocketFactory(new ProxyCommandSocketFactory(expandedCmd));
 *   ssh.connect(host, port);   // SSHJ calls createSocket() then socket.connect()
 * </pre>
 */
public class ProxyCommandSocket extends Socket {

    private final String command;
    private Process      process;
    private InputStream  inputStream;
    private OutputStream outputStream;
    private boolean      connected = false;

    /** Stores the command; process is not started until connect() is called. */
    public ProxyCommandSocket(String command) {
        this.command = command;
    }

    // SSHJ calls connect(addr, timeout) after createSocket() — this is where we start the process.
    @Override
    public void connect(SocketAddress endpoint) throws IOException {
        startProcess();
    }

    @Override
    public void connect(SocketAddress endpoint, int timeout) throws IOException {
        startProcess();
    }

    private static final boolean WINDOWS =
            System.getProperty("os.name", "").toLowerCase().contains("win");

    private void startProcess() throws IOException {
        if (connected) return;

        ProcessBuilder pb = WINDOWS
                ? new ProcessBuilder("cmd.exe", "/c", command)
                : new ProcessBuilder("/bin/sh", "-c", command);

        if (!WINDOWS) {
            // Augment PATH so tools like cloudflared installed via Homebrew are found
            // even when the JVM was launched from a GUI context (Dock, IDE) with a
            // stripped-down PATH that omits /opt/homebrew/bin etc.
            pb.environment().merge("PATH",
                    "/opt/homebrew/bin:/opt/homebrew/sbin:/usr/local/bin:/usr/local/sbin",
                    (existing, extra) -> extra + ":" + existing);
        }

        pb.redirectErrorStream(false);
        process = pb.start();

        // CRITICAL: drain stderr in a background daemon thread.
        // cloudflared writes status/auth messages to stderr.  If nobody reads
        // that pipe the OS buffer fills up (~64 KB on macOS), cloudflared blocks
        // mid-write, and nothing ever appears on stdout — SSHJ hangs waiting for
        // the SSH banner.  Routing to System.err also lets you see what went wrong.
        final InputStream stderr = process.getErrorStream();
        Thread errDrainer = new Thread(() -> {
            try {
                byte[] buf = new byte[2048];
                int n;
                while ((n = stderr.read(buf)) != -1) {
                    System.err.write(buf, 0, n);
                }
            } catch (IOException ignored) {}
        }, "proxy-stderr-drain");
        errDrainer.setDaemon(true);
        errDrainer.start();

        inputStream  = process.getInputStream();
        outputStream = process.getOutputStream();
        connected    = true;
    }

    @Override public InputStream  getInputStream()  throws IOException { return inputStream;  }
    @Override public OutputStream getOutputStream() throws IOException { return outputStream; }

    @Override public boolean isConnected() { return connected && (process == null || process.isAlive()); }
    @Override public boolean isClosed()    { return !isConnected(); }

    // Socket options are meaningless for a process pipe — suppress silently.
    @Override public void setSoTimeout(int t)          throws java.net.SocketException {}
    @Override public void setTcpNoDelay(boolean on)    throws java.net.SocketException {}
    @Override public void setKeepAlive(boolean on)     throws java.net.SocketException {}
    @Override public void setSendBufferSize(int size)  throws java.net.SocketException {}
    @Override public void setReceiveBufferSize(int s)  throws java.net.SocketException {}

    @Override
    public void close() throws IOException {
        connected = false;
        if (inputStream  != null) try { inputStream.close();  } catch (IOException ignored) {}
        if (outputStream != null) try { outputStream.close(); } catch (IOException ignored) {}
        if (process      != null) process.destroy();
    }
}
