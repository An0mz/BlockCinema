package dev.anomz.blockcinema.client.screen;

import net.minecraft.Util;

/** One wheel notch sends several scroll events; this lets one skip through per moment. */
final class SkipGate {
    private static final long MILLIS = 400;
    private long last;

    boolean allow() {
        long now = Util.getMillis();
        if (now - last < MILLIS) {
            return false;
        }
        last = now;
        return true;
    }
}
