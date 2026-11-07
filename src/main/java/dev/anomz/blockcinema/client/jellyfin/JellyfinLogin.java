package dev.anomz.blockcinema.client.jellyfin;

import java.nio.file.Path;
import java.util.Optional;

import org.jetbrains.annotations.Nullable;

import com.google.gson.JsonObject;

import dev.anomz.blockcinema.BlockCinema;
import dev.anomz.blockcinema.client.JsonFile;
import net.neoforged.fml.loading.FMLPaths;

/**
 * This player's Jellyfin login, saved in {@code config/blockcinema/jellyfin.json} on their own computer
 * only. The access token is what Jellyfin apps keep after login; the password is never seen or stored.
 */
public record JellyfinLogin(String server, String serverId, String userId, String userName, String token, String deviceId) {
    @Nullable
    private static JellyfinLogin current;
    private static boolean loaded;

    @Nullable
    public static JellyfinLogin current() {
        if (!loaded) {
            current = load();
            loaded = true;
        }
        return current;
    }

    /** The same login on a server whose ID wasn't known yet (logins saved by older versions). */
    public JellyfinLogin withServerId(String id) {
        return new JellyfinLogin(server, id, userId, userName, token, deviceId);
    }

    private static Path file() {
        return FMLPaths.CONFIGDIR.get().resolve(BlockCinema.MODID).resolve("jellyfin.json");
    }

    @Nullable
    private static JellyfinLogin load() {
        return JsonFile.read(file(), "Couldn't read the Jellyfin login, log in again", json -> {
            if (!json.has("token")) {
                return null;
            }
            String serverId = json.has("serverId") ? json.get("serverId").getAsString() : "";
            return new JellyfinLogin(json.get("server").getAsString(), serverId, json.get("userId").getAsString(),
                    json.get("userName").getAsString(), json.get("token").getAsString(), json.get("deviceId").getAsString());
        }).orElse(null);
    }

    public void save() {
        JsonObject json = new JsonObject();
        json.addProperty("server", server);
        json.addProperty("serverId", serverId);
        json.addProperty("userId", userId);
        json.addProperty("userName", userName);
        json.addProperty("token", token);
        json.addProperty("deviceId", deviceId);
        JsonFile.write(file(), json, "Couldn't save the Jellyfin login");
        current = this;
        loaded = true;
    }

    /** Forgets the login but keeps the device ID, so Jellyfin sees the same device next time. */
    public static void forget() {
        JsonObject json = new JsonObject();
        savedDeviceId().ifPresent(id -> json.addProperty("deviceId", id));
        JsonFile.write(file(), json, "Couldn't save the Jellyfin login");
        current = null;
        loaded = true;
    }

    public static Optional<String> savedDeviceId() {
        return JsonFile.read(file(), "Couldn't read the Jellyfin device ID",
                json -> json.has("deviceId") ? json.get("deviceId").getAsString() : null);
    }
}
