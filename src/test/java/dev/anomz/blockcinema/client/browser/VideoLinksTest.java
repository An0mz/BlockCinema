package dev.anomz.blockcinema.client.browser;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Optional;

import org.junit.jupiter.api.Test;

class VideoLinksTest {
    private static final String YT = "https://dev.anomz.blockcinema/youtube.html?";

    @Test
    void youtubeLinkForms() {
        assertEquals(Optional.of(YT + "v=dQw4w9WgXcQ"), VideoLinks.youtube("dQw4w9WgXcQ"));
        assertEquals(Optional.of(YT + "v=dQw4w9WgXcQ"), VideoLinks.youtube("https://www.youtube.com/watch?v=dQw4w9WgXcQ&t=42"));
        assertEquals(Optional.of(YT + "v=dQw4w9WgXcQ"), VideoLinks.youtube("youtu.be/dQw4w9WgXcQ?si=abc"));
        assertEquals(Optional.of(YT + "v=dQw4w9WgXcQ"), VideoLinks.youtube("https://youtube.com/shorts/dQw4w9WgXcQ"));
        assertEquals(Optional.of(YT + "v=dQw4w9WgXcQ"), VideoLinks.youtube("https://m.youtube.com/live/dQw4w9WgXcQ"));
        assertEquals(Optional.of(YT + "list=PL1234567890"), VideoLinks.youtube("https://www.youtube.com/playlist?list=PL1234567890"));
        assertEquals(Optional.of(YT + "v=dQw4w9WgXcQ&list=PL1234567890"),
                VideoLinks.youtube("https://www.youtube.com/watch?v=dQw4w9WgXcQ&list=PL1234567890"));
    }

    @Test
    void youtubeRejectsOtherSitesAndBadIds() {
        assertTrue(VideoLinks.youtube("https://example.com/watch?v=dQw4w9WgXcQ").isEmpty());
        assertTrue(VideoLinks.youtube("https://www.youtube.com.evil.com/watch?v=dQw4w9WgXcQ").isEmpty());
        assertTrue(VideoLinks.youtube("https://www.youtube.com/watch?v=<script>").isEmpty());
        assertTrue(VideoLinks.youtube("javascript:alert(1)").isEmpty());
        assertTrue(VideoLinks.youtube("https://www.youtube.com/").isEmpty());
        assertTrue(VideoLinks.youtube("").isEmpty());
    }

    @Test
    void twitchChannels() {
        String page = "https://dev.anomz.blockcinema/twitch.html?channel=some_channel";
        assertEquals(Optional.of(page), VideoLinks.twitch("Some_Channel"));
        assertEquals(Optional.of(page), VideoLinks.twitch("https://www.twitch.tv/some_channel"));
        assertEquals(Optional.of(page), VideoLinks.twitch("twitch.tv/some_channel/videos"));
        assertTrue(VideoLinks.twitch("https://evil.com/some_channel").isEmpty());
        assertTrue(VideoLinks.twitch("bad channel!").isEmpty());
    }

    @Test
    void directLinksPlayOnTheVideoPageWithTheStreamsScheme() {
        assertEquals(Optional.of("https://dev.anomz.blockcinema/video.html?src=https%3A%2F%2Fcdn.example.com%2Fa%2520b.mp4"),
                VideoLinks.direct(" https://cdn.example.com/a%20b.mp4 "));
        assertEquals(Optional.of("http://dev.anomz.blockcinema/video.html?src=http%3A%2F%2F192.168.1.5%3A8096%2Flive.m3u8"),
                VideoLinks.direct("http://192.168.1.5:8096/live.m3u8"));
        assertTrue(VideoLinks.direct("file:///C:/movie.mp4").isEmpty());
        assertTrue(VideoLinks.direct("javascript:alert(1)").isEmpty());
        assertTrue(VideoLinks.direct("cdn.example.com/a.mp4").isEmpty());
        assertTrue(VideoLinks.direct("https://user:pass@example.com/a.mp4").isEmpty());
    }

    @Test
    void authorizationGoesInTheFragment() {
        assertEquals("https://dev.anomz.blockcinema/video.html?src=https%3A%2F%2Fjf.example.com%2Fv.m3u8#auth=Token%3D%22x%22",
                VideoLinks.videoPage("https://jf.example.com/v.m3u8", "Token=\"x\""));
    }

    @Test
    void guardKeepsMainFrameOnOurPages() {
        assertTrue(NavigationGuard.allowed("https://dev.anomz.blockcinema/youtube.html?v=dQw4w9WgXcQ", true));
        assertTrue(!NavigationGuard.allowed("https://www.youtube.com/watch?v=dQw4w9WgXcQ", true));
        assertTrue(NavigationGuard.allowed("http://dev.anomz.blockcinema/video.html", true));
        assertTrue(!NavigationGuard.allowed("http://example.com/video.html", true));
        assertTrue(!NavigationGuard.allowed("file:///C:/Windows/win.ini", true));
        assertTrue(NavigationGuard.allowed("https://www.youtube.com/embed/dQw4w9WgXcQ", false));
        assertTrue(!NavigationGuard.allowed("file:///C:/Windows/win.ini", false));
    }
}
