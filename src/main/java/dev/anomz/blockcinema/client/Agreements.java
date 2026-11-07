package dev.anomz.blockcinema.client;

import java.nio.file.Path;

import com.google.gson.JsonObject;

import dev.anomz.blockcinema.BlockCinema;
import net.neoforged.fml.loading.FMLPaths;

/**
 * Whether this player agreed to YouTube's terms and the privacy policies that YouTube's developer policies
 * require. Kept in {@code config/blockcinema/agreements.json}. Client thread only.
 */
public final class Agreements {
    /** Raise when what players agree to for YouTube changes, so everyone is asked again. */
    private static final int YOUTUBE_VERSION = 1;
    public static final String YOUTUBE_TERMS = "https://www.youtube.com/t/terms";
    public static final String GOOGLE_PRIVACY = "http://www.google.com/policies/privacy";
    public static final String PRIVACY_POLICY = "https://github.com/An0mz/BlockCinema/blob/main/PRIVACY.md";
    public static final String TERMS = "https://github.com/An0mz/BlockCinema/blob/main/TERMS.md";

    private static int youtubeAgreed = -1;

    private Agreements() {
    }

    private static Path file() {
        return FMLPaths.CONFIGDIR.get().resolve(BlockCinema.MODID).resolve("agreements.json");
    }

    public static boolean youtube() {
        if (youtubeAgreed < 0) {
            youtubeAgreed = JsonFile.read(file(), "Couldn't read the saved agreements",
                    json -> json.has("youtube") ? json.get("youtube").getAsInt() : 0).orElse(0);
        }
        return youtubeAgreed >= YOUTUBE_VERSION;
    }

    public static void setYoutube(boolean agreed) {
        youtubeAgreed = agreed ? YOUTUBE_VERSION : 0;
        JsonObject json = new JsonObject();
        json.addProperty("youtube", youtubeAgreed);
        JsonFile.write(file(), json, "Couldn't save the agreements");
    }
}
