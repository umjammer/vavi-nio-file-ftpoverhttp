/*
 * Copyright (c) 2016 by Naohide Sano, All rights reserved.
 *
 * Programmed by Naohide Sano
 */

package vavi.nio.file.ftpoverhttp;

import java.nio.file.FileSystem;
import java.util.Collections;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static vavi.nio.file.Base.testAll;


/**
 * FtpOverHttp.
 *
 * @author <a href="mailto:umjammer@gmail.com">Naohide Sano</a> (umjammer)
 * @version 0.00 2016/04/03 umjammer initial version <br>
 */
public class TestCase {

    @Test
    void test01() throws Exception {
        try (FtpOverHttpTestServer server = new FtpOverHttpTestServer();
             FileSystem fs = new FtpOverHttpFileSystemProvider().newFileSystem(server.getUri(), Collections.emptyMap())) {
            testAll(fs);
            assertTrue(server.getProxyConnectCount() > 0);
        }
    }
}