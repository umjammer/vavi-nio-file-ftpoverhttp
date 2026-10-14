/*
 * Copyright (c) 2021 by Naohide Sano, All rights reserved.
 *
 * Programmed by Naohide Sano
 */

package vavi.nio.file.ftpovergttp;

import com.github.fge.filesystem.provider.FileSystemProviderBase;


/**
 * FtpOverHttpFileSystemProvider.
 *
 * @author <a href="mailto:umjammer@gmail.com">Naohide Sano</a> (umjammer)
 * @version 0.00 2021/11/30 umjammer initial version <br>
 */
public final class FtpOverHttpFileSystemProvider extends FileSystemProviderBase {

    public static final String PARAM_ALIAS = "alias";

    public FtpOverHttpFileSystemProvider() {
        super(new FtpOverHttpFileSystemRepository());
    }
}
