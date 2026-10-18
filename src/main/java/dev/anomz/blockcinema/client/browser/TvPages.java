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
 * request for it ever leaves the computer.
 */
public final class TvPages implements CefSchemeHandlerFactory {
    public static final String HOST = "dev.anomz.blockcinema";
    public static final String ORIGIN = "https://" + HOST;

    /** Page path to the file in the jar. Nothing else is served. */
    private static final Map<String, String> PAGES = Map.of(
            "/youtube.html", "/assets/" + BlockCinema.MODID + "/web/youtube.html",
            "/twitch.html", "/assets/" + BlockCinema.MODID + "/web/twitch.html");

    @Override
    public CefResourceHandler create(CefBrowser browser, CefFrame frame, String schemeName, CefRequest request) {
        String file = PAGES.get(URI.create(request.getURL()).getPath());
        return file == null ? null : new Page(file);
    }

    private static final class Page implements CefResourceHandler {
        private final String file;
        private byte[] data;
        private int offset;

        Page(String file) {
            this.file = file;
        }

        @Override
        public boolean processRequest(CefRequest request, CefCallback callback) {
            try (InputStream in = TvPages.class.getResourceAsStream(file)) {
                if (in == null) {
                    return false;
                }
                data = in.readAllBytes();
            } catch (IOException e) {
                BlockCinema.LOGGER.error("Couldn't read {}", file, e);
                return false;
            }
            callback.Continue();
            return true;
        }

        @Override
        public void getResponseHeaders(CefResponse response, IntRef responseLength, StringRef redirectUrl) {
            response.setStatus(200);
            response.setMimeType("text/html");
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
