package dev.anomz.blockcinema.client.jellyfin;

import java.net.URI;
import java.net.URISyntaxException;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import dev.anomz.blockcinema.BlockCinema;
import net.neoforged.fml.ModList;

/**
 * The few Jellyfin REST calls the TV needs, all made from this computer straight to the player's own
 * server (never through the Minecraft server). Works with Jellyfin 10.9 and later, including 12.
 */
public final class JellyfinApi {
    private static final String CLIENT = "Block Cinema";
    private static final String DEVICE = "Minecraft";
    private static final HttpClient HTTP = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(10))
            .followRedirects(HttpClient.Redirect.NORMAL)
            .build();

    private final String server;
    private final String deviceId;
    private final String version;
    private final String token;

    public record QuickConnect(String secret, String code) {
    }

    /** {@code resumeSeconds}: where the user stopped watching last time, in any Jellyfin app (0 if not started). */
    public record Item(String id, String name, String type, boolean folder, String detail, double resumeSeconds) {
    }

    /** Jellyfin counts time in ticks of 100 ns. */
    private static final double TICKS_PER_SECOND = 10_000_000;

    /** {@code url} is the stream for the TV page; the rest is for reporting playback to Jellyfin. */
    public record Playback(String url, String itemId, String mediaSourceId, String playSessionId) {
    }

    /** {@code token} is null before login. */
    public JellyfinApi(String server, String deviceId, String version, String token) {
        this.server = server;
        this.deviceId = deviceId;
        this.version = version;
        this.token = token;
    }

    /** The mod version, which Jellyfin shows in its device list. */
    public static String appVersion() {
        return ModList.get().getModContainerById(BlockCinema.MODID)
                .map(container -> container.getModInfo().getVersion().toString()).orElse("0");
    }

    public static JellyfinApi of(JellyfinLogin login, String version) {
        return new JellyfinApi(login.server(), login.deviceId(), version, login.token());
    }

    /**
     * A server address as typed, cleaned up: http assumed when no scheme is given (Jellyfin's default
     * on a home network), no trailing slash. Empty if it isn't an http(s) address.
     */
    public static Optional<String> normalizeServer(String input) {
        String text = input.trim();
        if (text.isEmpty()) {
            return Optional.empty();
        }
        if (!text.contains("://")) {
            text = "http://" + text;
        }
        while (text.endsWith("/")) {
            text = text.substring(0, text.length() - 1);
        }
        try {
            URI uri = new URI(text);
            String scheme = uri.getScheme().toLowerCase(Locale.ROOT);
            if (!scheme.equals("http") && !scheme.equals("https") || uri.getHost() == null
                    || uri.getQuery() != null || uri.getFragment() != null || uri.getUserInfo() != null) {
                return Optional.empty();
            }
            return Optional.of(text);
        } catch (URISyntaxException e) {
            return Optional.empty();
        }
    }

    /** The Authorization header Jellyfin expects from apps. */
    public String authorization() {
        StringBuilder header = new StringBuilder("MediaBrowser Client=\"").append(CLIENT)
                .append("\", Device=\"").append(DEVICE)
                .append("\", DeviceId=\"").append(deviceId)
                .append("\", Version=\"").append(version).append('"');
        if (token != null) {
            header.append(", Token=\"").append(token).append('"');
        }
        return header.toString();
    }

    /** The server's name; fails if this isn't a Jellyfin server. */
    public CompletableFuture<String> serverName() {
        return get("/System/Info/Public").thenApply(json -> {
            JsonObject info = json.getAsJsonObject();
            if (!info.has("Id")) {
                throw new IllegalStateException("Not a Jellyfin server");
            }
            return info.has("ServerName") ? info.get("ServerName").getAsString() : server;
        });
    }

    public CompletableFuture<QuickConnect> startQuickConnect() {
        return post("/QuickConnect/Initiate", null).thenApply(json -> {
            JsonObject result = json.getAsJsonObject();
            return new QuickConnect(result.get("Secret").getAsString(), result.get("Code").getAsString());
        });
    }

    /** Whether the code has been approved in another Jellyfin app yet. */
    public CompletableFuture<Boolean> quickConnectApproved(String secret) {
        return get("/QuickConnect/Connect?secret=" + encode(secret))
                .thenApply(json -> json.getAsJsonObject().get("Authenticated").getAsBoolean());
    }

    public CompletableFuture<JellyfinLogin> finishQuickConnect(String secret) {
        JsonObject body = new JsonObject();
        body.addProperty("Secret", secret);
        return post("/Users/AuthenticateWithQuickConnect", body).thenApply(json -> {
            JsonObject result = json.getAsJsonObject();
            JsonObject user = result.getAsJsonObject("User");
            return new JellyfinLogin(server, string(result, "ServerId"), user.get("Id").getAsString(), user.get("Name").getAsString(),
                    result.get("AccessToken").getAsString(), deviceId);
        });
    }

    /** The server's ID (the same whichever address it is reached at). */
    public CompletableFuture<String> serverId() {
        return get("/System/Info/Public").thenApply(json -> json.getAsJsonObject().get("Id").getAsString());
    }

    /** The user's libraries. */
    public CompletableFuture<List<Item>> libraries(String userId) {
        return get("/UserViews?userId=" + encode(userId)).thenApply(JellyfinApi::items);
    }

    /** What is in a library or folder (series, seasons and so on), sorted by name. */
    public CompletableFuture<List<Item>> children(String userId, String parentId) {
        return get("/Items?userId=" + encode(userId) + "&parentId=" + encode(parentId)
                + "&sortBy=SortName&fields=ProductionYear&enableUserData=true&limit=500").thenApply(JellyfinApi::items);
    }

    /**
     * Asks the server for an HLS stream of the item with H.264 video and AAC audio, which the TV's
     * browser can play: Jellyfin copies streams that already fit and transcodes the rest.
     */
    public CompletableFuture<Playback> playback(String userId, String itemId) {
        JsonObject profile = new JsonObject();
        profile.addProperty("Name", CLIENT);
        profile.addProperty("MaxStreamingBitrate", 20_000_000);
        profile.add("DirectPlayProfiles", new JsonArray());
        JsonObject hls = new JsonObject();
        hls.addProperty("Type", "Video");
        hls.addProperty("Container", "ts");
        hls.addProperty("Protocol", "hls");
        hls.addProperty("Context", "Streaming");
        hls.addProperty("VideoCodec", "h264");
        hls.addProperty("AudioCodec", "aac");
        hls.addProperty("MaxAudioChannels", "2");
        hls.addProperty("BreakOnNonKeyFrames", true);
        JsonArray transcoding = new JsonArray();
        transcoding.add(hls);
        profile.add("TranscodingProfiles", transcoding);

        JsonObject body = new JsonObject();
        body.addProperty("UserId", userId);
        body.addProperty("MaxStreamingBitrate", 20_000_000);
        body.addProperty("EnableDirectPlay", false);
        body.addProperty("EnableDirectStream", false);
        body.addProperty("EnableTranscoding", true);
        body.addProperty("AllowVideoStreamCopy", true);
        body.addProperty("AllowAudioStreamCopy", true);
        body.add("DeviceProfile", profile);
        return post("/Items/" + encode(itemId) + "/PlaybackInfo", body).thenApply(json -> {
            JsonObject result = json.getAsJsonObject();
            JsonArray sources = result.getAsJsonArray("MediaSources");
            if (sources == null || sources.isEmpty()) {
                throw new IllegalStateException("Jellyfin has no playable version of this item");
            }
            JsonObject source = sources.get(0).getAsJsonObject();
            if (!source.has("TranscodingUrl") || source.get("TranscodingUrl").isJsonNull()) {
                throw new IllegalStateException("Jellyfin didn't offer a stream for this item");
            }
            String path = source.get("TranscodingUrl").getAsString();
            return new Playback(path.startsWith("http") ? path : server + path, itemId,
                    source.get("Id").getAsString(), result.get("PlaySessionId").getAsString());
        });
    }

    /** Tells Jellyfin playback started, so it shows in its dashboard and keeps the stream alive. */
    public CompletableFuture<Void> reportStarted(Playback playback) {
        return post("/Sessions/Playing", playbackInfo(playback)).thenApply(json -> null);
    }

    /** Tells Jellyfin playback stopped {@code seconds} in, so it saves the position and stops transcoding. */
    public CompletableFuture<Void> reportStopped(Playback playback, double seconds) {
        JsonObject info = playbackInfo(playback);
        info.addProperty("PositionTicks", (long) (seconds * TICKS_PER_SECOND));
        return post("/Sessions/Playing/Stopped", info).thenApply(json -> null);
    }

    private static JsonObject playbackInfo(Playback playback) {
        JsonObject info = new JsonObject();
        info.addProperty("ItemId", playback.itemId());
        info.addProperty("MediaSourceId", playback.mediaSourceId());
        info.addProperty("PlaySessionId", playback.playSessionId());
        return info;
    }

    private static List<Item> items(JsonElement json) {
        JsonArray array = json.getAsJsonObject().getAsJsonArray("Items");
        List<Item> items = new ArrayList<>(array.size());
        for (JsonElement element : array) {
            JsonObject item = element.getAsJsonObject();
            String type = string(item, "Type");
            String detail = item.has("ProductionYear") ? item.get("ProductionYear").getAsString() : "";
            JsonObject userData = item.has("UserData") && item.get("UserData").isJsonObject() ? item.getAsJsonObject("UserData") : null;
            double resume = userData != null && userData.has("PlaybackPositionTicks")
                    ? userData.get("PlaybackPositionTicks").getAsLong() / TICKS_PER_SECOND : 0;
            items.add(new Item(string(item, "Id"), string(item, "Name"), type,
                    item.has("IsFolder") && item.get("IsFolder").getAsBoolean(), detail, resume));
        }
        return items;
    }

    private static String string(JsonObject object, String key) {
        JsonElement value = object.get(key);
        return value == null || value.isJsonNull() ? "" : value.getAsString();
    }

    private CompletableFuture<JsonElement> get(String path) {
        return send(request(path).GET().build());
    }

    private CompletableFuture<JsonElement> post(String path, JsonObject body) {
        HttpRequest.BodyPublisher publisher = body == null ? HttpRequest.BodyPublishers.noBody()
                : HttpRequest.BodyPublishers.ofString(body.toString());
        return send(request(path).header("Content-Type", "application/json").POST(publisher).build());
    }

    private HttpRequest.Builder request(String path) {
        return HttpRequest.newBuilder(URI.create(server + path))
                .timeout(Duration.ofSeconds(20))
                .header("Authorization", authorization())
                .header("Accept", "application/json");
    }

    private static CompletableFuture<JsonElement> send(HttpRequest request) {
        return HTTP.sendAsync(request, HttpResponse.BodyHandlers.ofString()).thenApply(response -> {
            if (response.statusCode() == 401) {
                throw new IllegalStateException("Jellyfin refused the login (401)");
            }
            if (response.statusCode() / 100 != 2) {
                throw new IllegalStateException("Jellyfin answered " + response.statusCode());
            }
            String text = response.body();
            return text == null || text.isBlank() ? new JsonObject() : JsonParser.parseString(text);
        });
    }

    private static String encode(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8);
    }
}
