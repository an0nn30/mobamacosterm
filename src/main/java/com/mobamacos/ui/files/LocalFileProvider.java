package com.mobamacos.ui.files;

import java.io.File;
import java.io.IOException;
import java.nio.file.*;
import java.nio.file.attribute.BasicFileAttributes;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

public class LocalFileProvider implements FileProvider {

    @Override
    public List<FileEntry> list(String path) throws IOException {
        List<FileEntry> entries = new ArrayList<>();
        try (DirectoryStream<Path> ds = Files.newDirectoryStream(Paths.get(path))) {
            for (Path p : ds) {
                try {
                    BasicFileAttributes attrs = Files.readAttributes(p, BasicFileAttributes.class);
                    entries.add(new FileEntry(
                            p.getFileName().toString(),
                            attrs.isDirectory(),
                            attrs.size(),
                            attrs.lastModifiedTime().toInstant()));
                } catch (IOException ignored) {}
            }
        }
        entries.sort(Comparator.comparing(FileEntry::isDirectory).reversed()
                               .thenComparing(e -> e.name().toLowerCase()));
        return entries;
    }

    @Override
    public void download(String fromPath, File toLocalDir) throws IOException {
        Path src = Paths.get(fromPath);
        Files.copy(src, toLocalDir.toPath().resolve(src.getFileName()),
                StandardCopyOption.REPLACE_EXISTING);
    }

    @Override
    public void upload(File localFile, String destDirPath) throws IOException {
        Files.copy(localFile.toPath(),
                Paths.get(destDirPath, localFile.getName()),
                StandardCopyOption.REPLACE_EXISTING);
    }

    @Override
    public void mkdir(String path) throws IOException {
        Files.createDirectory(Paths.get(path));
    }

    @Override public String  getHomePath()    { return System.getProperty("user.home"); }
    @Override public String  getSeparator()   { return File.separator; }
    @Override public boolean isRemote()       { return false; }
    @Override public String  getDisplayName() { return "Local"; }
}
