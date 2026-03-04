package com.github.an0nn30.conch.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.util.UUID;

@JsonIgnoreProperties(ignoreUnknown = true)
public class ServerEntry {
    private String id;
    private String name;
    private String host;
    private int port = 22;
    private String username;
    private String password = "";
    private String privateKeyPath = "";
    /** ProxyCommand, e.g. "cloudflared access ssh --hostname %h" or "ssh -W %h:%p bastion" */
    private String proxyCommand = "";
    /** ProxyJump host, e.g. "user@bastion.example.com" or "bastion:2222" */
    private String proxyJump = "";
    /** True when imported from ~/.ssh/config — never persisted back to that file */
    private boolean fromSshConfig = false;
    /** Connect via the system mosh binary instead of SSHJ */
    private boolean useMosh = false;
    /** SSH options passed verbatim as --ssh="ssh <value>", e.g. "-i ~/.ssh/id_ed25519 -oProxyCommand=...". Empty = plain ssh. */
    private String moshSshOpts = "";
    /** Override mosh UDP port / range, e.g. "60001" or "60001:60010". Empty = default. */
    private String moshPort = "";
    /** Path to mosh-server on the remote host. Empty = let mosh find it in PATH. */
    private String moshServerPath = "";
    /** Mosh predict mode: adaptive (default), always, never, experimental. */
    private String moshPredictMode = "adaptive";
    /** Raw extra arguments appended verbatim to the mosh command line. */
    private String moshExtraArgs = "";
    /**
     * ProxyCommand used exclusively for SSHJ SFTP/file-transfer connections when
     * the session transport is mosh.  Mosh's UDP transport and the SFTP TCP
     * connection are independent; this lets the user specify a separate proxy
     * (e.g. "cloudflared access ssh --hostname %h") for file-transfer while
     * mosh's --ssh flag handles the bootstrap proxy.
     */
    private String moshSftpProxyCommand = "";
    /** Command sent to the shell automatically after session start (SSH and Mosh only). */
    private String startupCommand = "";

    public ServerEntry() {
        this.id = UUID.randomUUID().toString();
    }

    public ServerEntry(String name, String host, int port, String username) {
        this();
        this.name = name;
        this.host = host;
        this.port = port;
        this.username = username;
    }

    public String getId()                      { return id; }
    public void   setId(String id)             { this.id = id; }
    public String getName()                    { return name; }
    public void   setName(String name)         { this.name = name; }
    public String getHost()                    { return host; }
    public void   setHost(String host)         { this.host = host; }
    public int    getPort()                    { return port; }
    public void   setPort(int port)            { this.port = port; }
    public String getUsername()                { return username; }
    public void   setUsername(String username) { this.username = username; }
    public String getPassword()                { return password; }
    public void   setPassword(String password) { this.password = password; }
    public String getPrivateKeyPath()          { return privateKeyPath; }
    public void   setPrivateKeyPath(String p)  { this.privateKeyPath = p; }
    public String getProxyCommand()              { return proxyCommand; }
    public void   setProxyCommand(String cmd)   { this.proxyCommand = cmd; }
    public String getProxyJump()                { return proxyJump; }
    public void   setProxyJump(String jump)     { this.proxyJump = jump; }
    public boolean isFromSshConfig()            { return fromSshConfig; }
    public void    setFromSshConfig(boolean b)  { this.fromSshConfig = b; }
    public boolean isUseMosh()                       { return useMosh; }
    public void    setUseMosh(boolean b)             { this.useMosh = b; }
    public String  getMoshSshOpts()                  { return moshSshOpts; }
    public void    setMoshSshOpts(String s)          { this.moshSshOpts = s; }
    public String  getMoshPort()                     { return moshPort; }
    public void    setMoshPort(String p)             { this.moshPort = p; }
    public String  getMoshServerPath()               { return moshServerPath; }
    public void    setMoshServerPath(String p)       { this.moshServerPath = p; }
    public String  getMoshPredictMode()              { return moshPredictMode; }
    public void    setMoshPredictMode(String m)      { this.moshPredictMode = m; }
    public String  getMoshExtraArgs()                { return moshExtraArgs; }
    public void    setMoshExtraArgs(String a)        { this.moshExtraArgs = a; }
    public String  getMoshSftpProxyCommand()         { return moshSftpProxyCommand; }
    public void    setMoshSftpProxyCommand(String c) { this.moshSftpProxyCommand = c; }
    public String  getStartupCommand()               { return startupCommand; }
    public void    setStartupCommand(String cmd)     { this.startupCommand = cmd; }

    @Override
    public String toString() {
        return name + " (" + host + ":" + port + ")";
    }
}
