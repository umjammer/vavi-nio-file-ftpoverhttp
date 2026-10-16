/*
 * Copyright (c) 2021 by Naohide Sano, All rights reserved.
 *
 * Programmed by Naohide Sano
 */

package vavi.nio.file.ftpoverhttp;

import java.io.IOException;
import java.net.URI;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.Objects;
import java.util.logging.Level;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import javax.annotation.ParametersAreNonnullByDefault;

import com.github.fge.filesystem.driver.FileSystemDriver;
import com.github.fge.filesystem.provider.FileSystemRepositoryBase;

import org.apache.commons.net.ftp.FTP;
import org.apache.commons.net.ftp.FTPClient;
import org.apache.commons.net.ftp.FTPHTTPClient;
import org.apache.commons.net.ftp.FTPReply;

import vavi.util.Debug;


/**
 * FtpOverHttpFileSystemRepository.
 *
 * @author <a href="mailto:umjammer@gmail.com">Naohide Sano</a> (umjammer)
 * @version 0.00 2021/11/30 umjammer initial version <br>
 */
@ParametersAreNonnullByDefault
public final class FtpOverHttpFileSystemRepository extends FileSystemRepositoryBase {

    public FtpOverHttpFileSystemRepository() {
        super("ftpoverhttp", new FtpOverHttpFileSystemFactoryProvider());
    }

    /**
     * @param uri "ftpoverhttp:protocol:///?alias=alias",
     *            sub url (after "truevfs:") parts will be replaced by properties.
     *            if you don't use alias, the url must include username, password, host, port.
     */
    @Nonnull
    @Override
    public FileSystemDriver createDriver(final URI uri, final Map<String, ?> env) throws IOException {
        String uriString = uri.toString();
        URI subUri = uri.isOpaque() ? URI.create(uriString.substring(uriString.indexOf(':') + 1)) : uri;

        String host = subUri.getHost();
        if ((host == null || host.isEmpty()) && env.containsKey("host")) {
            host = String.valueOf(env.get("host"));
        }
        if (host == null || host.isEmpty()) {
            throw new IllegalArgumentException("missing host in uri: " + uri);
        }

        int port = subUri.getPort();
        if (port == -1 && env.containsKey("port")) {
            Object p = env.get("port");
            port = p instanceof Number n ? n.intValue() : Integer.parseInt(String.valueOf(p));
        }
        if (port == -1) {
            port = FTP.DEFAULT_PORT;
        }

        String username = "anonymous";
        String password = "";
        String rawUserInfo = subUri.getRawUserInfo();
        if (rawUserInfo != null && !rawUserInfo.isEmpty()) {
            int colon = rawUserInfo.indexOf(':');
            if (colon >= 0) {
                username = URLDecoder.decode(rawUserInfo.substring(0, colon), StandardCharsets.UTF_8);
                password = URLDecoder.decode(rawUserInfo.substring(colon + 1), StandardCharsets.UTF_8);
            } else {
                username = URLDecoder.decode(rawUserInfo, StandardCharsets.UTF_8);
            }
        }
        if (env.containsKey("username")) {
            username = String.valueOf(env.get("username"));
        }
        if (env.containsKey("password")) {
            password = String.valueOf(env.get("password"));
        }

        Map<String, String> params = getParamsMap(subUri);
        String proxyHost = params.get("proxyHost");
        if (proxyHost == null && env.containsKey("proxyHost")) {
            proxyHost = String.valueOf(env.get("proxyHost"));
        }
        if (proxyHost == null) {
            proxyHost = System.getProperty("http.proxyHost");
        }

        int proxyPort = 80;
        String proxyPortStr = params.get("proxyPort");
        if (proxyPortStr == null && env.containsKey("proxyPort")) {
            proxyPortStr = String.valueOf(env.get("proxyPort"));
        }
        if (proxyPortStr == null) {
            proxyPortStr = System.getProperty("http.proxyPort");
        }
        if (proxyPortStr != null && !proxyPortStr.isEmpty()) {
            proxyPort = Integer.parseInt(proxyPortStr);
        }

        String proxyUser = params.get("proxyUser");
        if (proxyUser == null && env.containsKey("proxyUser")) {
            proxyUser = String.valueOf(env.get("proxyUser"));
        }
        if (proxyUser == null) {
            proxyUser = System.getProperty("http.proxyUser");
        }

        String proxyPassword = params.get("proxyPassword");
        if (proxyPassword == null && env.containsKey("proxyPassword")) {
            proxyPassword = String.valueOf(env.get("proxyPassword"));
        }
        if (proxyPassword == null) {
            proxyPassword = System.getProperty("http.proxyPassword");
        }

        String baseUrl = subUri.getPath() != null ? subUri.getPath() : "";
Debug.println(Level.FINE, "baseUrl: " + baseUrl + ", host: " + host + ":" + port + ", proxy: " + proxyHost + ":" + proxyPort);

        FTPClient client;
        if (proxyHost != null && !proxyHost.isEmpty()) {
            if (proxyUser != null && !proxyUser.isEmpty()) {
                client = new FTPHTTPClient(proxyHost, proxyPort, proxyUser, proxyPassword != null ? proxyPassword : "", StandardCharsets.UTF_8);
            } else {
                client = new FTPHTTPClient(proxyHost, proxyPort, StandardCharsets.UTF_8);
            }
        } else {
            client = new FTPClient();
        }
        client.setControlEncoding(StandardCharsets.UTF_8.name());
        client.connect(host, port);
        int reply = client.getReplyCode();
        if (!FTPReply.isPositiveCompletion(reply)) {
            client.disconnect();
            throw new IOException("FTP server refused connection. Reply code: " + reply);
        }
        if (!client.login(username, password)) {
            client.disconnect();
            throw new IOException("FTP login failed for user: " + username);
        }
        client.enterLocalPassiveMode();
        client.setFileType(FTP.BINARY_FILE_TYPE);

        FtpOverHttpFileStore fileStore = new FtpOverHttpFileStore(client, factoryProvider.getAttributesFactory());
        return new FtpOverHttpFileSystemDriver(fileStore, factoryProvider, client, baseUrl, env);
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
