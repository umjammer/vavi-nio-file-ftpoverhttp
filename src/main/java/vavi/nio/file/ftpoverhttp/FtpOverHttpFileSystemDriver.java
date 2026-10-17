/*
 * Copyright (c) 2016 by Naohide Sano, All rights reserved.
 *
 * Programmed by Naohide Sano
 */

package vavi.nio.file.ftpoverhttp;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.CopyOption;
import java.nio.file.FileStore;
import java.nio.file.NoSuchFileException;
import java.nio.file.OpenOption;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.Calendar;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.logging.Level;
import java.util.stream.Collectors;

import javax.annotation.ParametersAreNonnullByDefault;

import com.github.fge.filesystem.driver.ExtendedFileSystemDriver;
import com.github.fge.filesystem.provider.FileSystemFactoryProvider;

import org.apache.commons.net.ftp.FTPClient;
import org.apache.commons.net.ftp.FTPFile;

import vavi.nio.file.Util;
import vavi.util.Debug;

import static vavi.nio.file.Util.isAppleDouble;
import static vavi.nio.file.Util.toFilenameString;
import static vavi.nio.file.Util.toPathString;


/**
 * FtpOverHttpFileSystemDriver.
 *
 * @author <a href="mailto:umjammer@gmail.com">Naohide Sano</a> (umjammer)
 * @version 0.00 2016/03/30 umjammer initial version <br>
 */
@ParametersAreNonnullByDefault
public final class FtpOverHttpFileSystemDriver extends ExtendedFileSystemDriver<FTPFile> {

    private final FTPClient client;

    private final String baseUrl;

    /**
     * @param env { "ignoreAppleDouble": boolean }
     */
    public FtpOverHttpFileSystemDriver(FileStore fileStore,
                                       FileSystemFactoryProvider provider,
                                       FTPClient client,
                                       String baseUrl,
                                       Map<String, ?> env) throws IOException {

        super(fileStore, provider);

        setEnv(env);

        this.client = client;

        this.baseUrl = baseUrl;
    }

    /** Resolves a NIO path to an FTP path string relative to {@link #baseUrl}. */
    private String toFtpPath(Path path) throws IOException {
        String p = toPathString(path);
        if (baseUrl == null || baseUrl.isEmpty() || baseUrl.equals("/")) {
            return p;
        }
        String base = baseUrl.endsWith("/") ? baseUrl.substring(0, baseUrl.length() - 1) : baseUrl;
        return p.equals("/") ? base : base + p;
    }

    @Override
    protected String getFilenameString(FTPFile entry) {
        return entry.getName();
    }

    @Override
    protected boolean isFolder(FTPFile entry) throws IOException {
        return entry.isDirectory();
    }

    @Override
    protected boolean exists(FTPFile entry) throws IOException {
        return entry != null && entry.isValid();
    }

    // FTPFile might have cache?
    @Override
    protected FTPFile getEntry(Path path) throws IOException {
        return getEntry(path, true);
    }

    /**
     * @param check check existence of the path
     */
    private FTPFile getEntry(Path path, boolean check) throws IOException {
//Debug.println(Level.FINE, "path: " + path);
        if (ignoreAppleDouble && path.getFileName() != null && isAppleDouble(path)) {
            throw new NoSuchFileException("ignore apple double file: " + path);
        }

        if (path.getNameCount() == 0) {
            FTPFile root = new FTPFile();
            root.setName("/");
            root.setType(FTPFile.DIRECTORY_TYPE);
            root.setTimestamp(Calendar.getInstance());
            return root;
        }

        String filename = toFilenameString(path);
        if (!check) {
            FTPFile entry = new FTPFile();
            entry.setName(filename);
            entry.setType(FTPFile.FILE_TYPE);
            entry.setTimestamp(Calendar.getInstance());
            return entry;
        }

        Path parent = path.toAbsolutePath().getParent();
        String parentFtpPath = toFtpPath(parent);
        FTPFile[] files;
        synchronized (client) {
            files = client.listFiles(parentFtpPath);
        }
        if (files != null) {
            for (FTPFile entry : files) {
                if (entry != null && filename.equals(entry.getName())) {
Debug.println(Level.FINE, "entry: " + entry + ", true");
                    return entry;
                }
            }
        }
        throw new NoSuchFileException(path.toString());
    }

    /**
     * the whole content is retrieved at once, because while a data transfer is pending
     * the control connection cannot accept other commands (e.g. LIST from a fuse getattr),
     * that causes a deadlock.
     */
    @Override
    protected InputStream downloadEntry(FTPFile entry, Path path, Set<? extends OpenOption> options) throws IOException {
        String ftpPath = toFtpPath(path);
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        synchronized (client) {
            if (!client.retrieveFile(ftpPath, baos)) {
                throw new NoSuchFileException(path + " (" + client.getReplyString() + ")");
            }
        }
        return new ByteArrayInputStream(baos.toByteArray());
    }

    /**
     * the content is buffered and stored at closing, because while a data transfer is pending
     * the control connection cannot accept other commands (e.g. LIST from a fuse getattr),
     * that causes a deadlock.
     */
    @Override
    protected OutputStream uploadEntry(FTPFile parentEntry, Path path, Set<? extends OpenOption> options) throws IOException {
        String ftpPath = toFtpPath(path);
        return new Util.OutputStreamForUploading() {
            @Override
            protected void onClosed() throws IOException {
                synchronized (client) {
                    if (!client.storeFile(ftpPath, getInputStream())) {
                        throw new IOException("storeFile failed: " + path + " (" + client.getReplyString() + ")");
                    }
                }
            }
        };
    }

    /** */
    @Override
    protected List<FTPFile> getDirectoryEntries(FTPFile dirEntry, Path dir) throws IOException {
//System.err.println("path: " + dir);
//Arrays.stream(dirEntry.getChildren()).forEach(System.err::println);
        String ftpPath = toFtpPath(dir);
        FTPFile[] files;
        synchronized (client) {
            files = client.listFiles(ftpPath);
        }
        if (files == null) {
            return Collections.emptyList();
        }
        return Arrays.stream(files)
                .filter(f -> f != null && !f.getName().equals(".") && !f.getName().equals(".."))
                .collect(Collectors.toList());
    }

    @Override
    protected FTPFile createDirectoryEntry(FTPFile parentEntry, Path dir) throws IOException {
        String ftpPath = toFtpPath(dir);
        synchronized (client) {
            if (!client.makeDirectory(ftpPath)) {
                throw new IOException("makeDirectory failed: " + dir + " (" + client.getReplyString() + ")");
            }
        }
        FTPFile dirEntry = getEntry(dir, false);
        dirEntry.setType(FTPFile.DIRECTORY_TYPE);
        return dirEntry;
    }

    @Override
    protected boolean hasChildren(FTPFile dirEntry, Path dir) throws IOException {
        return !getDirectoryEntries(dirEntry, dir).isEmpty();
    }

    @Override
    protected void removeEntry(FTPFile entry, Path path) throws IOException {
        String ftpPath = toFtpPath(path);
        synchronized (client) {
            boolean ok = entry.isDirectory() ? client.removeDirectory(ftpPath) : client.deleteFile(ftpPath);
            if (!ok) {
                throw new IOException("remove failed: " + path + " (" + client.getReplyString() + ")");
            }
        }
    }

    @Override
    protected FTPFile copyEntry(FTPFile sourceEntry, FTPFile targetParentEntry, Path source, Path target, Set<CopyOption> options) throws IOException {
        String sourceFtpPath = toFtpPath(source);
        String targetFtpPath = toFtpPath(target);
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        synchronized (client) {
            if (!client.retrieveFile(sourceFtpPath, baos)) {
                throw new IOException("retrieveFile failed: " + source + " (" + client.getReplyString() + ")");
            }
            if (!client.storeFile(targetFtpPath, new ByteArrayInputStream(baos.toByteArray()))) {
                throw new IOException("storeFile failed: " + target + " (" + client.getReplyString() + ")");
            }
        }
        return getEntry(target, false);
    }

    @Override
    protected FTPFile moveEntry(FTPFile sourceEntry, FTPFile targetParentEntry, Path source, Path target, boolean targetIsParent) throws IOException {
        String sourceFtpPath = toFtpPath(source);
        String targetFtpPath = toFtpPath(target);
        synchronized (client) {
            if (!client.rename(sourceFtpPath, targetFtpPath)) {
                throw new IOException("rename failed: " + source + " -> " + target + " (" + client.getReplyString() + ")");
            }
        }
        return getEntry(target, false);
    }

    @Override
    protected FTPFile moveFolderEntry(FTPFile sourceEntry, FTPFile targetParentEntry, Path source, Path target, boolean targetIsParent) throws IOException {
        return moveEntry(sourceEntry, targetParentEntry, source, target, targetIsParent);
    }

    @Override
    protected FTPFile renameEntry(FTPFile sourceEntry, FTPFile targetParentEntry, Path source, Path target) throws IOException {
        return moveEntry(sourceEntry, targetParentEntry, source, target, false);
    }

    @Override
    public void close() throws IOException {
        synchronized (client) {
            if (client.isConnected()) {
                try {
                    client.logout();
                } finally {
                    client.disconnect();
                }
            }
        }
    }
}
