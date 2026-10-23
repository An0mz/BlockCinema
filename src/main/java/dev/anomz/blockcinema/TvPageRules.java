package dev.anomz.blockcinema;

import java.net.URI;
import java.net.URISyntaxException;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Pattern;

import org.jetbrains.annotations.Nullable;

/**
 * What a TV page URL may look like, checked by the server before it shares one with everybody. Pages are
 * our own (see the client's {@code TvPages}); only video IDs, a channel name or a video link can be in
 * them. No Minecraft or browser classes here, so the dedicated server can use it.
 */
public final class TvPageRules {
    /**
     * Our pages' host: a name under the author's own domain (anomz.dev), which identifies the mod to
     * YouTube and is the "parent" Twitch's embed checks. The pages are served from the jar, never fetched.
     */
    public static final String HOST = "blockcinema.anomz.dev";
    public static final int MAX_PAGE_LENGTH = 4096;
    public static final int MAX_LINK_LENGTH = 2048;

    private static final Pattern VIDEO_ID = Pattern.compile("[A-Za-z0-9_-]{11}");
    private static final Pattern LIST_ID = Pattern.compile("[A-Za-z0-9_-]{2,64}");
    /** A video's place in a playlist, from 0. */
    private static final Pattern LIST_INDEX = Pattern.compile("\\d{1,4}");
    public static final int MAX_LIST_INDEX = 9999;
    private static final Pattern TWITCH_CHANNEL = Pattern.compile("[a-z0-9_]{1,25}");
    /** Jellyfin's server and item IDs: 32 hex digits. */
    private static final Pattern JELLYFIN_ID = Pattern.compile("[0-9a-f]{32}");
    private static final String JELLYFIN_PREFIX = "jellyfin:";
    /** A Jellyfin stream index, or -1 (audio: the default track; subtitles: none). */
    private static final Pattern JELLYFIN_TRACK = Pattern.compile("-1|\\d{1,3}");

    private TvPageRules() {
    }

    /**
     * Whether {@code page} is one of our pages for {@code service}. For Jellyfin it is only which video on
     * which server ({@link #jellyfinPage}): each viewer streams it with their own login.
     */
    public static boolean valid(TvService service, String page) {
        if (page.length() > MAX_PAGE_LENGTH) {
            return false;
        }
        if (service == TvService.JELLYFIN || page.startsWith(JELLYFIN_PREFIX)) {
            return service == TvService.JELLYFIN && jellyfinIds(page) != null;
        }
        URI uri;
        try {
            uri = new URI(page);
        } catch (URISyntaxException e) {
            return false;
        }
        String scheme = uri.getScheme();
        if (!HOST.equals(uri.getHost()) || uri.getPort() != -1 || uri.getRawFragment() != null || uri.getUserInfo() != null) {
            return false;
        }
        Map<String, String> query = query(uri.getRawQuery());
        if (query == null) {
            return false;
        }
        return switch (service) {
            case YOUTUBE -> "https".equals(scheme) && "/youtube.html".equals(uri.getRawPath()) && youtubeQuery(query);
            case TWITCH -> "https".equals(scheme) && "/twitch.html".equals(uri.getRawPath()) && query.size() == 1
                    && TWITCH_CHANNEL.matcher(query.getOrDefault("channel", "")).matches();
            case DIRECT_LINK -> "/video.html".equals(uri.getRawPath()) && query.size() == 1
                    && videoSource(query.get("src"), scheme) != null;
            case JELLYFIN -> false;
        };
    }

    /** What a Jellyfin TV shares: the server's ID and the video's ID, never a login or an address. */
    public static String jellyfinPage(String serverId, String itemId) {
        return jellyfinPage(serverId, itemId, -1, -1);
    }

    /**
     * Like {@link #jellyfinPage(String, String)}, with the audio and subtitle tracks everyone watching gets
     * (Jellyfin stream indexes; -1 is the default audio and no subtitles).
     */
    public static String jellyfinPage(String serverId, String itemId, int audio, int subtitle) {
        String page = JELLYFIN_PREFIX + serverId.toLowerCase(Locale.ROOT) + ":" + itemId.toLowerCase(Locale.ROOT);
        return audio < 0 && subtitle < 0 ? page : page + ":" + Math.max(-1, audio) + ":" + Math.max(-1, subtitle);
    }

    /** The audio and subtitle tracks in a {@link #jellyfinPage} ({-1, -1} when it names none). */
    public static int[] jellyfinTracks(String page) {
        String[] parts = jellyfinIds(page);
        return parts == null || parts.length < 4 ? new int[] { -1, -1 }
                : new int[] { Integer.parseInt(parts[2]), Integer.parseInt(parts[3]) };
    }

    /** The server and item IDs (then any tracks) in a {@link #jellyfinPage}, or null if it isn't one. */
    @Nullable
    public static String[] jellyfinIds(String page) {
        if (!page.startsWith(JELLYFIN_PREFIX)) {
            return null;
        }
        String[] ids = page.substring(JELLYFIN_PREFIX.length()).split(":", -1);
        boolean tracks = ids.length == 4 && JELLYFIN_TRACK.matcher(ids[2]).matches() && JELLYFIN_TRACK.matcher(ids[3]).matches();
        return (ids.length == 2 || tracks) && JELLYFIN_ID.matcher(ids[0]).matches() && JELLYFIN_ID.matcher(ids[1]).matches()
                ? ids : null;
    }

    /** The host a video-link page plays from (lowercase), or null if it isn't a valid video-link page. */
    @Nullable
    public static String linkHost(String page) {
        try {
            URI uri = new URI(page);
            Map<String, String> query = query(uri.getRawQuery());
            URI src = query == null ? null : videoSource(query.get("src"), uri.getScheme());
            return src == null ? null : src.getHost().toLowerCase(Locale.ROOT);
        } catch (URISyntaxException e) {
            return null;
        }
    }

    /** Whether {@code host} is in {@code hosts} or a subdomain of one; an empty list allows any host. */
    public static boolean hostAllowed(List<? extends String> hosts, String host) {
        if (hosts.isEmpty()) {
            return true;
        }
        String lower = host.toLowerCase(Locale.ROOT);
        for (String allowed : hosts) {
            String entry = allowed.trim().toLowerCase(Locale.ROOT);
            if (lower.equals(entry) || lower.endsWith("." + entry)) {
                return true;
            }
        }
        return false;
    }

    private static boolean youtubeQuery(Map<String, String> query) {
        String video = query.get("v");
        String list = query.get("list");
        String index = query.get("index");
        String isShort = query.get("shorts");
        if (video == null && list == null || index != null && list == null || isShort != null && (video == null || list != null)
                || query.size() != (video != null ? 1 : 0) + (list != null ? 1 : 0) + (index != null ? 1 : 0) + (isShort != null ? 1 : 0)) {
            return false;
        }
        return (video == null || VIDEO_ID.matcher(video).matches()) && (list == null || LIST_ID.matcher(list).matches())
                && (index == null || LIST_INDEX.matcher(index).matches()) && (isShort == null || "1".equals(isShort));
    }

    /** Whether a YouTube page plays a Short (it came from a shorts link): fullscreen starts in the vertical view. */
    public static boolean youtubeShort(String page) {
        String list = youtubeList(page);
        return "1".equals(youtubeParam(page, "shorts")) || list != null && list.startsWith(CHANNEL_SHORTS);
    }

    /** A channel's Shorts as a playlist: this, then the channel ID without its "UC". */
    public static final String CHANNEL_SHORTS = "UUSH";

    /** Where a YouTube playlist page starts (from 0). */
    public static int youtubeListIndex(String page) {
        String index = youtubeParam(page, "index");
        return index != null && LIST_INDEX.matcher(index).matches() ? Integer.parseInt(index) : 0;
    }

    /** The playlist a YouTube page plays, or null if it plays a single video. */
    @Nullable
    public static String youtubeList(String page) {
        return youtubeParam(page, "list");
    }

    @Nullable
    private static String youtubeParam(String page, String name) {
        try {
            URI uri = new URI(page);
            Map<String, String> query = query(uri.getRawQuery());
            return query == null || !"/youtube.html".equals(uri.getRawPath()) ? null : query.get(name);
        } catch (URISyntaxException e) {
            return null;
        }
    }

    /** Our YouTube page playing playlist {@code list} from video {@code index} (counting from 0). */
    public static String youtubeListPage(String list, int index) {
        return "https://" + HOST + "/youtube.html?list=" + list + "&index=" + Math.max(0, Math.min(index, MAX_LIST_INDEX));
    }

    /** The video link in a video page, if it is an http(s) link served under the page's own scheme. */
    @Nullable
    private static URI videoSource(@Nullable String src, String pageScheme) {
        if (src == null || src.length() > MAX_LINK_LENGTH || !("https".equals(pageScheme) || "http".equals(pageScheme))) {
            return null;
        }
        try {
            URI uri = new URI(src);
            boolean ok = pageScheme.equals(uri.getScheme()) && uri.getHost() != null && uri.getUserInfo() == null;
            return ok ? uri : null;
        } catch (URISyntaxException e) {
            return null;
        }
    }

    /** Decoded query parameters, or null if a name repeats or the query is malformed. */
    @Nullable
    private static Map<String, String> query(@Nullable String raw) {
        Map<String, String> params = new HashMap<>();
        if (raw == null || raw.isEmpty()) {
            return params;
        }
        for (String pair : raw.split("&")) {
            int eq = pair.indexOf('=');
            if (eq <= 0) {
                return null;
            }
            try {
                String name = URLDecoder.decode(pair.substring(0, eq), StandardCharsets.UTF_8);
                String value = URLDecoder.decode(pair.substring(eq + 1), StandardCharsets.UTF_8);
                if (params.put(name, value) != null) {
                    return null;
                }
            } catch (IllegalArgumentException e) {
                return null;
            }
        }
        return params;
    }
}
