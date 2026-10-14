/*
 * Copyright (c) 2016 by Naohide Sano, All rights reserved.
 *
 * Programmed by Naohide Sano
 */

package vavi.nio.file.ftpovergttp;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.CopyOption;
import java.nio.file.FileStore;
import java.nio.file.NoSuchFileException;
import java.nio.file.OpenOption;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.logging.Level;
import java.util.stream.Collectors;

import javax.annotation.ParametersAreNonnullByDefault;

import com.github.fge.filesystem.driver.ExtendedFileSystemDriver;
import com.github.fge.filesystem.provider.FileSystemFactoryProvider;

import vavi.util.Debug;

import static vavi.nio.file.Util.isAppleDouble;
import static vavi.nio.file.Util.toPathString;

import net.java.ftpoverhttp.access.TConfig;
import net.java.ftpoverhttp.access.TFile;
import net.java.ftpoverhttp.access.TFileInputStream;
import net.java.ftpoverhttp.access.TFileOutputStream;


/**
 * FtpOverHttpFileSystemDriver.
 *
 * @author <a href="mailto:umjammer@gmail.com">Naohide Sano</a> (umjammer)
 * @version 0.00 2016/03/30 umjammer initial version <br>
 */
@ParametersAreNonnullByDefault
public final class FtpOverHttpFileSystemDriver extends ExtendedFileSystemDriver<TFile> {

    private final TConfig config;

    private final String baseUrl;

    /**
     * @param env { "baseUrl": "smb://10.3.1.1/Temporary Share/", "ignoreAppleDouble": boolean }
     */
    public FtpOverHttpFileSystemDriver(FileStore fileStore,
                                       FileSystemFactoryProvider provider,
                                       TConfig config,
                                       String baseUrl,
                                       Map<String, ?> env) throws IOException {

        super(fileStore, provider);

        setEnv(env);

        this.config = config;

        this.baseUrl = baseUrl;
    }

    @Override
    protected String getFilenameString(TFile entry) {
        return entry.getName();
    }

    @Override
    protected boolean isFolder(TFile entry) throws IOException {
        return entry.isDirectory();
    }

    @Override
    protected boolean exists(TFile entry) throws IOException {
        return entry.exists();
    }

    // TrueVFS might have cache?
    @Override
    protected TFile getEntry(Path path) throws IOException {
        return getEntry(path, true);
    }

    /**
     * @param check check existence of the path
     */
    private TFile getEntry(Path path, boolean check) throws IOException {
//Debug.println(Level.FINE, "path: " + path);
        if (ignoreAppleDouble && path.getFileName() != null && isAppleDouble(path)) {
            throw new NoSuchFileException("ignore apple double file: " + path);
        }

        TFile entry = new TFile(baseUrl + toPathString(path));
Debug.println(Level.FINE, "entry: " + entry + ", " + entry.exists());
        if (check) {
            if (entry.exists()) {
                return entry;
            } else {
                throw new NoSuchFileException(path.toString());
            }
        } else {
            return entry;
        }
    }

    @Override
    protected InputStream downloadEntry(TFile entry, Path path, Set<? extends OpenOption> options) throws IOException {
        return new TFileInputStream(entry);
    }

    @Override
    protected OutputStream uploadEntry(TFile parentEntry, Path path, Set<? extends OpenOption> options) throws IOException {
        TFile targetEntry = getEntry(path, false);
        targetEntry.createNewFile();
        return new TFileOutputStream(targetEntry);
    }

    /** */
    protected List<TFile> getDirectoryEntries(TFile dirEntry, Path dir) throws IOException {
//System.err.println("path: " + dir);
//Arrays.stream(dirEntry.getChildren()).forEach(System.err::println);
        return Arrays.stream(dirEntry.listFiles()).collect(Collectors.toList());
    }

    @Override
    protected TFile createDirectoryEntry(TFile parentEntry, Path dir) throws IOException {
        TFile dirEntry = getEntry(dir, false);
        dirEntry.createNewFile();
        return dirEntry;
    }

    @Override
    protected boolean hasChildren(TFile dirEntry, Path dir) throws IOException {
        return dirEntry.listFiles().length > 0;
    }

    @Override
    protected void removeEntry(TFile entry, Path path) throws IOException {
        TFile.rm(entry);
    }

    @Override
    protected TFile copyEntry(TFile sourceEntry, TFile targetParentEntry, Path source, Path target, Set<CopyOption> options) throws IOException {
        TFile targetEntry = getEntry(target, false);
        targetEntry.cp(sourceEntry);
        return targetEntry;
    }

    @Override
    protected TFile moveEntry(TFile sourceEntry, TFile targetParentEntry, Path source, Path target, boolean targetIsParent) throws IOException {
        TFile targetEntry = getEntry(target, false);
        sourceEntry.mv(targetEntry);
        return targetEntry;
    }

    @Override
    protected TFile moveFolderEntry(TFile sourceEntry, TFile targetParentEntry, Path source, Path target, boolean targetIsParent) throws IOException {
        return moveEntry(sourceEntry, targetParentEntry, source, target, targetIsParent);
    }

    @Override
    protected TFile renameEntry(TFile sourceEntry, TFile targetParentEntry, Path source, Path target) throws IOException {
        return moveEntry(sourceEntry, targetParentEntry, source, target, false);
    }
}
