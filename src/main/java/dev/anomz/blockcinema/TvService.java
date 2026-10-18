package dev.anomz.blockcinema;

import java.util.Locale;

/**
 * The only things a TV can show. There is no free web browsing: each service has its own player and
 * its own list of allowed sites. No Minecraft classes here, so tests can use it.
 */
public enum TvService {
    YOUTUBE,
    TWITCH,
    JELLYFIN,
    DIRECT_LINK;

    /** Lowercase name, used in translation keys and configs. */
    public String id() {
        return name().toLowerCase(Locale.ROOT);
    }

    /** The service's name in the language files. */
    public String translationKey() {
        return "service." + BlockCinema.MODID + "." + id();
    }
}
