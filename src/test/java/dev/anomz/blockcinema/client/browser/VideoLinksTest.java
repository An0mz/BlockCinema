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
    void guardKeepsMainFrameOnOurPages() {
        assertTrue(NavigationGuard.allowed("https://dev.anomz.blockcinema/youtube.html?v=dQw4w9WgXcQ", true));
        assertTrue(!NavigationGuard.allowed("https://www.youtube.com/watch?v=dQw4w9WgXcQ", true));
        assertTrue(!NavigationGuard.allowed("http://dev.anomz.blockcinema/youtube.html", true));
        assertTrue(!NavigationGuard.allowed("file:///C:/Windows/win.ini", true));
        assertTrue(NavigationGuard.allowed("https://www.youtube.com/embed/dQw4w9WgXcQ", false));
        assertTrue(!NavigationGuard.allowed("file:///C:/Windows/win.ini", false));
    }
}
