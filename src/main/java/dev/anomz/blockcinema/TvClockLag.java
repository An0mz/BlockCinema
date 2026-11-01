package dev.anomz.blockcinema;

/** How far a lagging server's ticks fall behind real time (see {@link TvClock}). No Minecraft classes, so tests can check it. */
final class TvClockLag {
    private static final double MILLIS_PER_TICK = 1000.0 / TvSource.TICKS_PER_SECOND;

    private TvClockLag() {
    }

    /**
     * Ticks not reached, after a tick that took {@code elapsedMillis} since the last. It shrinks again when
     * a server catches up by ticking faster, so the clock always moves on at real speed.
     */
    static double lostAfter(double lostTicks, long elapsedMillis, long maxGapMillis) {
        return Math.max(0, lostTicks + (Math.min(elapsedMillis, maxGapMillis) - MILLIS_PER_TICK) / MILLIS_PER_TICK);
    }
}
