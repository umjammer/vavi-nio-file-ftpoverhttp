/*
 * Copyright (c) 2026 by Naohide Sano, All rights reserved.
 *
 * Programmed by Naohide Sano
 */

package vavi.nio.file.ftpoverhttp;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Comparator;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Stream;


/**
 * A temporary FTP server and HTTP CONNECT proxy server for tests.
 *
 * @author <a href="mailto:umjammer@gmail.com">Naohide Sano</a> (nsano)
 * @version 0.00 2026-10-16 nsano initial version <br>
 */
class FtpOverHttpTestServer implements AutoCloseable {

    static final String USER = "tester";
    static final String PASSWORD = "testpassword";
    private static final DateTimeFormatter LIST_DATE_FORMAT =
            DateTimeFormatter.ofPattern("MMM dd HH:mm", Locale.US);

    private final Path base;
    private final Path root;
    private final ServerSocket ftpServerSocket;
    private final ServerSocket proxyServerSocket;
    private final ExecutorService executor = Executors.newCachedThreadPool();
    private final AtomicBoolean running = new AtomicBoolean(true);
    private final AtomicInteger proxyConnectCount = new AtomicInteger();

    FtpOverHttpTestServer() throws IOException {
        base = Files.createTempDirectory("ftpoverhttp-test-");
        root = Files.createDirectory(base.resolve("root"));

        InetAddress loopback = InetAddress.getByName("127.0.0.1");
        ftpServerSocket = new ServerSocket(0, 50, loopback);
        proxyServerSocket = new ServerSocket(0, 50, loopback);

        executor.submit(this::acceptFtpLoop);
        executor.submit(this::acceptProxyLoop);
    }

    /** the directory served as "/" */
    Path getRoot() {
        return root;
    }

    int getFtpPort() {
        return ftpServerSocket.getLocalPort();
    }

    int getProxyPort() {
        return proxyServerSocket.getLocalPort();
    }

    int getProxyConnectCount() {
        return proxyConnectCount.get();
    }

    /** @return "ftpoverhttp://..." uri configured with the local HTTP CONNECT proxy */
    URI getUri() {
        return URI.create(String.format("ftpoverhttp://%s:%s@127.0.0.1:%d/?proxyHost=127.0.0.1&proxyPort=%d",
                USER, PASSWORD, getFtpPort(), getProxyPort()));
    }

    private void acceptProxyLoop() {
        while (running.get()) {
            try {
                Socket clientSocket = proxyServerSocket.accept();
                executor.submit(() -> handleProxyClient(clientSocket));
            } catch (IOException e) {
                if (running.get()) {
                    e.printStackTrace();
                }
            }
        }
    }

    private void handleProxyClient(Socket clientSocket) {
        try {
            InputStream in = clientSocket.getInputStream();
            OutputStream out = clientSocket.getOutputStream();

            String requestLine = readAsciiLine(in);
            if (requestLine == null || !requestLine.startsWith("CONNECT ")) {
                clientSocket.close();
                return;
            }
            // Read remaining headers until blank line
            while (true) {
                String header = readAsciiLine(in);
                if (header == null || header.isEmpty()) {
                    break;
                }
            }

            String[] parts = requestLine.split(" ");
            String[] hostPort = parts[1].split(":");
            String targetHost = hostPort[0];
            int targetPort = Integer.parseInt(hostPort[1]);

            Socket targetSocket = new Socket(targetHost, targetPort);
            proxyConnectCount.incrementAndGet();

            out.write("HTTP/1.1 200 Connection established\r\n\r\n".getBytes(StandardCharsets.US_ASCII));
            out.flush();

            executor.submit(() -> pipe(in, targetSocket, clientSocket, true));
            executor.submit(() -> pipe(targetSocket, out, clientSocket, false));
        } catch (IOException e) {
            try {
                clientSocket.close();
            } catch (IOException ignore) {
            }
        }
    }

    private static String readAsciiLine(InputStream in) throws IOException {
        StringBuilder sb = new StringBuilder();
        int b;
        while ((b = in.read()) != -1) {
            if (b == '\n') {
                break;
            }
            if (b != '\r') {
                sb.append((char) b);
            }
        }
        return b == -1 && sb.isEmpty() ? null : sb.toString();
    }

    private static void pipe(InputStream in, Socket targetSocket, Socket clientSocket, boolean toTarget) {
        try {
            OutputStream out = targetSocket.getOutputStream();
            byte[] buf = new byte[8192];
            int r;
            while ((r = in.read(buf)) != -1) {
                out.write(buf, 0, r);
                out.flush();
            }
            targetSocket.shutdownOutput();
        } catch (IOException ignore) {
        }
    }

    private static void pipe(Socket targetSocket, OutputStream out, Socket clientSocket, boolean dummy) {
        try {
            InputStream in = targetSocket.getInputStream();
            byte[] buf = new byte[8192];
            int r;
            while ((r = in.read(buf)) != -1) {
                out.write(buf, 0, r);
                out.flush();
            }
            clientSocket.shutdownOutput();
        } catch (IOException ignore) {
        } finally {
            try {
                targetSocket.close();
            } catch (IOException ignore) {
            }
            try {
                clientSocket.close();
            } catch (IOException ignore) {
            }
        }
    }

    private void acceptFtpLoop() {
        while (running.get()) {
            try {
                Socket socket = ftpServerSocket.accept();
                executor.submit(() -> handleFtpSession(socket));
            } catch (IOException e) {
                if (running.get()) {
                    e.printStackTrace();
                }
            }
        }
    }

    private void handleFtpSession(Socket controlSocket) {
        ServerSocket pasvServer = null;
        try (controlSocket;
             BufferedReader reader = new BufferedReader(new InputStreamReader(controlSocket.getInputStream(), StandardCharsets.UTF_8));
             PrintWriter writer = new PrintWriter(new OutputStreamWriter(controlSocket.getOutputStream(), StandardCharsets.UTF_8), false)) {

            sendReply(writer, "220 Service ready for new user.");

            String currentUser = null;
            boolean loggedIn = false;
            Path cwd = root;
            Path renameFrom = null;

            String line;
            while ((line = reader.readLine()) != null) {
                int space = line.indexOf(' ');
                String cmd = (space >= 0 ? line.substring(0, space) : line).toUpperCase(Locale.ROOT);
                String arg = space >= 0 ? line.substring(space + 1) : "";

                switch (cmd) {
                    case "USER" -> {
                        currentUser = arg;
                        sendReply(writer, "331 User name okay, need password.");
                    }
                    case "PASS" -> {
                        if (USER.equals(currentUser) && PASSWORD.equals(arg)) {
                            loggedIn = true;
                            sendReply(writer, "230 User logged in, proceed.");
                        } else {
                            sendReply(writer, "530 Not logged in.");
                        }
                    }
                    case "SYST" -> sendReply(writer, "215 UNIX Type: L8");
                    case "FEAT" -> {
                        writer.print("211-Features:\r\n UTF8\r\n211 End\r\n");
                        writer.flush();
                    }
                    case "OPTS", "TYPE", "NOOP" -> sendReply(writer, "200 Command okay.");
                    case "PWD", "XPWD" -> {
                        String rel = "/" + root.relativize(cwd).toString().replace('\\', '/');
                        if (rel.endsWith("/") && rel.length() > 1) {
                            rel = rel.substring(0, rel.length() - 1);
                        }
                        sendReply(writer, "257 \"" + rel + "\" is current directory.");
                    }
                    case "CWD" -> {
                        Path target = resolvePath(cwd, arg);
                        if (Files.isDirectory(target)) {
                            cwd = target;
                            sendReply(writer, "250 Directory successfully changed.");
                        } else {
                            sendReply(writer, "550 Failed to change directory.");
                        }
                    }
                    case "PASV" -> {
                        if (pasvServer != null) {
                            pasvServer.close();
                        }
                        pasvServer = new ServerSocket(0, 1, InetAddress.getByName("127.0.0.1"));
                        int p = pasvServer.getLocalPort();
                        sendReply(writer, String.format("227 Entering Passive Mode (127,0,0,1,%d,%d).", (p >> 8) & 0xff, p & 0xff));
                    }
                    case "EPSV" -> {
                        if (pasvServer != null) {
                            pasvServer.close();
                        }
                        pasvServer = new ServerSocket(0, 1, InetAddress.getByName("127.0.0.1"));
                        int p = pasvServer.getLocalPort();
                        sendReply(writer, String.format("229 Entering Extended Passive Mode (|||%d|).", p));
                    }
                    case "LIST" -> {
                        String pathArg = arg;
                        if (pathArg.startsWith("-")) {
                            int sp = pathArg.indexOf(' ');
                            pathArg = sp >= 0 ? pathArg.substring(sp + 1).trim() : "";
                        }
                        Path target = pathArg.isEmpty() ? cwd : resolvePath(cwd, pathArg);
                        sendReply(writer, "150 Here comes the directory listing.");
                        try (ServerSocket ps = pasvServer;
                             Socket dataSocket = ps.accept();
                             OutputStream dataOut = dataSocket.getOutputStream()) {
                            pasvServer = null;
                            if (Files.isDirectory(target)) {
                                try (Stream<Path> children = Files.list(target)) {
                                    for (Path child : children.toList()) {
                                        String perms = Files.isDirectory(child) ? "drwxr-xr-x" : "-rw-r--r--";
                                        long size = Files.isDirectory(child) ? 0L : Files.size(child);
                                        ZonedDateTime mtime = Files.getLastModifiedTime(child).toInstant().atZone(ZoneId.systemDefault());
                                        String dateStr = LIST_DATE_FORMAT.format(mtime);
                                        String entryLine = String.format(Locale.US, "%s 1 tester tester %10d %s %s\r\n",
                                                perms, size, dateStr, child.getFileName().toString());
                                        dataOut.write(entryLine.getBytes(StandardCharsets.UTF_8));
                                    }
                                }
                            } else if (Files.exists(target)) {
                                long size = Files.size(target);
                                ZonedDateTime mtime = Files.getLastModifiedTime(target).toInstant().atZone(ZoneId.systemDefault());
                                String dateStr = LIST_DATE_FORMAT.format(mtime);
                                String entryLine = String.format(Locale.US, "-rw-r--r-- 1 tester tester %10d %s %s\r\n",
                                        size, dateStr, target.getFileName().toString());
                                dataOut.write(entryLine.getBytes(StandardCharsets.UTF_8));
                            }
                            dataOut.flush();
                        }
                        sendReply(writer, "226 Directory send OK.");
                    }
                    case "RETR" -> {
                        Path target = resolvePath(cwd, arg);
                        if (!Files.isRegularFile(target)) {
                            if (pasvServer != null) {
                                pasvServer.close();
                                pasvServer = null;
                            }
                            sendReply(writer, "550 Failed to open file.");
                        } else {
                            sendReply(writer, "150 Opening BINARY mode data connection.");
                            try (ServerSocket ps = pasvServer;
                                 Socket dataSocket = ps.accept();
                                 OutputStream dataOut = dataSocket.getOutputStream()) {
                                pasvServer = null;
                                Files.copy(target, dataOut);
                                dataOut.flush();
                            }
                            sendReply(writer, "226 Transfer complete.");
                        }
                    }
                    case "STOR" -> {
                        Path target = resolvePath(cwd, arg);
                        if (target.getParent() == null || !Files.isDirectory(target.getParent())) {
                            if (pasvServer != null) {
                                pasvServer.close();
                                pasvServer = null;
                            }
                            sendReply(writer, "550 Failed to open file.");
                        } else {
                            sendReply(writer, "150 Ok to send data.");
                            try (ServerSocket ps = pasvServer;
                                 Socket dataSocket = ps.accept();
                                 InputStream dataIn = dataSocket.getInputStream()) {
                                pasvServer = null;
                                Files.copy(dataIn, target, StandardCopyOption.REPLACE_EXISTING);
                            }
                            sendReply(writer, "226 Transfer complete.");
                        }
                    }
                    case "MKD", "XMKD" -> {
                        Path target = resolvePath(cwd, arg);
                        try {
                            Files.createDirectory(target);
                            sendReply(writer, "257 \"" + arg + "\" created.");
                        } catch (IOException e) {
                            sendReply(writer, "550 Create directory operation failed.");
                        }
                    }
                    case "RMD", "XRMD" -> {
                        Path target = resolvePath(cwd, arg);
                        if (!Files.isDirectory(target)) {
                            sendReply(writer, "550 Remove directory operation failed.");
                        } else {
                            try {
                                Files.delete(target);
                                sendReply(writer, "250 Remove directory operation successful.");
                            } catch (IOException e) {
                                sendReply(writer, "550 Remove directory operation failed.");
                            }
                        }
                    }
                    case "DELE" -> {
                        Path target = resolvePath(cwd, arg);
                        if (!Files.isRegularFile(target)) {
                            sendReply(writer, "550 Delete operation failed.");
                        } else {
                            try {
                                Files.delete(target);
                                sendReply(writer, "250 Delete operation successful.");
                            } catch (IOException e) {
                                sendReply(writer, "550 Delete operation failed.");
                            }
                        }
                    }
                    case "RNFR" -> {
                        Path target = resolvePath(cwd, arg);
                        if (!Files.exists(target)) {
                            sendReply(writer, "550 RNFR command failed.");
                        } else {
                            renameFrom = target;
                            sendReply(writer, "350 Ready for RNTO.");
                        }
                    }
                    case "RNTO" -> {
                        Path target = resolvePath(cwd, arg);
                        if (renameFrom == null) {
                            sendReply(writer, "503 RNFR required first.");
                        } else {
                            try {
                                Files.move(renameFrom, target, StandardCopyOption.REPLACE_EXISTING);
                                renameFrom = null;
                                sendReply(writer, "250 Rename successful.");
                            } catch (IOException e) {
                                renameFrom = null;
                                sendReply(writer, "550 Rename failed.");
                            }
                        }
                    }
                    case "QUIT" -> {
                        sendReply(writer, "221 Goodbye.");
                        return;
                    }
                    default -> sendReply(writer, "502 Command not implemented.");
                }
            }
        } catch (IOException ignore) {
        } finally {
            if (pasvServer != null) {
                try {
                    pasvServer.close();
                } catch (IOException ignore) {
                }
            }
        }
    }

    private Path resolvePath(Path cwd, String ftpPath) {
        if (ftpPath == null || ftpPath.isEmpty() || ftpPath.equals("/")) {
            return root;
        }
        String rel = ftpPath.startsWith("/") ? ftpPath.substring(1) : ftpPath;
        Path baseDir = ftpPath.startsWith("/") ? root : cwd;
        Path resolved = baseDir.resolve(rel).normalize();
        return resolved.startsWith(root) ? resolved : root;
    }

    private static void sendReply(PrintWriter writer, String reply) {
        writer.print(reply + "\r\n");
        writer.flush();
    }

    @Override
    public void close() throws IOException {
        running.set(false);
        try {
            ftpServerSocket.close();
        } catch (IOException ignore) {
        }
        try {
            proxyServerSocket.close();
        } catch (IOException ignore) {
        }
        executor.shutdownNow();
        try (Stream<Path> s = Files.walk(base)) {
            s.sorted(Comparator.reverseOrder()).forEach(p -> p.toFile().delete());
        }
    }
}
