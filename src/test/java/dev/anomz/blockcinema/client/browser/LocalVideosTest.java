package dev.anomz.blockcinema.client.browser;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class LocalVideosTest {
    @Test
    void onlyFilesRightInTheFolderAreFound(@TempDir Path temp) throws IOException {
        Path folder = Files.createDirectory(temp.resolve("videos"));
        Files.writeString(folder.resolve("b film.mp4"), "b");
        Files.writeString(folder.resolve("A.webm"), "a");
        Files.writeString(folder.resolve(".hidden"), "h");
        Files.createDirectory(folder.resolve("sub"));
        Files.writeString(temp.resolve("secret.txt"), "s");

        assertEquals(List.of("A.webm", "b film.mp4"), LocalVideos.list(folder));
        assertNotNull(LocalVideos.file(folder, "b film.mp4"));
        assertNull(LocalVideos.file(folder, "missing.mp4"));
        assertNull(LocalVideos.file(folder, "sub"));
        assertNull(LocalVideos.file(folder, "../secret.txt"));
        assertNull(LocalVideos.file(folder, ".."));
        assertNull(LocalVideos.file(folder, temp.resolve("secret.txt").toString()));
    }

    @Test
    void rangesOfAFile() {
        assertArrayEquals(new long[] { 0, 99 }, LocalVideoServer.range(null, 100));
        assertArrayEquals(new long[] { 10, 99 }, LocalVideoServer.range("bytes=10-", 100));
        assertArrayEquals(new long[] { 10, 19 }, LocalVideoServer.range("bytes=10-19", 100));
        assertArrayEquals(new long[] { 10, 99 }, LocalVideoServer.range("bytes=10-5000", 100));
        assertArrayEquals(new long[] { 90, 99 }, LocalVideoServer.range("bytes=-10", 100));
        assertNull(LocalVideoServer.range("bytes=100-", 100));
        assertNull(LocalVideoServer.range("bytes=20-10", 100));
        assertNull(LocalVideoServer.range("bytes=0-1,5-6", 100));
        assertNull(LocalVideoServer.range("lines=1-2", 100));
        assertNull(LocalVideoServer.range("bytes=a-b", 100));
    }

    @Test
    void servesWholeFilesAndRangesButNothingElse(@TempDir Path temp) throws Exception {
        Path folder = Files.createDirectory(temp.resolve("videos"));
        Files.writeString(folder.resolve("my film+1.mp4"), "0123456789");
        Files.writeString(temp.resolve("secret.txt"), "secret");
        HttpClient client = HttpClient.newHttpClient();
        try (LocalVideoServer server = new LocalVideoServer(name -> LocalVideos.file(folder, name))) {
            String url = server.url("my film+1.mp4");

            HttpResponse<String> whole = get(client, url, null);
            assertEquals(200, whole.statusCode());
            assertEquals("0123456789", whole.body());
            assertEquals("video/mp4", whole.headers().firstValue("Content-Type").orElseThrow());

            HttpResponse<String> part = get(client, url, "bytes=2-5");
            assertEquals(206, part.statusCode());
            assertEquals("2345", part.body());
            assertEquals("bytes 2-5/10", part.headers().firstValue("Content-Range").orElseThrow());

            assertEquals(416, get(client, url, "bytes=50-").statusCode());
            assertEquals(404, get(client, server.url("missing.mp4"), null).statusCode());
            String base = url.substring(0, url.lastIndexOf('/'));
            assertEquals(404, get(client, base + "/..%2Fsecret.txt", null).statusCode());
            // Without the random part of the address, nothing is given out.
            String root = base.substring(0, base.lastIndexOf('/'));
            assertEquals(404, get(client, root + "/my%20film%2B1.mp4", null).statusCode());
            assertEquals(404, get(client, root + "/wrong/my%20film%2B1.mp4", null).statusCode());
        }
    }

    private static HttpResponse<String> get(HttpClient client, String url, String range) throws Exception {
        HttpRequest.Builder request = HttpRequest.newBuilder(URI.create(url));
        if (range != null) {
            request.header("Range", range);
        }
        return client.send(request.build(), HttpResponse.BodyHandlers.ofString());
    }
}
