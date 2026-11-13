package dev.anomz.blockcinema.client;

import dev.anomz.blockcinema.TvService;
import net.minecraft.Util;
import net.neoforged.neoforge.common.ModConfigSpec;

/** Each player's own settings ({@code config/blockcinema-client.toml}); they only change what this player's game plays and shows. */
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
    private static final ModConfigSpec.BooleanValue OUT_OF_SIGHT = BUILDER
            .comment("Keep TVs playing when you aren't looking at them or are far away (as long as their area is loaded).",
                    "Off: TVs stop past 96 blocks. On costs more CPU. A YouTube TV still pauses a few seconds after",
                    "you look away, as YouTube's terms require.")
            .define("keepPlayingOutOfSight", false);
    private static final ModConfigSpec.EnumValue<Quality> QUALITY = BUILDER
            .comment("Picture quality: AUTO (1080p for big screens and fullscreen), P720 or P1080. Higher costs more CPU.")
            .defineEnum("quality", Quality.AUTO);
    private static final ModConfigSpec.IntValue VOLUME = BUILDER
            .comment("The volume a TV starts at for you, in percent. It follows the last volume you set on a TV,",
                    "so TVs keep your volume the next time they play.")
            .defineInRange("startVolume", 100, 0, 100);
    private static final ModConfigSpec.BooleanValue DIRECTIONAL_SOUND = BUILDER
            .comment("Hear a TV from the side it is on, left or right. Only for video links and Jellyfin: YouTube and",
                    "Twitch don't allow changing their sound.")
            .define("directionalSound", false);

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

    private static final long VOLUME_SAVE_MILLIS = 1000;
    /** When the remembered volume last changed, or 0 once it is saved. */
    private static long volumeChangedMillis;

    private BlockCinemaClientConfig() {
    }

    public static boolean playVideos() {
        return !SPEC.isLoaded() || VIDEO.get();
    }

    public static boolean onlyTvYouLookAt() {
        return SPEC.isLoaded() && FOCUS.get();
    }

    public static int maxScreens() {
        return SPEC.isLoaded() ? MAX_SCREENS.get() : 4;
    }

    public static boolean keepPlayingOutOfSight() {
        return SPEC.isLoaded() && OUT_OF_SIGHT.get();
    }

    public static Quality quality() {
        return SPEC.isLoaded() ? QUALITY.get() : Quality.AUTO;
    }

    public static boolean directionalSound() {
        return SPEC.isLoaded() && DIRECTIONAL_SOUND.get();
    }

    public static boolean youtubeCaptions() {
        return SPEC.isLoaded() && CAPTIONS.get();
    }

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

    /**
     * Makes the volume the player just set on a TV the one TVs start at. Muting isn't kept, so a TV never
     * starts silent. Written to the file by {@link #saveVolume}, not on every step of a slider.
     */
    public static void rememberVolume(float volume) {
        int percent = Math.round(volume * 100);
        if (SPEC.isLoaded() && percent > 0 && percent != VOLUME.get()) {
            VOLUME.set(percent);
            volumeChangedMillis = Math.max(1, Util.getMillis());
        }
    }

    /** Saves a remembered volume once it has stopped changing for a moment, or at once with {@code now}. */
    public static void saveVolume(boolean now) {
        if (volumeChangedMillis != 0 && (now || Util.getMillis() - volumeChangedMillis >= VOLUME_SAVE_MILLIS)) {
            volumeChangedMillis = 0;
            VOLUME.save();
        }
    }
}
