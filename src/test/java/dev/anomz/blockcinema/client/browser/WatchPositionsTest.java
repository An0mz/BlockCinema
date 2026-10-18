package dev.anomz.blockcinema.client.browser;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.nio.file.Path;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class WatchPositionsTest {
    @TempDir
    Path dir;

    @Test
    void remembersAndSurvivesAReload() {
        Path file = dir.resolve("positions.json");
        new WatchPositions(file).remember("a", 125.7, 600);
        assertEquals(125, new WatchPositions(file).get("a"));
        assertEquals(0, new WatchPositions(file).get("unknown"));
    }

    @Test
    void startAndEndAndLiveAreNotKept() {
        WatchPositions positions = new WatchPositions(dir.resolve("positions.json"));
        positions.remember("a", 300, 600);
        positions.remember("a", 590, 600);
        assertEquals(0, positions.get("a"));
        positions.remember("b", 5, 600);
        assertEquals(0, positions.get("b"));
        positions.remember("live", 300, 0);
        assertEquals(0, positions.get("live"));
    }

    @Test
    void forgetsTheOldestBeyondTheLimit() {
        WatchPositions positions = new WatchPositions(dir.resolve("positions.json"));
        for (int i = 0; i <= WatchPositions.MAX_ENTRIES; i++) {
            positions.remember("v" + i, 100, 600);
        }
        assertEquals(0, positions.get("v0"));
        assertEquals(100, positions.get("v" + WatchPositions.MAX_ENTRIES));
    }
}
