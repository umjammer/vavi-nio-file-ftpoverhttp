/*
 * Copyright (c) 2017 by Naohide Sano, All rights reserved.
 *
 * Programmed by Naohide Sano
 */

package vavi.nio.file.ftpoverhttp;

import java.io.IOException;
import java.net.URI;
import java.nio.file.FileSystem;
import java.nio.file.FileSystems;
import java.util.HashMap;
import java.util.Map;

import vavi.net.fuse.Base;
import vavi.net.fuse.Fuse;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.condition.DisabledIfEnvironmentVariable;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;


/**
 * TestFuse.
 *
 * @author <a href="mailto:umjammer@gmail.com">Naohide Sano</a> (umjammer)
 * @version 0.00 2017/03/19 umjammer initial version <br>
 */
@DisabledIfEnvironmentVariable(named = "GITHUB_WORKFLOW", matches = ".*")
class TestFuse {

    String mountPoint;
    FileSystem fs;
    Map<String, Object> options;
    static FtpOverHttpTestServer server;

    @BeforeAll
    static void setupAll() throws Exception {
        server = new FtpOverHttpTestServer();
    }

    @AfterAll
    static void afterAll() throws Exception {
        server.close();
    }

    @BeforeEach
    void before() throws Exception {
        mountPoint = System.getenv("TEST_MOUNT_POINT");

        URI uri = server.getUri();

        Map<String, Object> env = new HashMap<>();
        env.put("ignoreAppleDouble", true);

        fs = FileSystems.newFileSystem(uri, env);

        options = new HashMap<>();
        options.put("fsname", "ftpoverhttp_fs" + "@" + System.currentTimeMillis());
        options.put("noappledouble", null);
        //options.put("noapplexattr", null);
        options.put(vavi.net.fuse.javafs.JavaFSFuse.ENV_DEBUG, false);
        options.put(vavi.net.fuse.javafs.JavaFSFuse.ENV_READ_ONLY, false);
        // vfs io uses ThreadLocal to keep internal info when read/write, so this option must be set
        options.put(vavi.net.fuse.Fuse.ENV_SINGLE_THREAD, true);
    }

    @ParameterizedTest
    @ValueSource(strings = {
        "vavi.net.fuse.javafs.JavaFSFuseProvider",
        "vavi.net.fuse.jnrfuse.JnrFuseFuseProvider",
        "vavi.net.fuse.fusejna.FuseJnaFuseProvider",
    })
    public void test01(String providerClassName) throws Exception {
        System.setProperty("vavi.net.fuse.FuseProvider.class", providerClassName);

        Base.testFuse(fs, mountPoint, options);

        fs.close();
    }

    //

    /**
     * @param args 0: alias, args 1: mount point (should be replaced by alias)
     */
    public static void main(String... args) throws IOException {
        String url = args[0];
        String mountPoint = String.format(args[1]);

        URI uri = URI.create(String.format("ftpoverhttp://%s", url));

        Map<String, Object> env = new HashMap<>();
        env.put("ignoreAppleDouble", true);

        FileSystem fs = new FtpOverHttpFileSystemProvider().newFileSystem(uri, env);

//        System.setProperty("vavi.net.fuse.FuseProvider.class", "vavi.net.fuse.javafs.JavaFSFuseProvider");
//        System.setProperty("vavi.net.fuse.FuseProvider.class", "vavi.net.fuse.jnrfuse.JnrFuseFuseProvider");
        System.setProperty("vavi.net.fuse.FuseProvider.class", "vavi.net.fuse.fusejna.FuseJnaFuseProvider");

        Map<String, Object> options = new HashMap<>();
        options.put("fsname", "ftpoverhttp_fs" + "@" + System.currentTimeMillis());
        options.put(vavi.net.fuse.javafs.JavaFSFuse.ENV_DEBUG, false);
        options.put(vavi.net.fuse.javafs.JavaFSFuse.ENV_READ_ONLY, false);
        // vfs io uses ThreadLocal to keep internal info when read/write, so this option must be set
        options.put(vavi.net.fuse.Fuse.ENV_SINGLE_THREAD, true);

        Fuse.getFuse().mount(fs, mountPoint, options);
    }
}
