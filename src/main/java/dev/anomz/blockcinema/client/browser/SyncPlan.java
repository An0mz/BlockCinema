package dev.anomz.blockcinema.client.browser;

import org.jetbrains.annotations.Nullable;

/**
 * What a TV page should do to stay in step with the shared state: pause or resume, and/or jump to the
 * shared time. Gentle on purpose: a jump only when far off and not too often, so a buffering video isn't
 * pushed around. No Minecraft or browser classes, so tests can check it.
 *
 * @param setPaused true or false to pause or resume the page; null to leave it
 * @param seekTo seconds to jump to; null to leave it
 */
public record SyncPlan(@Nullable Boolean setPaused, @Nullable Double seekTo) {
    /** Being this far off the shared position makes the screen jump back in step. */
    static final double MAX_DRIFT_SECONDS = 3;
    /** At most one catch-up jump this often. */
    static final long SEEK_SYNC_MILLIS = 5000;
    static final long PAUSE_SYNC_MILLIS = 1500;

    private static final SyncPlan NOTHING = new SyncPlan(null, null);

    /**
     * @param sharedPaused whether the TV is paused for everyone
     * @param gamePaused singleplayer is paused here (the world's clock stands still)
     * @param visible the screen is in sight (an out-of-sight YouTube page pauses itself)
     * @param expected where the video should be, in seconds
     * @param time where the page is
     * @param length the video's length (0 when live or unknown)
     * @param pagePaused whether the page is paused
     * @param sincePauseSync milliseconds since the page was last told to pause or resume
     * @param sinceSeekSync milliseconds since the page was last told to jump
     */
    public static SyncPlan of(boolean sharedPaused, boolean gamePaused, boolean visible, double expected, double time,
            double length, boolean pagePaused, long sincePauseSync, long sinceSeekSync) {
        boolean paused = sharedPaused || gamePaused;
        Boolean setPaused = null;
        // An out-of-sight YouTube page pauses itself; it is brought back in step once seen again.
        if (paused != pagePaused && (visible || paused) && sincePauseSync > PAUSE_SYNC_MILLIS) {
            setPaused = paused;
        }
        Double seekTo = null;
        if (!gamePaused && visible && length > 0 && expected < length && Math.abs(time - expected) > MAX_DRIFT_SECONDS
                && sinceSeekSync > SEEK_SYNC_MILLIS) {
            seekTo = expected;
        }
        return setPaused == null && seekTo == null ? NOTHING : new SyncPlan(setPaused, seekTo);
    }
}
