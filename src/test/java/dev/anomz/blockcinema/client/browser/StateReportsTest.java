package dev.anomz.blockcinema.client.browser;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class StateReportsTest {
    @Test
    void parsesReports() {
        assertArrayEquals(new double[] { 12.5, 600, 1 }, StateReports.parse("[blockcinema-state] 12.5 600.0 1"));
        assertArrayEquals(new double[] { 3, 0, 0 }, StateReports.parse("[blockcinema-state] 3.0 0 0"));
        assertNull(StateReports.parse("[blockcinema-state] 1 2"));
        assertNull(StateReports.parse("[blockcinema-state] NaN 2 0"));
        assertNull(StateReports.parse("[blockcinema-state] -1 2 0"));
        assertNull(StateReports.parse("something else"));
    }

    @Test
    void parsesErrors() {
        assertEquals("no_embed", StateReports.parseError("[blockcinema-error] no_embed"));
        assertEquals("none", StateReports.parseError("[blockcinema-error] none"));
        assertNull(StateReports.parseError("[blockcinema-error] <b>"));
        assertNull(StateReports.parseError("[blockcinema-error] "));
        assertNull(StateReports.parseError("[blockcinema-state] 1 2 0"));
    }

    @Test
    void parsesPlaylistReports() {
        assertEquals(3, StateReports.parseNumber("[blockcinema-list] 3", StateReports.LIST_PREFIX));
        assertEquals(4, StateReports.parseNumber("[blockcinema-ended] 4", StateReports.ENDED + " "));
        assertEquals(-1, StateReports.parseNumber("[blockcinema-list] -1", StateReports.LIST_PREFIX));
        assertEquals(-1, StateReports.parseNumber("[blockcinema-list] x", StateReports.LIST_PREFIX));
    }

    @Test
    void onlyOurPagesCount() {
        assertTrue(StateReports.fromOurPage("https://blockcinema.anomz.dev/youtube.html?v=dQw4w9WgXcQ"));
        assertTrue(StateReports.fromOurPage("http://blockcinema.anomz.dev/video.html?src=x"));
        assertFalse(StateReports.fromOurPage("https://www.youtube.com/s/player/base.js"));
        assertFalse(StateReports.fromOurPage(""));
    }
}
