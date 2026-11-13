package dev.anomz.blockcinema.client.browser;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

class TvVolumeTest {
    @Test
    void fadesLinearlyBetweenFullAndSilent() {
        assertEquals(1, TvVolume.falloff(0));
        assertEquals(1, TvVolume.falloff(TvVolume.FULL_DISTANCE));
        assertEquals(0.5f, TvVolume.falloff((TvVolume.FULL_DISTANCE + TvVolume.SILENT_DISTANCE) / 2), 1e-6);
        assertEquals(0, TvVolume.falloff(TvVolume.SILENT_DISTANCE));
        assertEquals(0, TvVolume.falloff(1000));
    }

    @Test
    void soundComesFromTheSideTheScreenIsOn() {
        // Yaw 0 looks south (+z), so west (-x) is to the right.
        assertEquals(0, TvVolume.pan(0, 10, 0), 1e-6);
        assertEquals(TvVolume.MAX_PAN, TvVolume.pan(-10, 0, 0), 1e-6);
        assertEquals(-TvVolume.MAX_PAN, TvVolume.pan(10, 0, 0), 1e-6);
        assertEquals(-TvVolume.MAX_PAN, TvVolume.pan(0, 10, 90), 1e-6);
        assertEquals(0, TvVolume.pan(0, 0, 0));
        assertEquals(TvVolume.MAX_PAN / 2, TvVolume.pan(-TvVolume.FULL_DISTANCE / 2, 0, 0), 1e-6);
    }

    @Test
    void distanceIsToTheNearestPointOfTheScreen() {
        // A 4x2 wall from (0,0,0) to (4,2,1).
        assertEquals(0, TvVolume.distanceToBox(2, 1, 0.5, 0, 0, 0, 4, 2, 1));
        assertEquals(3, TvVolume.distanceToBox(3.5, 1, -3, 0, 0, 0, 4, 2, 1), 1e-9);
        assertEquals(5, TvVolume.distanceToBox(7, 6, 0.5, 0, 0, 0, 4, 2, 1), 1e-9);
    }
}
