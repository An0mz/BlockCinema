package dev.anomz.blockcinema;

import java.util.UUID;

/**
 * What a TV screen plays, anchored to the shared {@link TvClock} so every client works out the same
 * position. The video never goes through the server; each client plays {@code page} itself.
 */
public record TvSource(TvService service, String page, UUID owner, String ownerName, long anchorTick,
        double anchorSeconds, boolean paused) {
    public static final int TICKS_PER_SECOND = 20;

    public double position(long clockTicks) {
        return paused ? anchorSeconds : anchorSeconds + Math.max(0, clockTicks - anchorTick) / (double) TICKS_PER_SECOND;
    }

    public TvSource pausedAt(long clockTicks) {
        return paused ? this : new TvSource(service, page, owner, ownerName, clockTicks, position(clockTicks), true);
    }

    public TvSource resumedAt(long clockTicks) {
        return paused ? new TvSource(service, page, owner, ownerName, clockTicks, anchorSeconds, false) : this;
    }

    public TvSource seekedTo(long clockTicks, double seconds) {
        return new TvSource(service, page, owner, ownerName, clockTicks, Math.max(0, seconds), paused);
    }
}
