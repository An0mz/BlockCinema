package dev.anomz.blockcinema;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.Test;

class TvPageRulesTest {
    @Test
    void youtubePages() {
        assertTrue(TvPageRules.valid(TvService.YOUTUBE, "https://blockcinema.anomz.dev/youtube.html?v=dQw4w9WgXcQ"));
        assertTrue(TvPageRules.valid(TvService.YOUTUBE, "https://blockcinema.anomz.dev/youtube.html?v=dQw4w9WgXcQ&list=PL1234567890"));
        assertTrue(TvPageRules.valid(TvService.YOUTUBE, "https://blockcinema.anomz.dev/youtube.html?list=PL1234567890"));
        assertFalse(TvPageRules.valid(TvService.YOUTUBE, "https://blockcinema.anomz.dev/youtube.html"));
        assertFalse(TvPageRules.valid(TvService.YOUTUBE, "https://blockcinema.anomz.dev/youtube.html?v=dQw4w9WgXcQ&x=1"));
        assertFalse(TvPageRules.valid(TvService.YOUTUBE, "https://blockcinema.anomz.dev/youtube.html?v=%3Cscript%3E123"));
        assertFalse(TvPageRules.valid(TvService.YOUTUBE, "https://evil.com/youtube.html?v=dQw4w9WgXcQ"));
        assertFalse(TvPageRules.valid(TvService.YOUTUBE, "http://blockcinema.anomz.dev/youtube.html?v=dQw4w9WgXcQ"));
        assertFalse(TvPageRules.valid(TvService.YOUTUBE, "https://blockcinema.anomz.dev/youtube.html?v=dQw4w9WgXcQ#auth=x"));
        assertFalse(TvPageRules.valid(TvService.YOUTUBE, "https://blockcinema.anomz.dev/twitch.html?channel=abc"));
    }

    @Test
    void twitchAndLinkPages() {
        assertTrue(TvPageRules.valid(TvService.TWITCH, "https://blockcinema.anomz.dev/twitch.html?channel=some_channel"));
        assertFalse(TvPageRules.valid(TvService.TWITCH, "https://blockcinema.anomz.dev/twitch.html?channel=Bad%20Name"));
        String link = "https://blockcinema.anomz.dev/video.html?src=https%3A%2F%2Fcdn.example.com%2Fa.mp4";
        assertTrue(TvPageRules.valid(TvService.DIRECT_LINK, link));
        assertEquals("cdn.example.com", TvPageRules.linkHost(link));
        assertTrue(TvPageRules.valid(TvService.DIRECT_LINK, "http://blockcinema.anomz.dev/video.html?src=http%3A%2F%2F10.0.0.2%2Fa.m3u8"));
        // The page's scheme must match the video's.
        assertFalse(TvPageRules.valid(TvService.DIRECT_LINK, "https://blockcinema.anomz.dev/video.html?src=http%3A%2F%2F10.0.0.2%2Fa.m3u8"));
        assertFalse(TvPageRules.valid(TvService.DIRECT_LINK, "https://blockcinema.anomz.dev/video.html?src=file%3A%2F%2F%2Fc%3A%2Fa.mp4"));
        assertNull(TvPageRules.linkHost("https://blockcinema.anomz.dev/video.html"));
        assertFalse(TvPageRules.valid(TvService.JELLYFIN, link));
    }

    @Test
    void jellyfinSharesOnlyIds() {
        String server = "0123456789abcdef0123456789abcdef";
        String item = "f27caa37e5142225cceded48f6553502";
        String page = TvPageRules.jellyfinPage(server.toUpperCase(), item);
        assertEquals("jellyfin:" + server + ":" + item, page);
        assertTrue(TvPageRules.valid(TvService.JELLYFIN, page));
        assertEquals(item, TvPageRules.jellyfinIds(page)[1]);
        assertFalse(TvPageRules.valid(TvService.YOUTUBE, page));
        assertFalse(TvPageRules.valid(TvService.JELLYFIN, "jellyfin:" + server));
        assertFalse(TvPageRules.valid(TvService.JELLYFIN, page + ":x"));
        String withTracks = TvPageRules.jellyfinPage(server, item, 2, -1);
        assertEquals(page + ":2:-1", withTracks);
        assertTrue(TvPageRules.valid(TvService.JELLYFIN, withTracks));
        assertEquals(2, TvPageRules.jellyfinTracks(withTracks)[0]);
        assertEquals(-1, TvPageRules.jellyfinTracks(page)[1]);
        assertFalse(TvPageRules.valid(TvService.JELLYFIN, page + ":2:-2"));
        assertFalse(TvPageRules.valid(TvService.JELLYFIN, page + ":2:1000"));
        assertFalse(TvPageRules.valid(TvService.JELLYFIN, "jellyfin:http://192.168.1.5:8096:" + item));
        assertFalse(TvPageRules.valid(TvService.JELLYFIN, ""));
    }

    @Test
    void linkHostList() {
        assertTrue(TvPageRules.hostAllowed(List.of(), "anything.com"));
        assertTrue(TvPageRules.hostAllowed(List.of("example.com"), "cdn.Example.com"));
        assertTrue(TvPageRules.hostAllowed(List.of("example.com"), "example.com"));
        assertFalse(TvPageRules.hostAllowed(List.of("example.com"), "badexample.com"));
    }

    @Test
    void queueTitlesAreCleaned() {
        assertEquals("Movie night", TvQueueEntry.cleanTitle("  Movie\u0001 night\n "));
        assertEquals(TvQueueEntry.MAX_TITLE_LENGTH, TvQueueEntry.cleanTitle("x".repeat(500)).length());
    }

    @Test
    void positionFollowsGameTime() {
        TvSource source = new TvSource(TvService.YOUTUBE, "p", UUID.randomUUID(), "Dev", 100, 30, false);
        assertEquals(30, source.position(100));
        assertEquals(35, source.position(200));
        TvSource paused = source.pausedAt(200);
        assertEquals(35, paused.position(1000));
        assertEquals(36, paused.resumedAt(1000).position(1020));
        assertEquals(0, source.seekedTo(300, -5).position(300));
    }
}
