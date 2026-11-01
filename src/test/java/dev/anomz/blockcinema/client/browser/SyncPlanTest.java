package dev.anomz.blockcinema.client.browser;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import org.junit.jupiter.api.Test;

class SyncPlanTest {
    private static final long LONG_AGO = 60_000;

    /** Playing, in sight, a 10-minute video, nothing done lately. */
    private static SyncPlan playing(double expected, double time) {
        return SyncPlan.of(input(false, false, true, expected, time, 600, false, false, 1, LONG_AGO, LONG_AGO, 0));
    }

    private static SyncPlan.Input input(boolean sharedPaused, boolean gamePaused, boolean visible, double expected, double time,
            double length, boolean pagePaused, boolean loading, double rate, long sincePauseSync, long sinceSeekSync, double lead) {
        return new SyncPlan.Input(sharedPaused, gamePaused, visible, expected, time, length, pagePaused, loading, rate,
                sincePauseSync, sinceSeekSync, SyncPlan.SEEK_SYNC_MILLIS, lead);
    }

    @Test
    void inStepDoesNothing() {
        SyncPlan plan = playing(100, 100.2);
        assertNull(plan.setPaused());
        assertNull(plan.seekTo());
        assertNull(plan.setRate());
    }

    @Test
    void followsSharedPauseAndResume() {
        assertEquals(true, SyncPlan.of(input(true, false, true, 100, 100, 600, false, false, 1, LONG_AGO, LONG_AGO, 0)).setPaused());
        assertEquals(false, SyncPlan.of(input(false, false, true, 100, 100, 600, true, false, 1, LONG_AGO, LONG_AGO, 0)).setPaused());
    }

    @Test
    void doesNotResumeOutOfSightButStillPauses() {
        // A YouTube page out of sight paused itself; the shared state says play: leave it until seen.
        assertNull(SyncPlan.of(input(false, false, false, 100, 100, 600, true, false, 1, LONG_AGO, LONG_AGO, 0)).setPaused());
        // Someone pauses meanwhile: that still goes through.
        assertEquals(true, SyncPlan.of(input(true, false, false, 100, 100, 600, false, false, 1, LONG_AGO, LONG_AGO, 0)).setPaused());
    }

    @Test
    void singleplayerPausePausesAndNeverJumps() {
        SyncPlan plan = SyncPlan.of(input(false, true, true, 100, 140, 600, false, false, 1, LONG_AGO, LONG_AGO, 0));
        assertEquals(true, plan.setPaused());
        assertNull(plan.seekTo());
    }

    @Test
    void jumpsBackInStepOnlyWhenFarOffAndNotTooOften() {
        assertEquals(100.0, playing(100, 90).seekTo());
        // Within the drift allowed.
        assertNull(playing(100, 98).seekTo());
        // Jumped a moment ago.
        assertNull(SyncPlan.of(input(false, false, true, 100, 90, 600, false, false, 1, LONG_AGO, 1000, 0)).seekTo());
        // Out of sight, live (no length), or the shared time is past the end.
        assertNull(SyncPlan.of(input(false, false, false, 100, 90, 600, false, false, 1, LONG_AGO, LONG_AGO, 0)).seekTo());
        assertNull(SyncPlan.of(input(false, false, true, 100, 90, 0, false, false, 1, LONG_AGO, LONG_AGO, 0)).seekTo());
        assertNull(playing(700, 590).seekTo());
    }

    @Test
    void aLiveStreamIsNeverMoved() {
        // Live reports no length: 3 hours into the stream, the TV's own time of 30 s means nothing.
        SyncPlan plan = SyncPlan.of(input(false, false, true, 30, 10800, 0, false, false, 1, LONG_AGO, LONG_AGO, 0));
        assertNull(plan.seekTo());
        assertNull(plan.setRate());
    }

    @Test
    void doesNotJumpAgainWhileStillLoading() {
        // A long video can take more than the cooldown to load after a jump; jumping again would only restart that.
        assertNull(SyncPlan.of(input(false, false, true, 100, 90, 600, false, true, 1, LONG_AGO, 8000, 0)).seekTo());
        // But stuck for long, or someone just jumped (time since the last jump counts as long ago): it goes.
        assertEquals(100.0, SyncPlan.of(input(false, false, true, 100, 90, 600, false, true, 1, LONG_AGO, 25_000, 0)).seekTo());
    }

    @Test
    void jumpsFurtherByWhatLoadingTakes() {
        assertEquals(104.0, SyncPlan.of(input(false, false, true, 100, 90, 600, false, false, 1, LONG_AGO, LONG_AGO, 4)).seekTo());
        // Never past the end.
        assertEquals(600.0, SyncPlan.of(input(false, false, true, 599, 500, 600, false, false, 1, LONG_AGO, LONG_AGO, 4)).seekTo());
    }

    @Test
    void whilePausedTheSamePictureForEveryone() {
        // Small differences are put right at once, as nothing is playing; no lead, as the time stands still.
        assertEquals(100.0, SyncPlan.of(input(true, false, true, 100, 101, 600, true, false, 1, LONG_AGO, LONG_AGO, 4)).seekTo());
        assertNull(SyncPlan.of(input(true, false, true, 100, 100.3, 600, true, false, 1, LONG_AGO, LONG_AGO, 4)).seekTo());
    }

    @Test
    void smallDifferencesArePlayedAway() {
        assertEquals(SyncPlan.FASTER, playing(100, 99).setRate());
        assertEquals(SyncPlan.SLOWER, playing(100, 101).setRate());
        // Under the start: left alone.
        assertNull(playing(100, 99.7).setRate());
        // Far off: a jump does it, at normal speed.
        assertNull(playing(100, 90).setRate());
    }

    @Test
    void playsAwayUntilClose() {
        // Not close yet: keeps going, nothing new to tell the page.
        assertNull(SyncPlan.of(input(false, false, true, 100, 99.7, 600, false, false, SyncPlan.FASTER, LONG_AGO, LONG_AGO, 0)).setRate());
        // Close: back to normal.
        assertEquals(1.0, SyncPlan.of(input(false, false, true, 100, 99.9, 600, false, false, SyncPlan.FASTER, LONG_AGO, LONG_AGO, 0)).setRate());
        // Went past it: back to normal, not on and on.
        assertEquals(1.0, SyncPlan.of(input(false, false, true, 100, 100.4, 600, false, false, SyncPlan.FASTER, LONG_AGO, LONG_AGO, 0)).setRate());
        // Loading or a jump: back to normal.
        assertEquals(1.0, SyncPlan.of(input(false, false, true, 100, 99, 600, false, true, SyncPlan.FASTER, LONG_AGO, LONG_AGO, 0)).setRate());
        assertEquals(1.0, SyncPlan.of(input(false, false, true, 100, 90, 600, false, false, SyncPlan.FASTER, LONG_AGO, LONG_AGO, 0)).setRate());
    }

    @Test
    void pauseChangesAreRateLimited() {
        assertNull(SyncPlan.of(input(true, false, true, 100, 100, 600, false, false, 1, 500, LONG_AGO, 0)).setPaused());
    }

    @Test
    void jumpsThatDontHelpComeLessOften() {
        assertEquals(10_000, SyncPlan.nextSeekCooldown(5000, 8));
        assertEquals(SyncPlan.MAX_SEEK_SYNC_MILLIS, SyncPlan.nextSeekCooldown(50_000, -8));
        assertEquals(SyncPlan.SEEK_SYNC_MILLIS, SyncPlan.nextSeekCooldown(40_000, 1));
    }
}
