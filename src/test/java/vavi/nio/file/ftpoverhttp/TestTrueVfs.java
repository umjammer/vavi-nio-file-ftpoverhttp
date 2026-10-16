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

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import vavi.util.Debug;
import vavi.util.properties.annotation.Property;
import vavi.util.properties.annotation.PropsEntity;

import static org.junit.jupiter.api.Assertions.assertEquals;
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
    private String file = "src/test/resources/";

    @Property
    private String url = "src/test/resources/";

    @BeforeEach
    void setup() throws Exception {
        if (Files.exists(Paths.get("local.properties"))) {
            PropsEntity.Util.bind(this);
        }
    }

    /**
     * @param args 0: base url, 1: alias
     */
    public static void main(String[] args) throws Exception {
        TestTrueVfs app = new TestTrueVfs();
        if (Files.exists(Paths.get("local.properties"))) {
            PropsEntity.Util.bind(app);
        }
        app.proceed();
    }

    /** file */
    void proceed() throws IOException {
Debug.println("exists: " + Files.exists(Paths.get(this.file)));
        File file = new File(this.file);
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
    @DisplayName("ftpoverhttp path, file")
    void proceed2() throws Exception {
        try (FtpOverHttpTestServer server = new FtpOverHttpTestServer();
             FileSystem fs = FileSystems.newFileSystem(server.getUri(), Collections.emptyMap())) {
            Files.copy(Paths.get("src/test/resources/Hello.java"), server.getRoot().resolve("Hello.java"), StandardCopyOption.REPLACE_EXISTING);

            Path path = fs.getPath("/Hello.java");
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
            assertEquals(Files.size(Paths.get("src/test/resources/Hello.java")), Files.size(out));
        }
    }

    @Test
    @DisplayName("ftpoverhttp walk")
    void proceed3() throws Exception {
        FileSystemProvider.installedProviders().forEach(System.err::println);
        // "tpath://" is correct
        try (FtpOverHttpTestServer server = new FtpOverHttpTestServer();
             FileSystem fs = FileSystems.newFileSystem(server.getUri(), Collections.emptyMap())) {
            Files.copy(Paths.get("src/test/resources/Hello.java"), server.getRoot().resolve("Hello.java"), StandardCopyOption.REPLACE_EXISTING);

            Path root = fs.getRootDirectories().iterator().next();
Debug.println("root: " + root + ", " + root.getClass().getName());
            Files.walk(root).forEach(System.err::println);
            assertEquals(2, Files.walk(root).count());
        }
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
