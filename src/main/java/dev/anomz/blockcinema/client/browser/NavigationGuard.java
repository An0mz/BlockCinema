package dev.anomz.blockcinema.client.browser;

import java.net.URI;
import java.net.URISyntaxException;

import org.cef.browser.CefBrowser;
import org.cef.browser.CefFrame;
import org.cef.handler.CefLifeSpanHandlerAdapter;
import org.cef.handler.CefRequestHandlerAdapter;
import org.cef.network.CefRequest;

/**
 * Keeps TV browsers on our own player pages: the page itself can only be one of ours (over https, or
 * http for home servers), and nothing can open a new window. The players embedded in our pages
 * (YouTube, Twitch) may load their own frames, which includes their ads, so frames are only held to
 * https. Videos the video page plays are not frames, so they aren't checked here. There is no address
 * bar and no mouse input on a TV, so nothing else can be browsed to.
 */
final class NavigationGuard {
    private NavigationGuard() {
    }

    static boolean allowed(String url, boolean mainFrame) {
        URI uri;
        try {
            uri = new URI(url);
        } catch (URISyntaxException e) {
            return false;
        }
        String scheme = uri.getScheme();
        if (mainFrame) {
            return ("https".equals(scheme) || "http".equals(scheme)) && TvPages.HOST.equals(uri.getHost());
        }
        return "https".equals(scheme) || "about".equals(scheme);
    }

    static final class Requests extends CefRequestHandlerAdapter {
        @Override
        public boolean onBeforeBrowse(CefBrowser browser, CefFrame frame, CefRequest request, boolean userGesture,
                boolean isRedirect) {
            // Returning true cancels the navigation.
            return !allowed(request.getURL(), frame.isMain());
        }

        @Override
        public boolean onOpenURLFromTab(CefBrowser browser, CefFrame frame, String targetUrl, boolean userGesture) {
            return true;
        }
    }

    static final class Popups extends CefLifeSpanHandlerAdapter {
        @Override
        public boolean onBeforePopup(CefBrowser browser, CefFrame frame, String targetUrl, String targetFrameName) {
            return true;
        }
    }
}
