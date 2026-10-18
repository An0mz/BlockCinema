package dev.anomz.blockcinema;

import java.util.Locale;

import net.minecraft.network.chat.Component;

/**
 * The only things a TV can show. There is no free web browsing: each service has its own player
 * and its own list of allowed sites.
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

    public Component displayName() {
        return Component.translatable("service." + BlockCinema.MODID + "." + id());
    }
}
