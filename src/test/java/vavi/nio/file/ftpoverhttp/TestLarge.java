/*
 * Copyright (c) 2016 by Naohide Sano, All rights reserved.
 *
 * Programmed by Naohide Sano
 */

package vavi.nio.file.ftpoverhttp;

import java.net.URI;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.FileSystem;
import java.util.Collections;

import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;

import vavi.nio.file.ftpovergttp.FtpOverHttpFileSystemProvider;

import static vavi.nio.file.Base.testLargeFile;


/**
 * Vfs. (sftp)
 *
 * @author <a href="mailto:umjammer@gmail.com">Naohide Sano</a> (umjammer)
 * @version 0.00 2016/03/xx umjammer initial version <br>
 */
public class TestLarge {

    /**
     * environment variable
     * <ul>
     * <li> TEST_SFTP_ACCOUNT
     * <li> TEST_SFTP_PASSPHRASE
     * <li> TEST_SFTP_HOST
     * <li> TEST_SFTP_KEYPATH
     * <li> TEST_SFTP_PATH
     * </ul>
     */
    @Test
    @Disabled("no writing scheme")
    void test01() throws Exception {
        String username = URLEncoder.encode(System.getenv("TEST_SFTP_ACCOUNT"), StandardCharsets.UTF_8);
        String passPhrase = System.getenv("TEST_SFTP_PASSPHRASE");
        String host = System.getenv("TEST_SFTP_HOST");
        String keyPath = System.getenv("TEST_SFTP_KEYPATH");
        String path = System.getenv("TEST_SFTP_PATH");
        String mountPoint = System.getenv("TEST_MOUNT_POINT");

        URI uri = URI.create(String.format("truevfs:file://%s", mountPoint));

        FileSystem fs = new FtpOverHttpFileSystemProvider().newFileSystem(uri, Collections.emptyMap());

        testLargeFile(fs, null);
    }
}