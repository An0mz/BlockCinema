package dev.anomz.blockcinema;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

class TvClockTest {
    private static final long MAX_GAP = 60_000;

    @Test
    void aHealthyServerLosesNothing() {
        assertEquals(0, TvClockLag.lostAfter(0, 50, MAX_GAP));
    }

    @Test
    void aLaggingServerLosesTicks() {
        // A tick that took 100 ms is one tick late.
        assertEquals(1, TvClockLag.lostAfter(0, 100, MAX_GAP));
        // 15 TPS for a minute: the clock still moves 20 ticks a second.
        double lost = 0;
        long ticks = 15 * 60;
        for (int i = 0; i < ticks; i++) {
            lost = TvClockLag.lostAfter(lost, 1000 / 15, MAX_GAP);
        }
        assertEquals(60 * 20, ticks + lost, 15);
    }

    @Test
    void catchingUpWinsItBack() {
        assertEquals(2.5, TvClockLag.lostAfter(3, 25, MAX_GAP));
        assertEquals(0, TvClockLag.lostAfter(0, 25, MAX_GAP));
    }

    @Test
    void aPauseIsNotLag() {
        // Singleplayer back from the pause menu after 10 minutes: at most the allowed gap counts.
        assertEquals(19, TvClockLag.lostAfter(0, 600_000, 1000));
    }

    @Test
    void theFollowerRunsOnItsOwnTimeBetweenReports() {
        TvClockFollower follower = new TvClockFollower();
        // Game time until the first report.
        assertEquals(7, follower.now(7, 1000));
        follower.report(2000, 1000);
        assertEquals(2020, follower.now(7, 2000));
    }

    @Test
    void theFollowerSmoothsJitterButTakesBigChanges() {
        TvClockFollower follower = new TvClockFollower();
        follower.report(2000, 1000);
        // A report 5 ticks off moves it only a little.
        follower.report(2025, 2000);
        assertEquals(2020, follower.now(0, 2000));
        // 10 seconds off: taken as is.
        follower.report(2220, 2000);
        assertEquals(2220, follower.now(0, 2000));
    }

    @Test
    void theFollowerHoldsWhilePaused() {
        TvClockFollower follower = new TvClockFollower();
        follower.report(2000, 1000);
        follower.hold(1000);
        follower.hold(60_000);
        assertEquals(2000, follower.now(0, 60_000));
    }
}
