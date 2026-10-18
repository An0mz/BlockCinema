package dev.anomz.blockcinema;

import java.util.UUID;

/**
 * What a TV screen plays, as the server keeps and shares it. The time is anchored to the world's game
 * time, so every client works out the same position: {@code anchorSeconds} into the video at game tick
 * {@code anchorTick}, moving on 20 ticks a second unless paused. The video itself never goes through
 * the server; each client plays {@code page} itself (Jellyfin with each viewer's own login).
 *
 * @param page our page URL, or for Jellyfin which video on which server (see {@link TvPageRules})
 * @param owner who started it
 */
public record TvSource(TvService service, String page, UUID owner, String ownerName, long anchorTick,
        double anchorSeconds, boolean paused) {
    public static final int TICKS_PER_SECOND = 20;

    /** Seconds into the video at {@code gameTime}. */
    public double position(long gameTime) {
        return paused ? anchorSeconds : anchorSeconds + Math.max(0, gameTime - anchorTick) / (double) TICKS_PER_SECOND;
    }

    public TvSource pausedAt(long gameTime) {
        return paused ? this : new TvSource(service, page, owner, ownerName, gameTime, position(gameTime), true);
    }

    public TvSource resumedAt(long gameTime) {
        return paused ? new TvSource(service, page, owner, ownerName, gameTime, anchorSeconds, false) : this;
    }

    public TvSource seekedTo(long gameTime, double seconds) {
        return new TvSource(service, page, owner, ownerName, gameTime, Math.max(0, seconds), paused);
    }
}
