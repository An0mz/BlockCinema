package dev.anomz.blockcinema;

/**
 * A client's estimate of the server's {@link TvClock}: it runs on this computer's own time between the
 * server's reports, so it stays smooth when the server or this game stutters. No Minecraft classes, so
 * tests can check it. Client thread only.
 */
public final class TvClockFollower {
    /** This client's follower. */
    public static final TvClockFollower INSTANCE = new TvClockFollower();

    /** A report this far from the estimate is taken as is; smaller differences are network jitter and only nudge it. */
    private static final double SNAP_TICKS = TvSource.TICKS_PER_SECOND;
    private static final double NUDGE = 0.1;

    private boolean known;
    private double baseTicks;
    private long baseMillis;

    TvClockFollower() {
    }

    /** The server says its clock is at {@code serverTicks}. */
    public void report(long serverTicks, long nowMillis) {
        double estimate = estimate(nowMillis);
        double difference = serverTicks - estimate;
        baseTicks = !known || Math.abs(difference) > SNAP_TICKS ? serverTicks : estimate + difference * NUDGE;
        baseMillis = nowMillis;
        known = true;
    }

    /** The clock stands still for now (singleplayer paused: the server isn't ticking or reporting). */
    public void hold(long nowMillis) {
        baseMillis = nowMillis;
    }

    /** Back to not knowing (left the world). */
    public void reset() {
        known = false;
    }

    /** The clock now, in ticks; {@code gameTime} until the server's first report. */
    public long now(long gameTime, long nowMillis) {
        return known ? (long) estimate(nowMillis) : gameTime;
    }

    private double estimate(long nowMillis) {
        return baseTicks + (nowMillis - baseMillis) * TvSource.TICKS_PER_SECOND / 1000.0;
    }
}
