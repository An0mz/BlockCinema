package dev.anomz.blockcinema.client;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.file.Files;
import java.nio.file.Path;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import dev.anomz.blockcinema.BlockCinema;
import net.neoforged.fml.loading.FMLPaths;

/**
 * Whether this player agreed to YouTube's terms, the Google Privacy Policy and Block Cinema's privacy
 * policy, which YouTube's developer policies require before YouTube plays. Kept in
 * {@code config/blockcinema/agreements.json} on this computer. Client thread only.
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
            youtubeAgreed = 0;
            Path file = file();
            if (Files.isRegularFile(file)) {
                try (Reader reader = Files.newBufferedReader(file)) {
                    JsonObject json = JsonParser.parseReader(reader).getAsJsonObject();
                    youtubeAgreed = json.has("youtube") ? json.get("youtube").getAsInt() : 0;
                } catch (IOException | RuntimeException e) {
                    BlockCinema.LOGGER.warn("Couldn't read the saved agreements", e);
                }
            }
        }
        return youtubeAgreed >= YOUTUBE_VERSION;
    }

    /** Agrees ({@code true}) or withdraws the agreement ({@code false}). */
    public static void setYoutube(boolean agreed) {
        youtubeAgreed = agreed ? YOUTUBE_VERSION : 0;
        JsonObject json = new JsonObject();
        json.addProperty("youtube", youtubeAgreed);
        Path file = file();
        try {
            Files.createDirectories(file.getParent());
            try (Writer writer = Files.newBufferedWriter(file)) {
                writer.write(json.toString());
            }
        } catch (IOException e) {
            BlockCinema.LOGGER.error("Couldn't save the agreements", e);
        }
    }
}
