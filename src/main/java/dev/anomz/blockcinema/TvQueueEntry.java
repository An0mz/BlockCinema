package dev.anomz.blockcinema;

import java.util.UUID;

/** Something waiting to play on a screen after what plays now. */
public record TvQueueEntry(TvService service, String page, String title, UUID owner, String ownerName) {
    public static final int MAX_TITLE_LENGTH = 100;

    public TvSource start(long clockTicks) {
        return new TvSource(service, page, owner, ownerName, clockTicks, 0, false);
    }

    public static String cleanTitle(String title) {
        String clean = title.replaceAll("\\p{Cntrl}", "").strip();
        return clean.length() > MAX_TITLE_LENGTH ? clean.substring(0, MAX_TITLE_LENGTH) : clean;
    }
}
