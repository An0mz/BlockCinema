package dev.anomz.blockcinema.client;

import dev.anomz.blockcinema.TvService;
import net.neoforged.neoforge.common.ModConfigSpec;

/**
 * Each player's own settings ({@code config/blockcinema-client.toml}, also under Mods > Block Cinema >
 * Config). They only change what this player's game plays and shows.
 */
public final class BlockCinemaClientConfig {
    public enum Quality {
        /** 1080p for screens 4 or more TVs wide and fullscreen, 720p for smaller ones. */
        AUTO,
        P720,
        P1080
    }

    private static final ModConfigSpec.Builder BUILDER = new ModConfigSpec.Builder();

    private static final ModConfigSpec.BooleanValue VIDEO = BUILDER
            .comment("Play videos on TVs. Turn off on slow computers: TVs then show what is on without playing it.")
            .define("playVideos", true);
    private static final ModConfigSpec.BooleanValue FOCUS = BUILDER
            .comment("Only play the TV you are looking at (it keeps playing until you look at another one).",
                    "Other TVs show that they are paused for you; other players still see them play.")
            .define("onlyTvYouLookAt", false);
    private static final ModConfigSpec.IntValue MAX_SCREENS = BUILDER
            .comment("Most TVs playing at once for you; the closest ones play.")
            .defineInRange("maxScreens", 4, 1, 16);
    private static final ModConfigSpec.EnumValue<Quality> QUALITY = BUILDER
            .comment("Picture quality: AUTO (1080p for big screens and fullscreen), P720 or P1080. Higher costs more CPU.")
            .defineEnum("quality", Quality.AUTO);
    private static final ModConfigSpec.IntValue VOLUME = BUILDER
            .comment("The volume a TV starts at for you, in percent. Each TV's own volume can then be changed in its menu.")
            .defineInRange("startVolume", 100, 0, 100);

    private static final ModConfigSpec.BooleanValue CAPTIONS = BUILDER
            .comment("Show YouTube captions (in the game's language when the video has them). Off: no captions, even when",
                    "YouTube would turn them on by itself.")
            .define("youtubeCaptions", false);

    private static final ModConfigSpec.BooleanValue SHOW_YOUTUBE = BUILDER
            .comment("Which services the TV menu offers you. A hidden one still plays when someone else puts it on a TV.")
            .define("showYoutube", true);
    private static final ModConfigSpec.BooleanValue SHOW_TWITCH = BUILDER.define("showTwitch", true);
    private static final ModConfigSpec.BooleanValue SHOW_JELLYFIN = BUILDER.define("showJellyfin", true);
    private static final ModConfigSpec.BooleanValue SHOW_VIDEO_LINKS = BUILDER.define("showVideoLinks", true);

    public static final ModConfigSpec SPEC = BUILDER.build();

    private BlockCinemaClientConfig() {
    }

    // Before the config loads, use the defaults.
    public static boolean playVideos() {
        return !SPEC.isLoaded() || VIDEO.get();
    }

    public static boolean onlyTvYouLookAt() {
        return SPEC.isLoaded() && FOCUS.get();
    }

    public static int maxScreens() {
        return SPEC.isLoaded() ? MAX_SCREENS.get() : 4;
    }

    public static Quality quality() {
        return SPEC.isLoaded() ? QUALITY.get() : Quality.AUTO;
    }

    public static boolean youtubeCaptions() {
        return SPEC.isLoaded() && CAPTIONS.get();
    }

    /** Whether the TV menu offers this service to this player. */
    public static boolean shows(TvService service) {
        if (!SPEC.isLoaded()) {
            return true;
        }
        return switch (service) {
            case YOUTUBE -> SHOW_YOUTUBE.get();
            case TWITCH -> SHOW_TWITCH.get();
            case JELLYFIN -> SHOW_JELLYFIN.get();
            case DIRECT_LINK -> SHOW_VIDEO_LINKS.get();
        };
    }

    public static float startVolume() {
        return (SPEC.isLoaded() ? VOLUME.get() : 100) / 100f;
    }
}
