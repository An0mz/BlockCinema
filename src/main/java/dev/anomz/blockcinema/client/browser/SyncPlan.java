package dev.anomz.blockcinema.client.browser;

import org.jetbrains.annotations.Nullable;

/**
 * What a TV page should do to stay in step with the shared state: pause or resume, jump, or play a little
 * faster or slower for a while. No Minecraft or browser classes, so tests can check it.
 */
public record SyncPlan(@Nullable Boolean setPaused, @Nullable Double seekTo, @Nullable Double setRate) {
    static final double MAX_DRIFT_SECONDS = 3;
    /** Being this far off while paused makes it jump: nothing is playing, so it costs nothing, and all show the same picture. */
    static final double MAX_PAUSED_DRIFT_SECONDS = 0.5;
    /** Being this far off while playing starts playing it away... */
    static final double NUDGE_START_SECONDS = 0.5;
    /** ...until it is this close. */
    static final double NUDGE_STOP_SECONDS = 0.15;
    /** Speeds YouTube's player offers too. */
    static final double FASTER = 1.25;
    static final double SLOWER = 0.75;
    static final long SEEK_SYNC_MILLIS = 5000;
    static final long MAX_SEEK_SYNC_MILLIS = 60_000;
    /** A video still loading this long after the last jump is stuck, so jumping may help after all. */
    static final long LOADING_PATIENCE_MILLIS = 20_000;
    static final long PAUSE_SYNC_MILLIS = 1500;

    private static final SyncPlan NOTHING = new SyncPlan(null, null, null);

    /** Times are in seconds, except {@code sincePauseSync}, {@code sinceSeekSync} and {@code seekCooldown}, which are milliseconds. */
    public record Input(boolean sharedPaused, boolean gamePaused, boolean visible, double expected, double time, double length,
            boolean pagePaused, boolean loading, double rate, long sincePauseSync, long sinceSeekSync, long seekCooldown,
            double seekLead) {
    }

    public static SyncPlan of(Input in) {
        boolean paused = in.sharedPaused() || in.gamePaused();
        Boolean setPaused = null;
        // An out-of-sight YouTube page pauses itself; it is brought back in step once seen again.
        if (paused != in.pagePaused() && (in.visible() || paused) && in.sincePauseSync() > PAUSE_SYNC_MILLIS) {
            setPaused = paused;
        }
        boolean adjustable = !in.gamePaused() && in.visible() && in.length() > 0 && in.expected() < in.length();
        double behind = in.expected() - in.time();
        Double seekTo = null;
        if (adjustable && Math.abs(behind) > (paused ? MAX_PAUSED_DRIFT_SECONDS : MAX_DRIFT_SECONDS)
                && in.sinceSeekSync() > in.seekCooldown()
                && (!in.loading() || in.sinceSeekSync() > LOADING_PATIENCE_MILLIS)) {
            seekTo = paused ? in.expected() : Math.min(in.expected() + in.seekLead(), in.length());
        }
        double rate = adjustable && !paused && !in.loading() && seekTo == null ? nudge(behind, in.rate()) : 1;
        Double setRate = rate != in.rate() ? rate : null;
        return setPaused == null && seekTo == null && setRate == null ? NOTHING : new SyncPlan(setPaused, seekTo, setRate);
    }

    private static double nudge(double behind, double rate) {
        if (rate != 1) {
            // Keep going until close, or it has gone past the shared position.
            return Math.abs(behind) > NUDGE_STOP_SECONDS && behind > 0 == rate > 1 ? rate : 1;
        }
        return Math.abs(behind) <= NUDGE_START_SECONDS ? 1 : behind > 0 ? FASTER : SLOWER;
    }

    /**
     * The least time until the next jump, once the last one has loaded: a jump that didn't bring it in
     * step doubles it, so a connection too slow to keep up isn't pushed around, which would only make the
     * video load over and over.
     */
    static long nextSeekCooldown(long cooldown, double driftAfterJump) {
        return Math.abs(driftAfterJump) > MAX_DRIFT_SECONDS ? Math.min(cooldown * 2, MAX_SEEK_SYNC_MILLIS) : SEEK_SYNC_MILLIS;
    }
}
