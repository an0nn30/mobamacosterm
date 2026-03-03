package com.mobamacos.ui.files;

import java.time.Instant;

/** Immutable representation of a single file or directory in a browser panel. */
public record FileEntry(
        String  name,
        boolean isDirectory,
        long    size,
        Instant modified
) {
    public String displaySize() {
        if (isDirectory) return "<DIR>";
        if (size < 1_024)              return size + " B";
        if (size < 1_048_576)          return String.format("%.1f KB", size / 1_024.0);
        if (size < 1_073_741_824L)     return String.format("%.1f MB", size / 1_048_576.0);
        return                                String.format("%.1f GB", size / 1_073_741_824.0);
    }
}
