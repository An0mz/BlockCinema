package dev.anomz.blockcinema.client.browser;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import org.junit.jupiter.api.Test;

class SyncPlanTest {
    private static final long LONG_AGO = 60_000;

    @Test
    void inStepDoesNothing() {
        SyncPlan plan = SyncPlan.of(false, false, true, 100, 101, 600, false, LONG_AGO, LONG_AGO);
        assertNull(plan.setPaused());
        assertNull(plan.seekTo());
    }

    @Test
    void followsSharedPauseAndResume() {
        assertEquals(true, SyncPlan.of(true, false, true, 100, 100, 600, false, LONG_AGO, LONG_AGO).setPaused());
        assertEquals(false, SyncPlan.of(false, false, true, 100, 100, 600, true, LONG_AGO, LONG_AGO).setPaused());
    }

    @Test
    void doesNotResumeOutOfSightButStillPauses() {
        // A YouTube page out of sight paused itself; the shared state says play: leave it until seen.
        assertNull(SyncPlan.of(false, false, false, 100, 100, 600, true, LONG_AGO, LONG_AGO).setPaused());
        // Someone pauses meanwhile: that still goes through.
        assertEquals(true, SyncPlan.of(true, false, false, 100, 100, 600, false, LONG_AGO, LONG_AGO).setPaused());
    }

    @Test
    void singleplayerPausePausesAndNeverJumps() {
        SyncPlan plan = SyncPlan.of(false, true, true, 100, 140, 600, false, LONG_AGO, LONG_AGO);
        assertEquals(true, plan.setPaused());
        assertNull(plan.seekTo());
    }

    @Test
    void jumpsBackInStepOnlyWhenFarOffAndNotTooOften() {
        assertEquals(100.0, SyncPlan.of(false, false, true, 100, 90, 600, false, LONG_AGO, LONG_AGO).seekTo());
        // Within the drift allowed.
        assertNull(SyncPlan.of(false, false, true, 100, 98, 600, false, LONG_AGO, LONG_AGO).seekTo());
        // Jumped a moment ago.
        assertNull(SyncPlan.of(false, false, true, 100, 90, 600, false, LONG_AGO, 1000).seekTo());
        // Out of sight, live (no length), or the shared time is past the end.
        assertNull(SyncPlan.of(false, false, false, 100, 90, 600, false, LONG_AGO, LONG_AGO).seekTo());
        assertNull(SyncPlan.of(false, false, true, 100, 90, 0, false, LONG_AGO, LONG_AGO).seekTo());
        assertNull(SyncPlan.of(false, false, true, 700, 590, 600, false, LONG_AGO, LONG_AGO).seekTo());
    }

    @Test
    void pauseChangesAreRateLimited() {
        assertNull(SyncPlan.of(true, false, true, 100, 100, 600, false, 500, LONG_AGO).setPaused());
    }
}
