package dev.anomz.blockcinema;

import java.util.UUID;

/**
 * Something waiting to play on a screen after what plays now (see {@code TvBlockEntity#queue}).
 *
 * @param page our page URL, or for Jellyfin which video on which server (see {@link TvPageRules})
 * @param title a short name to show in the queue, from the player who added it (Jellyfin's item name, a
 *        link's file name; may be empty)
 * @param owner who added it, and who it plays for as its starter
 */
public record TvQueueEntry(TvService service, String page, String title, UUID owner, String ownerName) {
    public static final int MAX_TITLE_LENGTH = 100;

    /** It playing now, from the start, at game tick {@code gameTime}. */
    public TvSource start(long gameTime) {
        return new TvSource(service, page, owner, ownerName, gameTime, 0, false);
    }

    /** A title from a client made safe to show: no control characters, not too long. */
    public static String cleanTitle(String title) {
        String clean = title.replaceAll("\\p{Cntrl}", "").strip();
        return clean.length() > MAX_TITLE_LENGTH ? clean.substring(0, MAX_TITLE_LENGTH) : clean;
    }
}
