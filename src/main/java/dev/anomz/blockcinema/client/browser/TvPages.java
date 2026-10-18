package dev.anomz.blockcinema.client.browser;

import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.util.Map;

import org.cef.browser.CefBrowser;
import org.cef.browser.CefFrame;
import org.cef.callback.CefCallback;
import org.cef.callback.CefSchemeHandlerFactory;
import org.cef.handler.CefResourceHandler;
import org.cef.misc.IntRef;
import org.cef.misc.StringRef;
import org.cef.network.CefRequest;
import org.cef.network.CefResponse;

import dev.anomz.blockcinema.BlockCinema;

/**
 * Our own player pages, served from the mod jar under {@link #HOST}. The host is the mod's app ID in
 * reverse-DNS form, as YouTube asks apps to identify themselves with; it is not a real domain, so no
 * request for it ever leaves the computer. Served over https, and also over http so the video page
 * can play from a home server without https (Chromium blocks http video on an https page).
 */
public final class TvPages implements CefSchemeHandlerFactory {
    public static final String HOST = "dev.anomz.blockcinema";
    public static final String ORIGIN = "https://" + HOST;
    private static final String WEB = "/assets/" + BlockCinema.MODID + "/web";

    private record File(String path, String mimeType) {
    }

    /** Page path to the file in the jar. Nothing else is served. */
    private static final Map<String, File> FILES = Map.of(
            "/youtube.html", new File(WEB + "/youtube.html", "text/html"),
            "/twitch.html", new File(WEB + "/twitch.html", "text/html"),
            "/video.html", new File(WEB + "/video.html", "text/html"),
            "/hls.min.js", new File(WEB + "/hls.min.js", "text/javascript"));

    /** Our origin with {@code scheme} (http or https). */
    public static String origin(String scheme) {
        return scheme + "://" + HOST;
    }

    @Override
    public CefResourceHandler create(CefBrowser browser, CefFrame frame, String schemeName, CefRequest request) {
        File file = FILES.get(URI.create(request.getURL()).getPath());
        return file == null ? null : new Page(file);
    }

    private static final class Page implements CefResourceHandler {
        private final File file;
        private byte[] data;
        private int offset;

        Page(File file) {
            this.file = file;
        }

        @Override
        public boolean processRequest(CefRequest request, CefCallback callback) {
            try (InputStream in = TvPages.class.getResourceAsStream(file.path())) {
                if (in == null) {
                    return false;
                }
                data = in.readAllBytes();
            } catch (IOException e) {
                BlockCinema.LOGGER.error("Couldn't read {}", file.path(), e);
                return false;
            }
            callback.Continue();
            return true;
        }

        @Override
        public void getResponseHeaders(CefResponse response, IntRef responseLength, StringRef redirectUrl) {
            response.setStatus(200);
            response.setMimeType(file.mimeType());
            responseLength.set(data.length);
        }

        @Override
        public boolean readResponse(byte[] dataOut, int bytesToRead, IntRef bytesRead, CefCallback callback) {
            int count = Math.min(bytesToRead, data.length - offset);
            if (count <= 0) {
                bytesRead.set(0);
                return false;
            }
            System.arraycopy(data, offset, dataOut, 0, count);
            offset += count;
            bytesRead.set(count);
            return true;
        }

        @Override
        public void cancel() {
        }
    }
}
