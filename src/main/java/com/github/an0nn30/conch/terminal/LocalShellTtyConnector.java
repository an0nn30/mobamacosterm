package com.github.an0nn30.conch.terminal;

import com.jediterm.core.util.TermSize;
import com.jediterm.terminal.TtyConnector;
import com.pty4j.PtyProcess;
import com.pty4j.PtyProcessBuilder;
import com.pty4j.WinSize;
import org.jetbrains.annotations.NotNull;

import java.io.*;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;

/**
 * TtyConnector that opens a proper local PTY shell (zsh/bash) via pty4j.
 * Supports resize, readline, job-control, colours — everything a real terminal needs.
 *
 * Also injects a shell integration snippet on startup and parses OSC 7 sequences
 * so the file browser can follow the shell's working directory.
 */
public class LocalShellTtyConnector implements TtyConnector {

    public interface CwdListener {
        void cwdChanged(String absolutePath);
    }

    private final PtyProcess   pty;
    private final Reader       reader;
    private final OutputStream writer;

    private volatile CwdListener cwdListener;
    private final OscParser      oscParser = new OscParser();

    public LocalShellTtyConnector() throws IOException {
        boolean windows = System.getProperty("os.name", "").toLowerCase().contains("win");

        Map<String, String> env = new HashMap<>(System.getenv());
        String[] cmd;

        if (windows) {
            cmd = new String[]{"powershell.exe", "-NoExit", "-NoLogo"};
        } else {
            String shell = System.getenv("SHELL");
            if (shell == null || shell.isBlank()) shell = "/bin/zsh";

            env.put("TERM",      "xterm-256color");
            env.put("COLORTERM", "truecolor");

            cmd = (shell.endsWith("bash") || shell.endsWith("zsh"))
                    ? new String[]{shell, "--login"}
                    : new String[]{shell};
        }

        pty = new PtyProcessBuilder(cmd)
                .setEnvironment(env)
                .setDirectory(System.getProperty("user.home"))
                .setInitialColumns(220)
                .setInitialRows(50)
                .start();

        reader = new InputStreamReader(pty.getInputStream(), StandardCharsets.UTF_8);
        writer = pty.getOutputStream();

        if (!windows) injectShellIntegration();
    }

    public void setCwdListener(CwdListener l) { this.cwdListener = l; }

    @Override
    public int read(char[] buf, int offset, int length) throws IOException {
        int n = reader.read(buf, offset, length);
        if (n > 0) oscParser.process(buf, offset, n);
        return n;
    }

    @Override public boolean ready()       throws IOException  { return reader.ready(); }
    @Override public boolean isConnected()                     { return pty.isAlive(); }
    @Override public String  getName()                         { return "Local Terminal"; }

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
    // Shell integration injection — identical snippet to SshjTtyConnector
    // -----------------------------------------------------------------------

    private void injectShellIntegration() {
        Thread t = new Thread(() -> {
            try {
                Thread.sleep(300);
                write("stty -echo\r");
                Thread.sleep(100);
                String cmd =
                    " __mc(){ printf '\\033]7;file://%s\\007' \"$PWD\"; };" +
                    " [ -n \"$BASH_VERSION\" ] && PROMPT_COMMAND=\"${PROMPT_COMMAND:+$PROMPT_COMMAND;}__mc\";" +
                    " [ -n \"$ZSH_VERSION\" ]  && precmd_functions+=(__mc);" +
                    " __mc;" +
                    " stty echo;" +
                    " printf '\\033[2K\\033[1A\\033[2K\\r'\r";
                write(cmd);
            } catch (Exception ignored) {}
        }, "local-shell-integration-injector");
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
