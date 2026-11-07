package dev.anomz.blockcinema;

/** A client's estimate of the server's {@link TvClock}, run on this computer's own time between reports so it stays smooth. */
public final class TvClockFollower {
    public static final TvClockFollower INSTANCE = new TvClockFollower();

    /** A report this far from the estimate is taken as is; smaller differences are network jitter and only nudge it. */
    private static final double SNAP_TICKS = TvSource.TICKS_PER_SECOND;
    private static final double NUDGE = 0.1;

    private boolean known;
    private double baseTicks;
    private long baseMillis;

    TvClockFollower() {
    }

    public void report(long serverTicks, long nowMillis) {
        double estimate = estimate(nowMillis);
        double difference = serverTicks - estimate;
        baseTicks = !known || Math.abs(difference) > SNAP_TICKS ? serverTicks : estimate + difference * NUDGE;
        baseMillis = nowMillis;
        known = true;
    }

    public void hold(long nowMillis) {
        baseMillis = nowMillis;
    }

    public void reset() {
        known = false;
    }

    public long now(long gameTime, long nowMillis) {
        return known ? (long) estimate(nowMillis) : gameTime;
    }

    private double estimate(long nowMillis) {
        return baseTicks + (nowMillis - baseMillis) * TvSource.TICKS_PER_SECOND / 1000.0;
    }
}
