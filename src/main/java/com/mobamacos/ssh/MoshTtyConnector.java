package com.mobamacos.ssh;

import com.jediterm.core.util.TermSize;
import com.jediterm.terminal.TtyConnector;
import com.mobamacos.model.ServerEntry;
import com.pty4j.PtyProcess;
import com.pty4j.PtyProcessBuilder;
import com.pty4j.WinSize;
import org.jetbrains.annotations.NotNull;

import java.io.*;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * TtyConnector that connects via the system {@code mosh} binary.
 *
 * Mosh is spawned as a pty4j PTY process so resize and keyboard input work
 * correctly.  The mosh client handles its own UDP transport; it uses SSH
 * only to bootstrap mosh-server on the remote host.
 *
 * Requirements:
 *   - {@code mosh} installed on the client  (macOS: brew install mosh)
 *   - {@code mosh-server} installed on the remote host
 *   - Not supported on Windows (no native mosh binary available)
 */
public class MoshTtyConnector implements TtyConnector {

    // -----------------------------------------------------------------------
    // CWD listener (same contract as SshjTtyConnector.CwdListener)
    // -----------------------------------------------------------------------

    public interface CwdListener {
        /** Called (off the EDT) whenever the remote shell reports a new CWD via OSC 7. */
        void cwdChanged(String absolutePath);
    }

    // -----------------------------------------------------------------------
    // Fields
    // -----------------------------------------------------------------------

    private final PtyProcess   pty;
    private final Reader       reader;
    private final OutputStream writer;
    private final String       name;

    private volatile CwdListener cwdListener;
    private final OscParser      oscParser = new OscParser();

    public MoshTtyConnector(ServerEntry server) throws IOException {
        this.name = server.getName() + "@" + server.getHost();

        String moshBin = findMosh();

        // Build the command:  mosh [--ssh="ssh OPTS"] user@host
        List<String> cmd = new ArrayList<>();
        cmd.add(moshBin);

        // --ssh: pass the user-supplied SSH options verbatim as a single argv element.
        // mosh receives the entire string and handles it; we must not split it.
        String sshOpts = server.getMoshSshOpts();
        if (sshOpts != null && !sshOpts.isBlank()) {
            cmd.add("--ssh=ssh " + sshOpts.trim());
        }

        // UDP port range override
        String moshPort = server.getMoshPort();
        if (moshPort != null && !moshPort.isBlank()) {
            cmd.add("--port=" + moshPort);
        }

        // Remote mosh-server path
        String moshServer = server.getMoshServerPath();
        if (moshServer != null && !moshServer.isBlank()) {
            cmd.add("--server=" + moshServer);
        }

        // Predict mode (skip if it's the default "adaptive" to keep the command clean)
        String predict = server.getMoshPredictMode();
        if (predict != null && !predict.isBlank() && !predict.equals("adaptive")) {
            cmd.add("--predict=" + predict);
        }

        // Raw extra args — split on whitespace and append before user@host
        String extraArgs = server.getMoshExtraArgs();
        if (extraArgs != null && !extraArgs.isBlank()) {
            for (String arg : extraArgs.trim().split("\\s+")) {
                cmd.add(arg);
            }
        }

        cmd.add(server.getUsername() + "@" + server.getHost());

        Map<String, String> env = new HashMap<>(System.getenv());
        env.put("TERM",      "xterm-256color");
        env.put("COLORTERM", "truecolor");

        pty = new PtyProcessBuilder(cmd.toArray(new String[0]))
                .setEnvironment(env)
                .setDirectory(System.getProperty("user.home"))
                .setInitialColumns(220)
                .setInitialRows(50)
                .start();

        reader = new InputStreamReader(pty.getInputStream(), StandardCharsets.UTF_8);
        writer = pty.getOutputStream();
        injectShellIntegration();
    }

    public void setCwdListener(CwdListener l) { this.cwdListener = l; }

    // -----------------------------------------------------------------------
    // Discovery helpers
    // -----------------------------------------------------------------------

    /**
     * Returns the absolute path of the mosh binary, or throws a descriptive
     * IOException listing every location that was searched.
     */
    public static String findMosh() throws IOException {
        if (System.getProperty("os.name", "").toLowerCase().contains("win")) {
            throw new IOException("Mosh is not available on Windows.");
        }

        // Use a LinkedHashSet so each path appears once and insertion order is kept.
        Set<String> searched = new LinkedHashSet<>();

        // Well-known hard-coded locations that GUI apps sometimes miss because
        // their $PATH doesn't include shell-profile additions.
        String[] fixed = {
            "/opt/homebrew/bin/mosh",    // macOS Apple Silicon (Homebrew)
            "/usr/local/bin/mosh",       // macOS Intel (Homebrew) / manual install
            "/usr/bin/mosh",             // Linux — apt / dnf / pacman
            "/usr/local/sbin/mosh",      // some BSD / custom installs
        };
        for (String path : fixed) {
            searched.add(path);
            if (new File(path).canExecute()) return path;
        }

        // Walk every directory in $PATH
        String pathEnv = System.getenv("PATH");
        if (pathEnv != null) {
            for (String dir : pathEnv.split(File.pathSeparator)) {
                File candidate = new File(dir, "mosh");
                if (searched.add(candidate.getAbsolutePath()) && candidate.canExecute()) {
                    return candidate.getAbsolutePath();
                }
            }
        }

        StringBuilder sb = new StringBuilder("mosh not found. Searched:\n");
        for (String p : searched) sb.append("  ").append(p).append('\n');
        sb.append("\nInstall with:\n")
          .append("  macOS:  brew install mosh\n")
          .append("  Linux:  sudo apt install mosh  (or your distro's package manager)\n")
          .append("\nAlso ensure mosh-server is installed on the remote host.");
        throw new IOException(sb.toString());
    }

    /** Returns true if mosh is available on this system. */
    public static boolean isAvailable() {
        try { findMosh(); return true; }
        catch (IOException e) { return false; }
    }

    // -----------------------------------------------------------------------
    // TtyConnector
    // -----------------------------------------------------------------------

    @Override
    public int read(char[] buf, int offset, int length) throws IOException {
        int n = reader.read(buf, offset, length);
        if (n > 0) oscParser.process(buf, offset, n);
        return n;
    }
    @Override public boolean ready()       throws IOException  { return reader.ready(); }
    @Override public boolean isConnected()                     { return pty.isAlive(); }
    @Override public String  getName()                         { return name; }

    @Override
    public void write(byte[] bytes) throws IOException {
        writer.write(bytes);
        writer.flush();
    }

    @Override
    public void write(String s) throws IOException { write(s.getBytes(StandardCharsets.UTF_8)); }

    @Override
    public void resize(@NotNull TermSize termSize) {
        pty.setWinSize(new WinSize(termSize.getColumns(), termSize.getRows()));
    }

    @Override
    public int waitFor() throws InterruptedException { return pty.waitFor(); }

    @Override
    public void close() { pty.destroy(); }

    // -----------------------------------------------------------------------
    // Shell integration injection
    // -----------------------------------------------------------------------

    /**
     * Injects a PROMPT_COMMAND / precmd hook so that bash and zsh emit an
     * OSC 7 sequence on every prompt — identical to the injection in
     * SshjTtyConnector.  Mosh passes OSC 7 sequences transparently.
     *
     * Sent 800 ms after construction (mosh needs slightly longer than a direct
     * SSH session to finish bootstrapping the remote shell).
     */
    private void injectShellIntegration() {
        Thread t = new Thread(() -> {
            try {
                Thread.sleep(800);
                String cmd =
                    " __mc(){ printf '\\033]7;file://%s\\007' \"$PWD\"; };" +
                    " [ -n \"$BASH_VERSION\" ] && PROMPT_COMMAND=\"${PROMPT_COMMAND:+$PROMPT_COMMAND;}__mc\";" +
                    " [ -n \"$ZSH_VERSION\" ]  && precmd_functions+=(__mc);" +
                    " __mc\r";
                write(cmd.getBytes(StandardCharsets.UTF_8));
            } catch (Exception ignored) {}
        }, "mosh-shell-integration-injector");
        t.setDaemon(true);
        t.start();
    }

    // -----------------------------------------------------------------------
    // OSC 7 state machine
    // -----------------------------------------------------------------------

    private class OscParser {
        private enum State { NORMAL, ESC, OSC_NUM, OSC_7_DATA, ST_WAIT }

        private State         state  = State.NORMAL;
        private int           oscNum = 0;
        private StringBuilder buf    = new StringBuilder();

        void process(char[] data, int off, int len) {
            for (int i = off; i < off + len; i++) {
                char c = data[i];
                switch (state) {
                    case NORMAL   -> { if (c == '\u001B') state = State.ESC; }
                    case ESC      -> {
                        if (c == ']') { state = State.OSC_NUM; oscNum = 0; buf.setLength(0); }
                        else           state = State.NORMAL;
                    }
                    case OSC_NUM  -> {
                        if (c >= '0' && c <= '9') { oscNum = oscNum * 10 + (c - '0'); }
                        else if (c == ';') {
                            if (oscNum == 7) state = State.OSC_7_DATA;
                            else             state = State.NORMAL;
                        } else state = State.NORMAL;
                    }
                    case OSC_7_DATA -> {
                        if (c == '\u0007') { fire(buf.toString()); state = State.NORMAL; }
                        else if (c == '\u001B') state = State.ST_WAIT;
                        else buf.append(c);
                    }
                    case ST_WAIT -> {
                        if (c == '\\') fire(buf.toString());
                        state = State.NORMAL;
                    }
                }
            }
        }

        private void fire(String url) {
            CwdListener l = cwdListener;
            if (l == null || !url.startsWith("file://")) return;
            String rest = url.substring(7);
            String path = rest.startsWith("/") ? rest
                        : rest.contains("/") ? rest.substring(rest.indexOf('/'))
                        : null;
            if (path == null || path.isBlank()) return;
            try { path = URLDecoder.decode(path, StandardCharsets.UTF_8); }
            catch (Exception ignored) {}
            l.cwdChanged(path);
        }
    }
}
