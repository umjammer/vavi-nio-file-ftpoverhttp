/*
 * Copyright (c) 2021 by Naohide Sano, All rights reserved.
 *
 * Programmed by Naohide Sano
 */

package vavi.nio.file.ftpovergttp;

import java.io.IOException;
import java.net.URI;
import java.util.Map;
import java.util.Objects;
import java.util.logging.Level;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import javax.annotation.ParametersAreNonnullByDefault;

import com.github.fge.filesystem.driver.FileSystemDriver;
import com.github.fge.filesystem.provider.FileSystemRepositoryBase;

import vavi.util.Debug;

import net.java.truevfs.access.TConfig;
import net.java.truevfs.access.TFile;


/**
 * FtpOverHttpFileSystemRepository.
 *
 * @author <a href="mailto:umjammer@gmail.com">Naohide Sano</a> (umjammer)
 * @version 0.00 2021/11/30 umjammer initial version <br>
 */
@ParametersAreNonnullByDefault
public final class FtpOverHttpFileSystemRepository extends FileSystemRepositoryBase {

    public FtpOverHttpFileSystemRepository() {
        super("truevfs", new FtpOverHttpFileSystemFactoryProvider());
    }

    /**
     * @param uri "ftpoverhttp:protocol:///?alias=alias",
     *            sub url (after "truevfs:") parts will be replaced by properties.
     *            if you don't use alias, the url must include username, password, host, port.
     */
    @Nonnull
    @Override
    public FileSystemDriver createDriver(final URI uri, final Map<String, ?> env) throws IOException {
        String baseUrl = uri.getPath();
Debug.println(Level.FINE, "baseUrl: " + baseUrl);

        TConfig config = TConfig.current();
        TFile root = new TFile(baseUrl);
Debug.println(Level.FINE, "root: " + root);
        FtpOverHttpFileStore fileStore = new FtpOverHttpFileStore(root, factoryProvider.getAttributesFactory());
        return new FtpOverHttpFileSystemDriver(fileStore, factoryProvider, config, baseUrl, env);
    }

    /* ad-hoc hack for ignoring checking opacity */
    protected void checkURI(@Nullable final URI uri) {
        Objects.requireNonNull(uri);
        if (!uri.isAbsolute()) {
            throw new IllegalArgumentException("uri is not absolute");
        }
        if (!getScheme().equals(uri.getScheme())) {
            throw new IllegalArgumentException("bad scheme");
        }
    }
}
