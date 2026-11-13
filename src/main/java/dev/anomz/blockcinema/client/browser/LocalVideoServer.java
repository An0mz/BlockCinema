package dev.anomz.blockcinema.client.browser;

import java.io.BufferedReader;
import java.io.Closeable;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.URLDecoder;
import java.net.URLEncoder;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.security.SecureRandom;
import java.util.HexFormat;
import java.util.Locale;
import java.util.Map;
import java.util.function.Function;

import org.jetbrains.annotations.Nullable;

import dev.anomz.blockcinema.BlockCinema;

/**
 * Hands the player's own video files to the TV browsers, which can't open files themselves. It only
 * listens on this computer (127.0.0.1), only answers under a random path made up when it starts, and
 * only gives out the files {@code files} finds by name. It answers byte ranges, which the browser
 * needs to jump around in a video.
 */
final class LocalVideoServer implements Closeable {
    private static final int MAX_HEADERS = 100;
    private static final int TIMEOUT_MILLIS = 30_000;
    private static final Map<String, String> TYPES = Map.of("mp4", "video/mp4", "m4v", "video/mp4", "webm", "video/webm",
            "mkv", "video/x-matroska", "mov", "video/quicktime", "ogv", "video/ogg", "ogg", "audio/ogg", "mp3", "audio/mpeg",
            "m4a", "audio/mp4", "wav", "audio/wav");

    private final ServerSocket socket;
    private final String secret;
    private final Function<String, Path> files;

    /** {@code files} gives the file for a name, or null if there is none. */
    LocalVideoServer(Function<String, Path> files) throws IOException {
        this.files = files;
        byte[] random = new byte[16];
        new SecureRandom().nextBytes(random);
        secret = HexFormat.of().formatHex(random);
        socket = new ServerSocket(0, 16, InetAddress.getLoopbackAddress());
        Thread.ofPlatform().daemon().name("Block Cinema local videos").start(this::accept);
    }

    String url(String name) {
        return "http://127.0.0.1:" + socket.getLocalPort() + "/" + secret + "/"
                + URLEncoder.encode(name, StandardCharsets.UTF_8).replace("+", "%20");
    }

    @Override
    public void close() {
        try {
            socket.close();
        } catch (IOException e) {
            BlockCinema.LOGGER.warn("Couldn't stop serving local videos", e);
        }
    }

    private void accept() {
        while (!socket.isClosed()) {
            try {
                Socket connection = socket.accept();
                Thread.ofPlatform().daemon().name("Block Cinema local video").start(() -> answer(connection));
            } catch (IOException e) {
                if (!socket.isClosed()) {
                    BlockCinema.LOGGER.warn("Local videos: a connection failed", e);
                }
            }
        }
    }

    private void answer(Socket connection) {
        try (connection) {
            connection.setSoTimeout(TIMEOUT_MILLIS);
            BufferedReader in = new BufferedReader(new InputStreamReader(connection.getInputStream(), StandardCharsets.ISO_8859_1));
            OutputStream out = connection.getOutputStream();
            String first = in.readLine();
            String[] request = first == null ? new String[0] : first.split(" ");
            String rangeHeader = null;
            int headers = 0;
            for (String header = in.readLine(); header != null && !header.isEmpty(); header = in.readLine()) {
                if (++headers > MAX_HEADERS) {
                    return;
                }
                if (header.regionMatches(true, 0, "Range:", 0, 6)) {
                    rangeHeader = header.substring(6).trim();
                }
            }
            boolean head = request.length == 3 && request[0].equals("HEAD");
            Path file = request.length == 3 && (head || request[0].equals("GET")) ? file(request[1]) : null;
            if (file == null) {
                out.write(head(404, "Not Found", "Content-Length: 0").getBytes(StandardCharsets.ISO_8859_1));
                return;
            }
            try (FileChannel channel = FileChannel.open(file)) {
                long size = channel.size();
                long[] range = range(rangeHeader, size);
                if (range == null) {
                    out.write(head(416, "Range Not Satisfiable", "Content-Range: bytes */" + size, "Content-Length: 0")
                            .getBytes(StandardCharsets.ISO_8859_1));
                    return;
                }
                long length = range[1] - range[0] + 1;
                String type = "Content-Type: " + type(file.getFileName().toString());
                out.write((rangeHeader == null ? head(200, "OK", type, "Content-Length: " + length)
                        : head(206, "Partial Content", type, "Content-Length: " + length,
                                "Content-Range: bytes " + range[0] + "-" + range[1] + "/" + size))
                        .getBytes(StandardCharsets.ISO_8859_1));
                if (!head) {
                    send(channel, range[0], length, out);
                }
            }
        } catch (IOException e) {
            // The browser hangs up whenever it has enough or jumps elsewhere in the video.
        }
    }

    private static void send(FileChannel channel, long start, long length, OutputStream out) throws IOException {
        ByteBuffer buffer = ByteBuffer.allocate(64 * 1024);
        channel.position(start);
        long left = length;
        while (left > 0) {
            buffer.clear().limit((int) Math.min(buffer.capacity(), left));
            int read = channel.read(buffer);
            if (read <= 0) {
                return;
            }
            out.write(buffer.array(), 0, read);
            left -= read;
        }
    }

    @Nullable
    private Path file(String target) {
        String prefix = "/" + secret + "/";
        int query = target.indexOf('?');
        String path = query < 0 ? target : target.substring(0, query);
        if (!path.startsWith(prefix)) {
            return null;
        }
        try {
            // A "+" in a path is a plus, not the space it would be in a query.
            return files.apply(URLDecoder.decode(path.substring(prefix.length()).replace("+", "%2B"), StandardCharsets.UTF_8));
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    private static String head(int status, String reason, String... headers) {
        StringBuilder head = new StringBuilder("HTTP/1.1 ").append(status).append(' ').append(reason).append("\r\n");
        for (String header : headers) {
            head.append(header).append("\r\n");
        }
        return head.append("Accept-Ranges: bytes\r\nConnection: close\r\n\r\n").toString();
    }

    private static String type(String name) {
        String ending = name.substring(name.lastIndexOf('.') + 1).toLowerCase(Locale.ROOT);
        return TYPES.getOrDefault(ending, "application/octet-stream");
    }

    /**
     * The first and last byte a {@code Range} header asks for in a file of {@code size} bytes (all of it
     * without a header), or null if it asks for nothing the file has.
     */
    @Nullable
    static long[] range(@Nullable String header, long size) {
        if (header == null) {
            return size > 0 ? new long[] { 0, size - 1 } : new long[] { 0, -1 };
        }
        int dash = header.indexOf('-');
        if (!header.startsWith("bytes=") || dash < 0 || header.indexOf(',') >= 0) {
            return null;
        }
        String from = header.substring(6, dash).trim();
        String to = header.substring(dash + 1).trim();
        try {
            long start;
            long end = size - 1;
            if (from.isEmpty()) {
                // "-500": the last 500 bytes.
                start = Math.max(0, size - Long.parseLong(to));
            } else {
                start = Long.parseLong(from);
                if (!to.isEmpty()) {
                    end = Math.min(end, Long.parseLong(to));
                }
            }
            return start < 0 || start > end ? null : new long[] { start, end };
        } catch (NumberFormatException e) {
            return null;
        }
    }
}
