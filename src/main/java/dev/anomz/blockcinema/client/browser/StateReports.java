package dev.anomz.blockcinema.client.browser;

import java.net.URI;
import java.net.URISyntaxException;

import org.cef.CefSettings;
import org.cef.browser.CefBrowser;
import org.cef.handler.CefDisplayHandlerAdapter;

import net.minecraft.client.Minecraft;

/**
 * Reads the playback reports our pages write to the browser console (java-cef can't return values from
 * JavaScript). Only lines from our own pages count; anything a service's player logs is left alone.
 */
final class StateReports extends CefDisplayHandlerAdapter {
    static final String PREFIX = "[blockcinema-state] ";

    /** Time, length and paused from a report line, or null if it isn't one. */
    static double[] parse(String message) {
        if (!message.startsWith(PREFIX)) {
            return null;
        }
        String[] parts = message.substring(PREFIX.length()).split(" ");
        if (parts.length != 3) {
            return null;
        }
        try {
            double time = Double.parseDouble(parts[0]);
            double length = Double.parseDouble(parts[1]);
            double paused = Double.parseDouble(parts[2]);
            if (!Double.isFinite(time) || !Double.isFinite(length) || time < 0 || length < 0) {
                return null;
            }
            return new double[] { time, length, paused };
        } catch (NumberFormatException e) {
            return null;
        }
    }

    static boolean fromOurPage(String source) {
        try {
            return TvPages.HOST.equals(new URI(source).getHost());
        } catch (URISyntaxException | NullPointerException e) {
            return false;
        }
    }

    @Override
    public boolean onConsoleMessage(CefBrowser browser, CefSettings.LogSeverity level, String message, String source,
            int line) {
        double[] report = parse(message);
        if (report == null || !fromOurPage(source)) {
            return false;
        }
        Minecraft.getInstance().execute(() -> TvPlayers.report(browser, report[0], report[1], report[2] != 0));
        // Handled: keeps a line a second out of Chromium's log.
        return true;
    }
}
