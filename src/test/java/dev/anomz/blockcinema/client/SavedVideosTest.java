package dev.anomz.blockcinema.client;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Path;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import dev.anomz.blockcinema.TvService;

class SavedVideosTest {
    private static final String A = "https://blockcinema.anomz.dev/youtube.html?v=dQw4w9WgXcQ";
    private static final String B = "https://blockcinema.anomz.dev/twitch.html?channel=some_channel";

    @TempDir
    Path dir;

    @Test
    void historyIsLatestFirstWithoutRepeatsAndSurvivesAReload() {
        Path file = dir.resolve("saved.json");
        SavedVideos saved = new SavedVideos(file);
        saved.played(TvService.YOUTUBE, A);
        saved.played(TvService.TWITCH, B);
        saved.played(TvService.YOUTUBE, A);
        saved.played(TvService.JELLYFIN, "jellyfin:0123456789abcdef0123456789abcdef:f27caa37e5142225cceded48f6553502");
        List<SavedVideos.Entry> expected = List.of(new SavedVideos.Entry(TvService.YOUTUBE, A, "dQw4w9WgXcQ"),
                new SavedVideos.Entry(TvService.TWITCH, B, "some_channel"));
        assertEquals(expected, saved.history());
        assertEquals(expected, new SavedVideos(file).history());
    }

    @Test
    void favoritesToggleAndTakeTheVideosName() {
        Path file = dir.resolve("saved.json");
        SavedVideos saved = new SavedVideos(file);
        saved.played(TvService.YOUTUBE, A);
        saved.rename(A, "A Song");
        saved.toggleFavorite(TvService.YOUTUBE, A);
        saved.clearHistory();
        SavedVideos reloaded = new SavedVideos(file);
        assertTrue(reloaded.history().isEmpty());
        assertEquals(List.of(new SavedVideos.Entry(TvService.YOUTUBE, A, "A Song")), reloaded.favorites());
        reloaded.toggleFavorite(TvService.YOUTUBE, A);
        assertFalse(reloaded.isFavorite(A));
    }

    @Test
    void historyForgetsTheOldestBeyondTheLimit() {
        SavedVideos saved = new SavedVideos(dir.resolve("saved.json"));
        for (int i = 0; i <= SavedVideos.MAX_HISTORY; i++) {
            saved.played(TvService.TWITCH, "https://blockcinema.anomz.dev/twitch.html?channel=c" + i);
        }
        assertEquals(SavedVideos.MAX_HISTORY, saved.history().size());
        assertEquals("c" + SavedVideos.MAX_HISTORY, saved.history().get(0).title());
    }
}
