/*
 * Copyright (c) 2016 by Naohide Sano, All rights reserved.
 *
 * Programmed by Naohide Sano
 */

package vavi.nio.file.ftpoverhttp;

import java.nio.file.FileSystem;
import java.util.Collections;

import org.junit.jupiter.api.Test;

import static vavi.nio.file.Base.testLargeFile;


/**
 * TestLarge.
 *
 * @author <a href="mailto:umjammer@gmail.com">Naohide Sano</a> (umjammer)
 * @version 0.00 2016/03/xx umjammer initial version <br>
 */
public class TestLarge {

    @Test
    void test01() throws Exception {
        try (FtpOverHttpTestServer server = new FtpOverHttpTestServer();
             FileSystem fs = new FtpOverHttpFileSystemProvider().newFileSystem(server.getUri(), Collections.emptyMap())) {
            testLargeFile(fs, null);
        }
    }
}