package dev.anomz.blockcinema.client.browser;

import java.net.URI;
import java.net.URISyntaxException;
import java.net.URLDecoder;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Pattern;

import org.jetbrains.annotations.Nullable;

import dev.anomz.blockcinema.TvPageRules;
import dev.anomz.blockcinema.TvService;

/**
 * Turns what the player typed into one of our player pages. For YouTube and Twitch only video/playlist
 * IDs and channel names are passed on, so nothing else can be loaded; direct links only ever play as video.
 */
public final class VideoLinks {
    private static final Pattern VIDEO_ID = Pattern.compile("[A-Za-z0-9_-]{11}");
    private static final Pattern LIST_ID = Pattern.compile("[A-Za-z0-9_-]{2,64}");
    private static final Pattern CHANNEL_ID = Pattern.compile("UC[A-Za-z0-9_-]{22}");
    private static final Pattern TWITCH_CHANNEL = Pattern.compile("[A-Za-z0-9_]{1,25}");
    private static final Set<String> YOUTUBE_HOSTS = Set.of("youtube.com", "www.youtube.com", "m.youtube.com",
            "music.youtube.com", "youtube-nocookie.com", "www.youtube-nocookie.com");
    private static final int MAX_LINK_LENGTH = 2048;
    private static final Set<String> TWITCH_HOSTS = Set.of("twitch.tv", "www.twitch.tv", "m.twitch.tv");
    private static final Set<String> UNPLAYABLE_FORMATS = Set.of("avi", "wmv", "flv", "mpg", "mpeg", "vob", "rm", "rmvb");

    private VideoLinks() {
    }

    public static Optional<String> youtube(String input) {
        String text = input.trim();
        if (VIDEO_ID.matcher(text).matches()) {
            return Optional.of(youtubePage(text, null));
        }
        URI uri = parse(text);
        if (uri == null || uri.getHost() == null) {
            return Optional.empty();
        }
        String host = uri.getHost().toLowerCase(Locale.ROOT);
        String path = uri.getPath() == null ? "" : uri.getPath();
        String video = null;
        boolean isShort = false;
        if (host.equals("youtu.be")) {
            video = firstSegment(path);
        } else if (YOUTUBE_HOSTS.contains(host)) {
            String channel = afterPrefix(path, "/channel/");
            if (channel != null && CHANNEL_ID.matcher(channel).matches()) {
                // A channel plays as its uploads playlist: its Shorts from a shorts link, else all its videos.
                String uploads = (path.endsWith("/shorts") ? TvPageRules.CHANNEL_SHORTS : "UU") + channel.substring(2);
                return Optional.of(youtubePage(null, uploads));
            }
            video = path.equals("/watch") ? query(uri, "v") : afterPrefix(path, "/shorts/", "/live/", "/embed/");
            isShort = path.startsWith("/shorts/");
        } else {
            return Optional.empty();
        }
        String list = query(uri, "list");
        boolean videoOk = video != null && VIDEO_ID.matcher(video).matches();
        boolean listOk = list != null && LIST_ID.matcher(list).matches();
        if (!videoOk && !listOk) {
            return Optional.empty();
        }
        String page = youtubePage(videoOk ? video : null, listOk ? list : null);
        return Optional.of(isShort && videoOk && !listOk ? page + "&shorts=1" : page);
    }

    public static Optional<String> twitch(String input) {
        String text = input.trim();
        String kind = "channel";
        Pattern allowed = TWITCH_CHANNEL;
        String value = text;
        if (!TWITCH_CHANNEL.matcher(text).matches()) {
            URI uri = parse(text);
            String host = uri == null || uri.getHost() == null ? "" : uri.getHost().toLowerCase(Locale.ROOT);
            String path = uri == null || uri.getPath() == null ? "" : uri.getPath();
            boolean clipHost = host.equals("clips.twitch.tv");
            if (!clipHost && !TWITCH_HOSTS.contains(host)) {
                return Optional.empty();
            }
            int clip = clipHost ? 0 : path.indexOf("/clip/");
            if (clip >= 0) {
                kind = "clip";
                allowed = TvPageRules.TWITCH_CLIP;
                value = firstSegment(path.substring(clipHost ? 0 : clip + 5));
            } else if (path.startsWith("/videos/")) {
                kind = "video";
                allowed = TvPageRules.TWITCH_VIDEO;
                value = afterPrefix(path, "/videos/");
            } else {
                value = firstSegment(path);
            }
        }
        if (value == null || !allowed.matcher(value).matches()) {
            return Optional.empty();
        }
        return Optional.of(TvPages.ORIGIN + "/twitch.html?" + kind + "=" + (kind.equals("channel") ? value.toLowerCase(Locale.ROOT) : value));
    }

    /** The file ending of a link Chromium has no player for (like "avi"), or null. */
    @Nullable
    public static String unplayableFormat(String input) {
        URI uri = parse(input.trim());
        return unplayableFile(uri == null || uri.getPath() == null ? "" : uri.getPath());
    }

    /** The same for a file's name or path. */
    @Nullable
    public static String unplayableFile(String name) {
        String ending = name.substring(name.lastIndexOf('.') + 1).toLowerCase(Locale.ROOT);
        return UNPLAYABLE_FORMATS.contains(ending) ? ending : null;
    }

    /**
     * The service whose site a link points to, or null. Those are web pages, not video files, so they
     * only play through that service's own player.
     */
    @Nullable
    public static TvService siteOf(String input) {
        URI uri = parse(input.trim());
        String host = uri == null || uri.getHost() == null ? "" : uri.getHost().toLowerCase(Locale.ROOT);
        if (host.equals("youtu.be") || YOUTUBE_HOSTS.contains(host)) {
            return TvService.YOUTUBE;
        }
        return host.equals("clips.twitch.tv") || TWITCH_HOSTS.contains(host) ? TvService.TWITCH : null;
    }

    public static Optional<String> direct(String input) {
        String text = input.trim();
        if (text.length() > MAX_LINK_LENGTH || !text.contains("://")) {
            return Optional.empty();
        }
        URI uri = parse(text);
        if (uri == null || uri.getHost() == null || uri.getUserInfo() != null) {
            return Optional.empty();
        }
        return Optional.of(videoPage(uri.toString(), null));
    }

    /**
     * Our video page playing {@code src}. The page uses the stream's scheme, as Chromium won't play an
     * http stream on an https page. {@code authorization} (a Jellyfin header) goes in the fragment,
     * which the browser never sends anywhere; the page adds it to its requests to that server.
     */
    public static String videoPage(String src, @Nullable String authorization) {
        String scheme = src.regionMatches(true, 0, "https:", 0, 6) ? "https" : "http";
        String page = TvPages.origin(scheme) + "/video.html?src=" + URLEncoder.encode(src, StandardCharsets.UTF_8);
        return authorization == null ? page
                : page + "#auth=" + URLEncoder.encode(authorization, StandardCharsets.UTF_8);
    }

    public static String label(String page) {
        String ownFile = TvPageRules.localFile(page);
        if (ownFile != null) {
            return ownFile;
        }
        URI uri = parse(page);
        if (uri == null || uri.getRawQuery() == null) {
            return "";
        }
        for (String name : new String[] { "v", "list", "channel", "video", "clip" }) {
            String value = query(uri, name);
            if (value != null) {
                return value;
            }
        }
        String src = query(uri, "src");
        if (src == null) {
            return "";
        }
        try {
            String link = URLDecoder.decode(src, StandardCharsets.UTF_8);
            String path = new URI(link).getPath();
            String file = path == null ? "" : path.substring(path.lastIndexOf('/') + 1);
            return file.isEmpty() ? link : file;
        } catch (URISyntaxException | IllegalArgumentException e) {
            return "";
        }
    }

    private static String youtubePage(String video, String list) {
        StringBuilder url = new StringBuilder(TvPages.ORIGIN).append("/youtube.html?");
        if (video != null) {
            url.append("v=").append(video);
        }
        if (list != null) {
            url.append(video != null ? "&" : "").append("list=").append(list);
        }
        return url.toString();
    }

    private static URI parse(String text) {
        String withScheme = text.contains("://") ? text : "https://" + text;
        try {
            URI uri = new URI(withScheme);
            String scheme = uri.getScheme().toLowerCase(Locale.ROOT);
            return scheme.equals("https") || scheme.equals("http") ? uri : null;
        } catch (URISyntaxException e) {
            return null;
        }
    }

    private static String firstSegment(String path) {
        String[] parts = path.split("/");
        return parts.length > 1 && !parts[1].isEmpty() ? parts[1] : null;
    }

    private static String afterPrefix(String path, String... prefixes) {
        for (String prefix : prefixes) {
            if (path.startsWith(prefix)) {
                return firstSegment(path.substring(prefix.length() - 1));
            }
        }
        return null;
    }

    private static String query(URI uri, String name) {
        String query = uri.getRawQuery();
        if (query == null) {
            return null;
        }
        for (String pair : query.split("&")) {
            int eq = pair.indexOf('=');
            if (eq > 0 && pair.substring(0, eq).equals(name)) {
                return pair.substring(eq + 1);
            }
        }
        return null;
    }
}
