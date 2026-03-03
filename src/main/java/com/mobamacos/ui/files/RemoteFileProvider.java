package com.mobamacos.ui.files;

import com.mobamacos.model.ServerEntry;
import com.mobamacos.ssh.SshSessionManager;
import net.schmizz.sshj.SSHClient;
import net.schmizz.sshj.common.StreamCopier;
import net.schmizz.sshj.sftp.RemoteResourceInfo;
import net.schmizz.sshj.sftp.SFTPClient;
import net.schmizz.sshj.xfer.FileSystemFile;
import net.schmizz.sshj.xfer.TransferListener;

import java.io.Closeable;
import java.io.File;
import java.io.IOException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * FileProvider backed by an SSHJ SFTP connection.
 * Must be created off the EDT (connectAndAuth blocks).
 */
public class RemoteFileProvider implements FileProvider, Closeable {

    private final ServerEntry server;
    private final SSHClient   ssh;
    private final SFTPClient  sftp;
    private final String      homePath;

    public RemoteFileProvider(ServerEntry server) throws Exception {
        this.server = server;
        SshSessionManager mgr = new SshSessionManager();
        ssh  = mgr.connectAndAuth(server);
        sftp = ssh.newSFTPClient();
        String h;
        try { h = sftp.canonicalize("."); } catch (Exception e) { h = "/home/" + server.getUsername(); }
        homePath = h;
    }

    @Override
    public List<FileEntry> list(String path) throws IOException {
        List<RemoteResourceInfo> infos = sftp.ls(path);
        List<FileEntry> entries = new ArrayList<>();
        for (RemoteResourceInfo info : infos) {
            if (".".equals(info.getName()) || "..".equals(info.getName())) continue;
            entries.add(new FileEntry(
                    info.getName(),
                    info.isDirectory(),
                    info.getAttributes().getSize(),
                    Instant.ofEpochSecond(info.getAttributes().getMtime())));
        }
        entries.sort(Comparator.comparing(FileEntry::isDirectory).reversed()
                               .thenComparing(e -> e.name().toLowerCase()));
        return entries;
    }

    @Override
    public void download(String remotePath, File localDestDir) throws IOException {
        download(remotePath, localDestDir, null);
    }

    @Override
    public void download(String remotePath, File localDestDir, ProgressListener progress) throws IOException {
        String name = remotePath.substring(remotePath.lastIndexOf('/') + 1);
        sftp.getFileTransfer().setTransferListener(listenerFor(progress));
        sftp.get(remotePath, new FileSystemFile(new File(localDestDir, name).getAbsolutePath()));
    }

    @Override
    public void upload(File localFile, String remoteDestDir) throws IOException {
        upload(localFile, remoteDestDir, null);
    }

    @Override
    public void upload(File localFile, String remoteDestDir, ProgressListener progress) throws IOException {
        String destPath = remoteDestDir.endsWith("/")
                ? remoteDestDir + localFile.getName()
                : remoteDestDir + "/" + localFile.getName();
        sftp.getFileTransfer().setTransferListener(listenerFor(progress));
        sftp.put(new FileSystemFile(localFile.getAbsolutePath()), destPath);
    }

    /** Wraps a {@link ProgressListener} in SSHJ's {@link TransferListener}. */
    private static TransferListener listenerFor(ProgressListener progress) {
        return new TransferListener() {
            @Override
            public TransferListener directory(String name) { return this; }

            @Override
            public StreamCopier.Listener file(String name, long size) {
                return transferred -> {
                    if (progress != null) progress.onProgress(name, transferred, size);
                };
            }
        };
    }

    @Override
    public void mkdir(String path) throws IOException {
        sftp.mkdir(path);
    }

    @Override public String  getHomePath()    { return homePath; }
    @Override public String  getSeparator()   { return "/"; }
    @Override public boolean isRemote()       { return true; }
    @Override public String  getDisplayName() { return server.getUsername() + "@" + server.getHost(); }

    @Override
    public void close() throws IOException {
        try { sftp.close();     } catch (Exception ignored) {}
        try { ssh.disconnect(); } catch (Exception ignored) {}
    }
}
