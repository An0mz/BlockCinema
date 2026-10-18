package dev.anomz.blockcinema.client.jellyfin;

import java.util.HashMap;
import java.util.Map;
import java.util.function.Consumer;

import org.jetbrains.annotations.Nullable;

import dev.anomz.blockcinema.BlockCinema;
import dev.anomz.blockcinema.client.browser.TvPlayers;
import dev.anomz.blockcinema.client.browser.VideoLinks;
import net.minecraft.Util;
import net.minecraft.client.Minecraft;

/**
 * Watching along on a Jellyfin TV: the TV only shares which video on which server, and every player
 * logged in to that server here streams it with their own login (their account, their access, their
 * watch progress). Players who aren't see who is watching instead. Client thread only.
 */
public final class JellyfinWatch {
    /** After a failure (no access, server down), wait this long before asking again. */
    private static final long RETRY_MILLIS = 60_000;

    private static final Map<String, Request> REQUESTS = new HashMap<>();

    private enum State { LOADING, READY, FAILED }

    private static final class Request {
        State state = State.LOADING;
        long failedAt;
        JellyfinApi api;
        JellyfinApi.Playback playback;
    }

    /** A stream ready for the TV page; {@code onStop} tells this player's Jellyfin where they stopped. */
    public record Stream(String page, Consumer<TvPlayers.Position> onStop) {
    }

    private JellyfinWatch() {
    }

    /** Whether this player is logged in to the Jellyfin server with this ID. */
    public static boolean canWatch(String serverId) {
        JellyfinLogin login = JellyfinLogin.current();
        return login != null && serverId.equals(login.serverId());
    }

    /** Whether this player's account couldn't play the video at {@code key} lately. */
    public static boolean failed(String key) {
        Request request = REQUESTS.get(key);
        return request != null && request.state == State.FAILED;
    }

    /**
     * This player's stream of {@code itemId} for the TV page at {@code key} (the shared Jellyfin page),
     * or null while it is being set up (the first call starts that). Each stream is handed out once.
     */
    @Nullable
    public static Stream take(String key, String itemId) {
        JellyfinLogin login = JellyfinLogin.current();
        if (login == null) {
            return null;
        }
        Request request = REQUESTS.get(key);
        if (request == null || request.state == State.FAILED && Util.getMillis() - request.failedAt > RETRY_MILLIS) {
            request(key, login, itemId);
            return null;
        }
        if (request.state != State.READY) {
            return null;
        }
        REQUESTS.remove(key);
        JellyfinApi api = request.api;
        JellyfinApi.Playback playback = request.playback;
        api.reportStarted(playback);
        return new Stream(VideoLinks.videoPage(playback.url(), api.authorization()),
                position -> api.reportStopped(playback, position.time()));
    }

    private static void request(String key, JellyfinLogin login, String itemId) {
        Request request = new Request();
        request.api = JellyfinApi.of(login, JellyfinApi.appVersion());
        REQUESTS.put(key, request);
        request.api.playback(login.userId(), itemId).whenComplete((playback, error) -> Minecraft.getInstance().execute(() -> {
            if (REQUESTS.get(key) != request) {
                return;
            }
            if (error != null) {
                BlockCinema.LOGGER.info("Your Jellyfin account can't play {}: {}", itemId, error.getMessage());
                request.state = State.FAILED;
                request.failedAt = Util.getMillis();
            } else {
                request.playback = playback;
                request.state = State.READY;
            }
        }));
    }

    /** Forgets streams being set up (on leaving a world, or logging out of Jellyfin). */
    public static void clear() {
        REQUESTS.clear();
    }
}
