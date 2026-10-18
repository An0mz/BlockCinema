package dev.anomz.blockcinema.client.jellyfin;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;

import org.jetbrains.annotations.Nullable;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import dev.anomz.blockcinema.BlockCinema;
import net.neoforged.fml.loading.FMLPaths;

/**
 * This player's Jellyfin login, saved in {@code config/blockcinema/jellyfin.json} on their own computer
 * only. The access token is what Jellyfin apps keep after login; the password is never seen or stored.
 */
public record JellyfinLogin(String server, String userId, String userName, String token, String deviceId) {
    private static Path file() {
        return FMLPaths.CONFIGDIR.get().resolve(BlockCinema.MODID).resolve("jellyfin.json");
    }

    @Nullable
    public static JellyfinLogin load() {
        Path file = file();
        if (!Files.isRegularFile(file)) {
            return null;
        }
        try (Reader reader = Files.newBufferedReader(file)) {
            JsonObject json = JsonParser.parseReader(reader).getAsJsonObject();
            if (!json.has("token")) {
                return null;
            }
            return new JellyfinLogin(json.get("server").getAsString(), json.get("userId").getAsString(),
                    json.get("userName").getAsString(), json.get("token").getAsString(), json.get("deviceId").getAsString());
        } catch (IOException | RuntimeException e) {
            BlockCinema.LOGGER.warn("Couldn't read the Jellyfin login, log in again", e);
            return null;
        }
    }

    public void save() {
        JsonObject json = new JsonObject();
        json.addProperty("server", server);
        json.addProperty("userId", userId);
        json.addProperty("userName", userName);
        json.addProperty("token", token);
        json.addProperty("deviceId", deviceId);
        write(json);
    }

    /** Forgets the login but keeps the device ID, so Jellyfin sees the same device next time. */
    public static void forget() {
        JsonObject json = new JsonObject();
        savedDeviceId().ifPresent(id -> json.addProperty("deviceId", id));
        write(json);
    }

    /** The device ID saved earlier, if any (kept across logins). */
    public static Optional<String> savedDeviceId() {
        Path file = file();
        if (!Files.isRegularFile(file)) {
            return Optional.empty();
        }
        try (Reader reader = Files.newBufferedReader(file)) {
            JsonObject json = JsonParser.parseReader(reader).getAsJsonObject();
            return json.has("deviceId") ? Optional.of(json.get("deviceId").getAsString()) : Optional.empty();
        } catch (IOException | RuntimeException e) {
            return Optional.empty();
        }
    }

    private static void write(JsonObject json) {
        Path file = file();
        try {
            Files.createDirectories(file.getParent());
            try (Writer writer = Files.newBufferedWriter(file)) {
                writer.write(json.toString());
            }
        } catch (IOException e) {
            BlockCinema.LOGGER.error("Couldn't save the Jellyfin login", e);
        }
    }
}
