package dev.anomz.blockcinema.client.browser;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.function.Consumer;

import org.cef.CefClient;
import org.cef.browser.CefBrowser;
import org.cef.browser.CefFrame;
import org.cef.handler.CefLoadHandler.ErrorCode;
import org.cef.handler.CefLoadHandlerAdapter;
import org.jetbrains.annotations.Nullable;

import com.cinemamod.mcef.MCEF;
import com.cinemamod.mcef.MCEFBrowser;
import com.cinemamod.mcef.MCEFClient;

import dev.anomz.blockcinema.BlockCinema;
import dev.anomz.blockcinema.TvClockFollower;
import dev.anomz.blockcinema.TvPageRules;
import dev.anomz.blockcinema.TvService;
import dev.anomz.blockcinema.TvSource;
import dev.anomz.blockcinema.block.TvBlockEntity;
import dev.anomz.blockcinema.block.TvLayout;
import dev.anomz.blockcinema.client.Agreements;
import dev.anomz.blockcinema.client.BlockCinemaClientConfig;
import dev.anomz.blockcinema.client.SavedVideos;
import dev.anomz.blockcinema.client.TvRemote;
import dev.anomz.blockcinema.client.jellyfin.JellyfinWatch;
import it.unimi.dsi.fastutil.longs.Long2ObjectMap;
import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import it.unimi.dsi.fastutil.longs.LongSet;
import net.minecraft.Util;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Options;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.fml.loading.FMLPaths;

/**
 * The browsers playing on TV screens on this client, one per screen, keyed by the screen's origin (its
 * bottom-left TV seen from the front). They follow what the server says each screen plays (see
 * {@link TvSource}): started at the shared position, kept in step with pauses and jumps, and closed when
 * the screen is turned off, broken or too far away. Client thread only.
 */
public final class TvPlayers {
    private static final int WIDTH = 1280;
    private static final int HEIGHT = 720;
    private static final int HD_WIDTH = 1920;
    private static final int HD_HEIGHT = 1080;
    private static final int HD_COLUMNS = 4;
    private static final double MAX_DISTANCE = 96;
    private static final int STATE_RESEND_INTERVAL = 8;
    /**
     * A screen not seen from the front for this long counts as out of sight. YouTube's terms don't allow
     * playing in a player the user can't see, so our YouTube page pauses until it is seen again, even
     * with {@link BlockCinemaClientConfig#keepPlayingOutOfSight} on (which lifts {@link #MAX_DISTANCE}).
     */
    private static final long OUT_OF_SIGHT_MILLIS = 3000;
    private static final long REPORT_MILLIS = 1100;
    private static final double MAX_SEEK_LEAD_SECONDS = 10;

    private static final Long2ObjectMap<Playing> PLAYING = new Long2ObjectOpenHashMap<>();
    private static final WatchPositions POSITIONS =
            new WatchPositions(FMLPaths.CONFIGDIR.get().resolve(BlockCinema.MODID).resolve("positions.json"));
    private static final Consumer<Position> NOTHING = position -> {
    };
    private static final Long2ObjectMap<Hidden> HIDDEN = new Long2ObjectOpenHashMap<>();
    @Nullable
    private static Long focused;
    @Nullable
    private static Long fullscreenOrigin;
    private static MCEFClient client;
    private static int nextTextureId;
    private static boolean gamePaused;

    public enum Hidden {
        VIDEO_OFF,
        NOT_LOOKED_AT,
        TOO_MANY
    }

    public record Position(double time, double length, boolean paused, boolean loading) {
        public boolean seekable() {
            return length > 0;
        }
    }

    private static final class Playing {
        final MCEFBrowser browser;
        final ResourceLocation texture;
        String key;
        /** Key for {@link WatchPositions}, or null when the service keeps the position itself. */
        @Nullable
        String resumeKey;
        Consumer<Position> onStop;
        int listIndex = -1;
        boolean portrait;
        @Nullable
        TvSource synced;
        @Nullable
        Boolean captions;
        boolean pageHeard;
        boolean verticalView;
        boolean endedSent;
        @Nullable
        String error;
        @Nullable
        Position position;
        long positionMillis;
        double rate = 1;
        long seekCooldown = SyncPlan.SEEK_SYNC_MILLIS;
        double seekLead;
        long seekSentMillis;
        boolean seekLoaded;
        float userVolume = BlockCinemaClientConfig.startVolume();
        float worldGain = 1;
        boolean fullscreen;
        int width = WIDTH;
        /** Last volume sent to the page; -1 sends it again. */
        float volume = -1;
        boolean visible = true;
        float pan;
        int checksSinceStateSent;
        /** Written by the renderer, read by the tick; both on the client thread. */
        long lastSeenMillis = Util.getMillis();
        long lastSeekSyncMillis;
        long lastPauseSyncMillis;

        Playing(MCEFBrowser browser, ResourceLocation texture) {
            this.browser = browser;
            this.texture = texture;
        }

        void stopped() {
            Consumer<Position> run = onStop;
            onStop = NOTHING;
            Position last = position != null ? position : new Position(0, 0, true, false);
            if (resumeKey != null) {
                POSITIONS.remember(resumeKey, last.time(), last.length());
            }
            run.accept(last);
        }
    }

    private TvPlayers() {
    }

    /** Whether Chromium is ready; false while MCEF is still downloading it, or if it failed to start. */
    public static boolean ready() {
        return MCEF.isInitialized();
    }

    public static double resumeSeconds(String page) {
        return POSITIONS.get(page);
    }

    public static String withStart(String pageUrl, double seconds) {
        if (seconds < 1) {
            return pageUrl;
        }
        return pageUrl + (pageUrl.contains("#") ? "&" : "#") + "start=" + (long) seconds;
    }

    public static boolean isPlaying(BlockPos origin) {
        return PLAYING.containsKey(origin.asLong());
    }

    public static void stopAll() {
        PLAYING.values().forEach(TvPlayers::close);
        PLAYING.clear();
        JellyfinWatch.clear();
        LocalVideos.stop();
        BlockCinemaClientConfig.saveVolume(true);
    }

    @Nullable
    public static ResourceLocation texture(BlockPos origin) {
        Playing playing = PLAYING.get(origin.asLong());
        return playing == null || playing.browser.getRenderer().getTextureID() == 0 ? null : playing.texture;
    }

    @Nullable
    public static Position position(BlockPos origin) {
        Playing playing = PLAYING.get(origin.asLong());
        return playing == null ? null : playing.position;
    }

    static void report(CefBrowser browser, double time, double length, boolean paused, boolean loading) {
        for (Playing playing : PLAYING.values()) {
            if (playing.browser == browser) {
                playing.position = new Position(time, length, paused, loading);
                playing.positionMillis = Util.getMillis();
                playing.seekLoaded |= loading;
            }
        }
    }

    static void reportEnded(CefBrowser browser, int nextInList) {
        for (Long2ObjectMap.Entry<Playing> entry : PLAYING.long2ObjectEntrySet()) {
            Playing playing = entry.getValue();
            if (playing.browser == browser && !playing.endedSent) {
                playing.endedSent = true;
                TvRemote.ended(BlockPos.of(entry.getLongKey()), playing.key, nextInList);
            }
        }
    }

    static void reportListIndex(CefBrowser browser, int index) {
        for (Playing playing : PLAYING.values()) {
            if (playing.browser == browser) {
                playing.listIndex = index;
            }
        }
    }

    public static int listIndex(BlockPos origin) {
        Playing playing = PLAYING.get(origin.asLong());
        return playing == null ? -1 : playing.listIndex;
    }

    public static boolean portrait(BlockPos origin) {
        Playing playing = PLAYING.get(origin.asLong());
        return playing != null && playing.portrait;
    }

    static void reportTitle(CefBrowser browser, String title) {
        for (Playing playing : PLAYING.values()) {
            if (playing.browser == browser) {
                SavedVideos.get().rename(playing.key, title);
            }
        }
    }

    static void reportError(CefBrowser browser, @Nullable String code) {
        for (Playing playing : PLAYING.values()) {
            if (playing.browser == browser) {
                playing.error = code;
            }
        }
    }

    /**
     * What went wrong playing on the screen at {@code origin} (a code for the
     * {@code screen.blockcinema.error.*} texts), or null.
     */
    @Nullable
    public static String error(BlockPos origin) {
        Playing playing = PLAYING.get(origin.asLong());
        return playing == null ? null : playing.error;
    }

    public static float volume(BlockPos origin) {
        Playing playing = PLAYING.get(origin.asLong());
        return playing == null ? 1 : playing.userVolume;
    }

    public static void setVolume(BlockPos origin, float volume) {
        Playing playing = PLAYING.get(origin.asLong());
        if (playing != null) {
            playing.userVolume = Math.max(0, Math.min(1, volume));
            BlockCinemaClientConfig.rememberVolume(playing.userVolume);
            sendState(playing, playing.worldGain * playing.userVolume, playing.visible);
        }
    }

    public static void setFullscreen(BlockPos origin, boolean fullscreen) {
        fullscreenOrigin = fullscreen ? origin.asLong() : null;
        Playing playing = PLAYING.get(origin.asLong());
        if (playing != null) {
            playing.fullscreen = fullscreen;
            playing.volume = -1;
            playing.lastSeenMillis = Util.getMillis();
        }
    }

    public static void setVerticalView(BlockPos origin, boolean vertical) {
        Playing playing = PLAYING.get(origin.asLong());
        if (playing != null) {
            playing.verticalView = vertical;
        }
    }

    public static void markSeen(BlockPos origin) {
        Playing playing = PLAYING.get(origin.asLong());
        if (playing != null) {
            playing.lastSeenMillis = Util.getMillis();
        }
    }

    public static void tick(ClientLevel level, Player player) {
        long now = Util.getMillis();
        BlockCinemaClientConfig.saveVolume(false);
        boolean gamePaused = Minecraft.getInstance().isPaused();
        if (gamePaused != TvPlayers.gamePaused) {
            TvPlayers.gamePaused = gamePaused;
            PLAYING.values().forEach(playing -> playing.lastPauseSyncMillis = 0);
        }
        if (gamePaused) {
            TvClockFollower.INSTANCE.hold(now);
        }
        LongSet wanted = new LongOpenHashSet();
        HIDDEN.clear();
        if (ready()) {
            for (Candidate candidate : choose(candidates(level, player), player)) {
                follow(level, player, candidate.tv(), candidate.layout(), candidate.source(), now, wanted);
            }
        }
        PLAYING.long2ObjectEntrySet().removeIf(entry -> {
            if (wanted.contains(entry.getLongKey())) {
                return false;
            }
            close(entry.getValue());
            return true;
        });
    }

    private record Candidate(TvBlockEntity tv, TvLayout layout, TvSource source, double distanceSqr) {
    }

    private static List<Candidate> candidates(ClientLevel level, Player player) {
        List<Candidate> candidates = new ArrayList<>();
        double maxDistanceSqr = BlockCinemaClientConfig.keepPlayingOutOfSight() ? Double.MAX_VALUE : MAX_DISTANCE * MAX_DISTANCE;
        for (TvBlockEntity tv : new ArrayList<>(TvBlockEntity.CLIENT_PLAYING)) {
            TvSource source = tv.source();
            BlockPos origin = tv.getBlockPos();
            double distanceSqr = origin.distToCenterSqr(player.position());
            if (source == null || tv.isRemoved() || tv.getLevel() != level || distanceSqr > maxDistanceSqr) {
                continue;
            }
            TvLayout layout = tv.layout(level.getGameTime());
            if (!layout.invalid() && layout.origin().equals(origin)) {
                candidates.add(new Candidate(tv, layout, source, distanceSqr));
            }
        }
        return candidates;
    }

    private static List<Candidate> choose(List<Candidate> candidates, Player player) {
        candidates.sort(Comparator.comparingDouble(Candidate::distanceSqr));
        List<Candidate> chosen = new ArrayList<>();
        Long focus = BlockCinemaClientConfig.onlyTvYouLookAt() ? focus(candidates, player) : null;
        for (Candidate candidate : candidates) {
            long key = candidate.tv().getBlockPos().asLong();
            Hidden hidden = null;
            if (fullscreenOrigin == null || fullscreenOrigin != key) {
                if (!BlockCinemaClientConfig.playVideos()) {
                    hidden = Hidden.VIDEO_OFF;
                } else if (focus != null && focus != key) {
                    hidden = Hidden.NOT_LOOKED_AT;
                } else if (chosen.size() >= BlockCinemaClientConfig.maxScreens()) {
                    hidden = Hidden.TOO_MANY;
                }
            }
            if (hidden == null) {
                chosen.add(candidate);
            } else {
                HIDDEN.put(key, hidden);
            }
        }
        return chosen;
    }

    @Nullable
    private static Long focus(List<Candidate> candidates, Player player) {
        if (candidates.isEmpty()) {
            return null;
        }
        Vec3 eye = player.getEyePosition();
        Vec3 end = eye.add(player.getViewVector(1).scale(MAX_DISTANCE));
        double best = Double.MAX_VALUE;
        Long looked = null;
        for (Candidate candidate : candidates) {
            Optional<Vec3> hit = candidate.layout().bounds().clip(eye, end);
            if (hit.isPresent() && hit.get().distanceToSqr(eye) < best) {
                best = hit.get().distanceToSqr(eye);
                looked = candidate.tv().getBlockPos().asLong();
            }
        }
        if (looked != null) {
            focused = looked;
        } else if (focused == null || candidates.stream().noneMatch(c -> c.tv().getBlockPos().asLong() == focused)) {
            focused = candidates.get(0).tv().getBlockPos().asLong();
        }
        return focused;
    }

    @Nullable
    public static Hidden hidden(BlockPos origin) {
        return HIDDEN.get(origin.asLong());
    }

    private static void follow(ClientLevel level, Player player, TvBlockEntity tv, TvLayout layout, TvSource source,
            long now, LongSet wanted) {
        BlockPos origin = tv.getBlockPos();
        String key = source.page();
        long clockTicks = TvClockFollower.INSTANCE.now(level.getGameTime(), now);
        Playing playing = PLAYING.get(origin.asLong());
        if (playing == null || !playing.key.equals(key)) {
            String page = key;
            String resumeKey = key;
            Consumer<Position> onStop = NOTHING;
            if (source.service() == TvService.YOUTUBE && !Agreements.youtube()) {
                return;
            }
            if (source.service() == TvService.JELLYFIN) {
                // Streamed with this player's own Jellyfin login, once that's set up; until then (or without
                // a login on that server) the screen shows who is watching.
                String[] ids = TvPageRules.jellyfinIds(key);
                JellyfinWatch.Stream stream = ids != null && JellyfinWatch.canWatch(ids[0]) ? JellyfinWatch.take(key, ids[1]) : null;
                if (stream == null) {
                    return;
                }
                page = stream.page();
                resumeKey = null;
                onStop = stream.onStop();
            }
            String file = source.service() == TvService.DIRECT_LINK ? TvPageRules.localFile(key) : null;
            if (file != null) {
                // A file of the players' own: played from this player's videos folder, if it is there too.
                String url = LocalVideos.url(file);
                if (url == null) {
                    return;
                }
                page = VideoLinks.videoPage(url, null);
            }
            playing = start(origin, playing, withStart(page, source.position(clockTicks)), key, resumeKey, onStop);
        }
        wanted.add(origin.asLong());
        boolean portrait = playing.fullscreen ? playing.verticalView : layout.portrait();
        double longSide = Math.max(layout.cols() * layout.frame().cellWidth(), layout.rows());
        int width = switch (BlockCinemaClientConfig.quality()) {
            case P720 -> WIDTH;
            case P1080 -> HD_WIDTH;
            case AUTO -> playing.fullscreen || longSide >= HD_COLUMNS ? HD_WIDTH : WIDTH;
        };
        if (width != playing.width || portrait != playing.portrait) {
            playing.width = width;
            playing.portrait = portrait;
            int height = width == HD_WIDTH ? HD_HEIGHT : HEIGHT;
            playing.browser.resize(portrait ? height : width, portrait ? width : height);
        }
        syncState(playing, source, clockTicks, now);
        updateVolume(playing, layout, player, now,
                source.service() != TvService.YOUTUBE && BlockCinemaClientConfig.keepPlayingOutOfSight());
        updateCaptions(playing);
    }

    private static void updateCaptions(Playing playing) {
        boolean captions = BlockCinemaClientConfig.youtubeCaptions();
        if (!playing.pageHeard || Boolean.valueOf(captions).equals(playing.captions)) {
            return;
        }
        playing.captions = captions;
        // The game's language, "fr_fr" -> "fr", for the caption track.
        String language = Minecraft.getInstance().getLanguageManager().getSelected().split("_")[0];
        run(playing, "window.blockcinemaCaptions && blockcinemaCaptions(" + captions + ", '" + language.replaceAll("[^a-z]", "") + "')");
    }

    private static Playing start(BlockPos origin, @Nullable Playing playing, String url, String key,
            @Nullable String resumeKey, Consumer<Position> onStop) {
        if (playing == null) {
            MCEFBrowser browser = new MCEFBrowser(client(), url, false);
            // A TV in the world must never change the player's mouse cursor (MCEF's default does).
            browser.setCursorChangeListener(cursor -> {
            });
            browser.setCloseAllowed();
            browser.createImmediately();
            browser.resize(WIDTH, HEIGHT);
            ResourceLocation texture = ResourceLocation.fromNamespaceAndPath(BlockCinema.MODID, "tv_browser_" + nextTextureId++);
            Minecraft.getInstance().getTextureManager().register(texture, new BrowserTexture(browser));
            playing = new Playing(browser, texture);
            PLAYING.put(origin.asLong(), playing);
        } else {
            playing.stopped();
            playing.browser.loadURL(url);
        }
        playing.key = key;
        playing.resumeKey = resumeKey;
        playing.onStop = onStop;
        playing.position = null;
        playing.error = null;
        playing.endedSent = false;
        playing.listIndex = -1;
        playing.pageHeard = false;
        playing.captions = null;
        playing.synced = null;
        playing.volume = -1;
        playing.lastSeenMillis = Util.getMillis();
        playing.lastSeekSyncMillis = Util.getMillis();
        playing.positionMillis = 0;
        playing.rate = 1;
        playing.seekCooldown = SyncPlan.SEEK_SYNC_MILLIS;
        playing.seekSentMillis = 0;
        playing.seekLoaded = false;
        return playing;
    }

    private static void syncState(Playing playing, TvSource source, long clockTicks, long now) {
        if (playing.synced == null) {
            playing.synced = source;
        } else if (!source.equals(playing.synced)) {
            // Someone paused, resumed or jumped: follow at once, not at the gentle drift-correction pace.
            playing.synced = source;
            playing.lastPauseSyncMillis = 0;
            playing.lastSeekSyncMillis = 0;
            playing.seekCooldown = SyncPlan.SEEK_SYNC_MILLIS;
        }
        Position position = playing.position;
        if (position == null) {
            return;
        }
        double time = position.time();
        if (!position.paused() && !position.loading()) {
            time += (now - playing.positionMillis) / 1000.0 * playing.rate;
        }
        double expected = source.position(clockTicks);
        noteJumpLoaded(playing, position, expected - time, now);
        SyncPlan plan = SyncPlan.of(new SyncPlan.Input(source.paused(), gamePaused, playing.visible, expected, time,
                position.length(), position.paused(), position.loading(), playing.rate, now - playing.lastPauseSyncMillis,
                now - playing.lastSeekSyncMillis, playing.seekCooldown, playing.seekLead));
        if (plan.setPaused() != null) {
            playing.lastPauseSyncMillis = now;
            run(playing, "window.blockcinemaSetPaused && blockcinemaSetPaused(" + plan.setPaused() + ")");
        }
        if (plan.seekTo() != null) {
            playing.lastSeekSyncMillis = now;
            playing.seekSentMillis = now;
            playing.seekLoaded = false;
            run(playing, String.format(Locale.ROOT, "window.blockcinemaSeek && blockcinemaSeek(%.1f)", plan.seekTo()));
        }
        if (plan.setRate() != null) {
            playing.rate = plan.setRate();
            run(playing, String.format(Locale.ROOT, "window.blockcinemaRate && blockcinemaRate(%.2f)", plan.setRate()));
        }
    }

    private static void noteJumpLoaded(Playing playing, Position position, double drift, long now) {
        if (playing.seekSentMillis == 0 || position.loading() || playing.positionMillis < playing.seekSentMillis
                || now - playing.seekSentMillis < REPORT_MILLIS) {
            return;
        }
        if (!position.paused()) {
            double loaded = playing.seekLoaded ? (playing.positionMillis - playing.seekSentMillis) / 1000.0 : 0;
            playing.seekLead = Math.min((playing.seekLead + loaded) / 2, MAX_SEEK_LEAD_SECONDS);
            playing.seekCooldown = SyncPlan.nextSeekCooldown(playing.seekCooldown, drift);
        }
        playing.seekSentMillis = 0;
    }

    private static void updateVolume(Playing playing, TvLayout layout, Player player, long now, boolean playsUnseen) {
        if (!playing.pageHeard && playing.position != null) {
            // The page is up now: what was sent while it loaded was lost (it starts silent meanwhile).
            playing.pageHeard = true;
            playing.volume = -1;
        }
        Options options = Minecraft.getInstance().options;
        float gain = options.getSoundSourceVolume(SoundSource.MASTER) * options.getSoundSourceVolume(SoundSource.RECORDS);
        playing.worldGain = gain * (playing.fullscreen ? 1 : TvVolume.falloff(distance(layout, player.getEyePosition())));
        Vec3 toScreen = layout.bounds().getCenter().subtract(player.getEyePosition());
        float pan = playing.fullscreen || !BlockCinemaClientConfig.directionalSound() ? 0
                : TvVolume.pan(toScreen.x, toScreen.z, player.getYRot());
        updateState(playing, playing.worldGain * playing.userVolume,
                playing.fullscreen || playsUnseen || now - playing.lastSeenMillis < OUT_OF_SIGHT_MILLIS, pan);
    }

    private static double distance(TvLayout layout, Vec3 ear) {
        AABB box = layout.bounds();
        return TvVolume.distanceToBox(ear.x, ear.y, ear.z, box.minX, box.minY, box.minZ, box.maxX, box.maxY, box.maxZ);
    }

    private static void updateState(Playing playing, float volume, boolean visible, float pan) {
        if (Math.abs(volume - playing.volume) < 0.005f && visible == playing.visible && Math.abs(pan - playing.pan) < 0.02f
                && ++playing.checksSinceStateSent < STATE_RESEND_INTERVAL) {
            return;
        }
        playing.pan = pan;
        sendState(playing, volume, visible);
    }

    private static void sendState(Playing playing, float volume, boolean visible) {
        if (visible && !playing.visible) {
            playing.lastSeekSyncMillis = 0;
        }
        playing.volume = volume;
        playing.visible = visible;
        playing.checksSinceStateSent = 0;
        run(playing, String.format(Locale.ROOT, "window.blockcinemaState && blockcinemaState(%.3f, %b, %.2f)", volume, visible,
                playing.pan));
    }

    private static void run(Playing playing, String script) {
        playing.browser.executeJavaScript(script, playing.browser.getURL(), 0);
    }

    private static void close(Playing playing) {
        playing.stopped();
        playing.browser.close();
        Minecraft.getInstance().getTextureManager().release(playing.texture);
    }

    /**
     * Our own CEF client, so the navigation guard applies to TV browsers only and never clashes with
     * other mods using MCEF (java-cef keeps one request handler per client).
     */
    private static MCEFClient client() {
        if (client == null) {
            TvPages pages = new TvPages();
            MCEF.getApp().getHandle().registerSchemeHandlerFactory("https", TvPages.HOST, pages);
            MCEF.getApp().getHandle().registerSchemeHandlerFactory("http", TvPages.HOST, pages);
            CefClient handle = MCEF.getApp().getHandle().createClient();
            handle.addRequestHandler(new NavigationGuard.Requests());
            handle.addLifeSpanHandler(new NavigationGuard.Popups());
            client = new MCEFClient(handle);
            client.addDisplayHandler(new StateReports());
            client.addLoadHandler(new CefLoadHandlerAdapter() {
                @Override
                public void onLoadError(CefBrowser browser, CefFrame frame, ErrorCode errorCode, String errorText, String failedUrl) {
                    if (frame.isMain() && errorCode != ErrorCode.ERR_ABORTED) {
                        Minecraft.getInstance().execute(() -> reportError(browser, "load_failed"));
                    }
                }
            });
        }
        return client;
    }
}
