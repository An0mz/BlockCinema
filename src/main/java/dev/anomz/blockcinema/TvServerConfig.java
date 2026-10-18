package dev.anomz.blockcinema;

import java.util.List;

import net.neoforged.neoforge.common.ModConfigSpec;

/**
 * Settings for server owners ({@code config/blockcinema-server.toml}; a world can override it in
 * {@code serverconfig/}). NeoForge syncs these to clients, so the TV menu shows the server's choices;
 * the server checks them again on every request.
 */
public final class TvServerConfig {
    private static final ModConfigSpec.Builder BUILDER = new ModConfigSpec.Builder();

    private static final ModConfigSpec.BooleanValue YOUTUBE = BUILDER
            .comment("Allow YouTube on TVs (official embedded player).")
            .define("enableYoutube", true);
    private static final ModConfigSpec.BooleanValue TWITCH = BUILDER
            .comment("Allow Twitch on TVs (official embedded player).")
            .define("enableTwitch", true);
    private static final ModConfigSpec.BooleanValue JELLYFIN = BUILDER
            .comment("Allow players to watch their own Jellyfin server on TVs. Only which video on which server is",
                    "shared: players logged in to that Jellyfin server watch along with their own login, others see",
                    "who is watching. Logins and video never go through this server.")
            .define("enableJellyfin", true);
    private static final ModConfigSpec.BooleanValue DIRECT_LINKS = BUILDER
            .comment("Allow playing direct video links (video files and .m3u8 streams) that everybody nearby sees.")
            .define("enableDirectLinks", true);
    private static final ModConfigSpec.ConfigValue<List<? extends String>> DIRECT_LINK_HOSTS = BUILDER
            .comment("If not empty, video links may only come from these hosts (and their subdomains),",
                    "like [\"example.com\", \"cdn.example.org\"].")
            .defineListAllowEmpty("directLinkHosts", List.of(), () -> "example.com",
                    host -> host instanceof String text && !text.isBlank());
    private static final ModConfigSpec.BooleanValue ANYONE_CAN_CONTROL = BUILDER
            .comment("Let any player change or control what a TV plays. If false, only the player who started it",
                    "and operators can.")
            .define("anyoneCanControl", true);

    static final ModConfigSpec SPEC = BUILDER.build();

    private TvServerConfig() {
    }

    // Before a world loads the config is not there yet; default to the defaults.
    public static boolean enabled(TvService service) {
        if (!SPEC.isLoaded()) {
            return true;
        }
        return switch (service) {
            case YOUTUBE -> YOUTUBE.get();
            case TWITCH -> TWITCH.get();
            case JELLYFIN -> JELLYFIN.get();
            case DIRECT_LINK -> DIRECT_LINKS.get();
        };
    }

    /** Whether video links may come from {@code host}. */
    public static boolean linkHostAllowed(String host) {
        List<? extends String> hosts = SPEC.isLoaded() ? DIRECT_LINK_HOSTS.get() : List.of();
        return TvPageRules.hostAllowed(hosts, host);
    }

    public static boolean anyoneCanControl() {
        return !SPEC.isLoaded() || ANYONE_CAN_CONTROL.get();
    }
}
