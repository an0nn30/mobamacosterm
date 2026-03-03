package com.mobamacos.ui.files;

import java.io.File;
import java.io.IOException;
import java.util.List;

public interface FileProvider {

    /**
     * Receives per-file progress callbacks during a transfer.
     * All callbacks are issued on the transfer (background) thread —
     * callers must dispatch to the EDT if they touch Swing components.
     *
     * @param fileName   the name of the file currently being transferred
     * @param transferred bytes transferred so far for this file
     * @param total       total size of this file (-1 if unknown)
     */
    @FunctionalInterface
    interface ProgressListener {
        void onProgress(String fileName, long transferred, long total);
    }

    List<FileEntry> list(String path) throws IOException;

    void   download(String remotePath, File localDestDir) throws IOException;
    void   upload  (File localFile,    String remoteDirPath) throws IOException;
    void   mkdir   (String path) throws IOException;

    /** Download with per-file progress callbacks. Default delegates to the basic method. */
    default void download(String remotePath, File localDestDir, ProgressListener progress) throws IOException {
        download(remotePath, localDestDir);
    }

    /** Upload with per-file progress callbacks. Default delegates to the basic method. */
    default void upload(File localFile, String remoteDirPath, ProgressListener progress) throws IOException {
        upload(localFile, remoteDirPath);
    }

    String  getHomePath();
    String  getSeparator();
    boolean isRemote();
    String  getDisplayName();
}
