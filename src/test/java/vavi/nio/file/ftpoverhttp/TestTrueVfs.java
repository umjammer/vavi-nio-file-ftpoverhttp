/*
 * Copyright (c) 2021 by Naohide Sano, All rights reserved.
 *
 * Programmed by Naohide Sano
 */

package vavi.nio.file.ftpoverhttp;

import java.io.File;
import java.io.IOException;
import java.net.URI;
import java.nio.file.FileSystem;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.nio.file.spi.FileSystemProvider;
import java.time.Instant;
import java.util.Collections;

import net.java.truevfs.access.TFileSystemProvider;
import net.java.truevfs.access.TPath;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import vavi.util.Debug;
import vavi.util.properties.annotation.Property;
import vavi.util.properties.annotation.PropsEntity;

import net.java.truevfs.access.TFile;

import static org.junit.jupiter.api.Assertions.assertTrue;


/**
 * TestTrueVfs (http).
 *
 * @author <a href="mailto:umjammer@gmail.com">Naohide Sano</a> (umjammer)
 * @version 0.00 2021/11/30 umjammer initial version <br>
 */
@PropsEntity(url = "file://${user.dir}/local.properties")
public class TestTrueVfs {

    @Property
    private String file = "file:src/test/resources/";

    @Property
    private String url = "file:src/test/resources/";

    @Property
    private String url2;

    @BeforeEach
    void setup() throws Exception {
        PropsEntity.Util.bind(this);
    }

    /**
     * @param args 0: base url, 1: alias
     */
    public static void main(String[] args) throws Exception {
        TestTrueVfs app = new TestTrueVfs();
        PropsEntity.Util.bind(app);
        app.proceed();
    }

    /** file */
    void proceed() throws IOException {
Debug.println("exists: " + Files.exists(Paths.get(this.file)));
        File file = new TFile(this.file);
System.err.println(file.exists() + ", " + Instant.ofEpochMilli(file.lastModified()) + ", " + file + ", " + file.isDirectory());
        if (file.isDirectory()) { // root
            proceedDir("", file);
        }
    }

    /** file sub */
    void proceedDir(String parent, File dir) {
        for (File file : dir.listFiles()) {
            if (file.isDirectory()) {
                proceedDir(parent + "/" + file.getName(), file);
            } else {
                System.err.println(parent + "/" + file.getName());
            }
        }
    }

    @Test
    @DisplayName("truevfs path, file")
    void proceed2() throws IOException {
        Path path = new TPath(URI.create(this.url2));
Debug.println(path.getFileSystem());
System.err.println(Files.exists(path) + ", " + Files.getLastModifiedTime(path) + ", " + path + ", " + Files.isDirectory(path)  + ", " + Files.size(path));
        Path out = Paths.get("tmp").resolve(path.getFileName().toString());
        if (!Files.exists(out.getParent())) {
            Files.createDirectories(out.getParent());
        }
        Files.copy(Files.newInputStream(path), out, StandardCopyOption.REPLACE_EXISTING);
Debug.println("out: " + Files.exists(out) + ", " + Files.size(out));
        assertTrue(Files.exists(out));
        assertTrue(Files.size(out) > 0);
    }

    @Test
    @DisplayName("truevfs path, html")
    void proceed22() throws IOException {
        Path path = new TPath(URI.create(this.url));
Debug.println(path.getFileSystem());
System.err.println(Files.exists(path) + ", " + Files.getLastModifiedTime(path) + ", " + path + ", " + Files.isDirectory(path)  + ", " + Files.size(path));
        Path out = Paths.get("tmp").resolve(path.getFileName().toString());
        if (!Files.exists(out.getParent())) {
            Files.createDirectories(out.getParent());
        }
        Files.copy(Files.newInputStream(path), out, StandardCopyOption.REPLACE_EXISTING);
Debug.println("out: " + Files.exists(out) + ", " + Files.size(out));
        assertTrue(Files.exists(out));
        assertTrue(Files.size(out) > 0);
    }

    @Test
    @DisplayName("truevfs filesystem")
    @Disabled("url syntax???")
    void proceed3() throws Exception {
        TFileSystemProvider.installedProviders().forEach(System.err::println);
        URI uri = URI.create("tpath://" + this.url); // "tpath://" is correct
Debug.println("uri: " + uri + ", path: " + uri.getPath());
        FileSystem fs = FileSystems.newFileSystem(uri, Collections.emptyMap());
        Path root = fs.getRootDirectories().iterator().next();
Debug.println("root: " + root + ", " + root.getClass().getName());
        Files.walk(root).forEach(System.err::println);
    }

    @Test
    @DisplayName("try jsr203-http")
    @Disabled("Not implemented at HttpAbstractFileSystemProvider#readAttributes()")
    void testOtherProvider() throws Exception {
        FileSystemProvider.installedProviders().forEach(System.err::println);
Debug.println("url: " + this.url);
        FileSystem fs = FileSystems.newFileSystem(URI.create(this.url), Collections.emptyMap());
        Path root = fs.getRootDirectories().iterator().next();
Debug.println("root: " + root + ", " + root.getClass().getName());
        Files.walk(root).forEach(System.err::println);
    }
}
