/*
 * Copyright (c) 2021 by Naohide Sano, All rights reserved.
 *
 * Programmed by Naohide Sano
 */

package vavi.nio.file.ftpoverhttp;

import com.github.fge.filesystem.driver.ExtendedFileSystemDriverBase.ExtendedFileAttributesFactory;
import net.java.truevfs.access.TFile;


/**
 * FtpOverHttpFileAttributesFactory.
 *
 * @author <a href="mailto:umjammer@gmail.com">Naohide Sano</a> (umjammer)
 * @version 0.00 2021/11/30 umjammer initial version <br>
 */
public final class FtpOverHttpFileAttributesFactory extends ExtendedFileAttributesFactory {

    public FtpOverHttpFileAttributesFactory() {
        setMetadataClass(TFile.class);
        addImplementation("basic", FtpOverHttpBasicFileAttributesProvider.class);
    }
}
