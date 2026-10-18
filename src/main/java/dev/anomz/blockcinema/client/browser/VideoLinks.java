package dev.anomz.blockcinema.client.browser;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * Turns what the player typed into one of our player pages. Only YouTube video/playlist links and
 * Twitch channels are accepted, and only their IDs are passed on, so nothing else can be loaded.
 */
public final class VideoLinks {
    private static final Pattern VIDEO_ID = Pattern.compile("[A-Za-z0-9_-]{11}");
    private static final Pattern LIST_ID = Pattern.compile("[A-Za-z0-9_-]{2,64}");
    private static final Pattern TWITCH_CHANNEL = Pattern.compile("[A-Za-z0-9_]{1,25}");
    private static final Set<String> YOUTUBE_HOSTS = Set.of("youtube.com", "www.youtube.com", "m.youtube.com",
            "music.youtube.com", "youtube-nocookie.com", "www.youtube-nocookie.com");
    private static final Set<String> TWITCH_HOSTS = Set.of("twitch.tv", "www.twitch.tv", "m.twitch.tv");

    private VideoLinks() {
    }

    /** A YouTube link (watch, youtu.be, shorts, live, embed or playlist) or a bare video ID. */
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
        if (host.equals("youtu.be")) {
            video = firstSegment(path);
        } else if (YOUTUBE_HOSTS.contains(host)) {
            video = path.equals("/watch") ? query(uri, "v") : afterPrefix(path, "/shorts/", "/live/", "/embed/");
        } else {
            return Optional.empty();
        }
        String list = query(uri, "list");
        boolean videoOk = video != null && VIDEO_ID.matcher(video).matches();
        boolean listOk = list != null && LIST_ID.matcher(list).matches();
        if (!videoOk && !listOk) {
            return Optional.empty();
        }
        return Optional.of(youtubePage(videoOk ? video : null, listOk ? list : null));
    }

    /** A Twitch channel name or channel link. */
    public static Optional<String> twitch(String input) {
        String text = input.trim();
        String channel = text;
        if (!TWITCH_CHANNEL.matcher(text).matches()) {
            URI uri = parse(text);
            if (uri == null || uri.getHost() == null || !TWITCH_HOSTS.contains(uri.getHost().toLowerCase(Locale.ROOT))) {
                return Optional.empty();
            }
            channel = firstSegment(uri.getPath() == null ? "" : uri.getPath());
            if (channel == null || !TWITCH_CHANNEL.matcher(channel).matches()) {
                return Optional.empty();
            }
        }
        return Optional.of(TvPages.ORIGIN + "/twitch.html?channel=" + channel.toLowerCase(Locale.ROOT));
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
