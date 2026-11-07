package dev.anomz.blockcinema;

import java.util.Locale;

/** The only things a TV can show: each service has its own player and its own list of allowed sites. */
public enum TvService {
    YOUTUBE,
    TWITCH,
    JELLYFIN,
    DIRECT_LINK;

    public String id() {
        return name().toLowerCase(Locale.ROOT);
    }

    public String translationKey() {
        return "service." + BlockCinema.MODID + "." + id();
    }
}
