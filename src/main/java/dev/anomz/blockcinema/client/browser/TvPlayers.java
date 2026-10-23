package dev.anomz.blockcinema.client.browser;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.function.Consumer;

import org.cef.CefClient;
import org.cef.browser.CefBrowser;
import org.jetbrains.annotations.Nullable;

import com.cinemamod.mcef.MCEF;
import com.cinemamod.mcef.MCEFBrowser;
import com.cinemamod.mcef.MCEFClient;

import dev.anomz.blockcinema.BlockCinema;
import dev.anomz.blockcinema.TvPageRules;
import dev.anomz.blockcinema.TvService;
import dev.anomz.blockcinema.TvSource;
import dev.anomz.blockcinema.block.TvBlockEntity;
import dev.anomz.blockcinema.block.TvLayout;
import dev.anomz.blockcinema.client.Agreements;
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
    /** Browser size for small screens; big screens and fullscreen get {@link #HD_WIDTH} (and YouTube picks 1080p). */
    private static final int WIDTH = 1280;
    private static final int HEIGHT = 720;
    private static final int HD_WIDTH = 1920;
    private static final int HD_HEIGHT = 1080;
    /** Screens at least this many blocks wide play in 1080p. */
    private static final int HD_COLUMNS = 4;
    /** A screen this far from the player stops. */
    private static final double MAX_DISTANCE = 96;
    /** State updates sent at least this often (in checks), in case the page wasn't ready for the last one. */
    private static final int STATE_RESEND_INTERVAL = 8;
    /**
     * A screen not seen from the front for this long counts as out of sight. YouTube's terms don't allow
     * playing in a player the user can't see, so our YouTube page pauses until it is seen again.
     */
    private static final long OUT_OF_SIGHT_MILLIS = 3000;
    /** Being this far off the shared position makes the screen jump back in step. */
    private static final double MAX_DRIFT_SECONDS = 3;
    /** At most one catch-up jump this often, so a buffering video isn't pushed around. */
    private static final long SEEK_SYNC_MILLIS = 5000;
    private static final long PAUSE_SYNC_MILLIS = 1500;

    private static final Long2ObjectMap<Playing> PLAYING = new Long2ObjectOpenHashMap<>();
    private static final WatchPositions POSITIONS =
            new WatchPositions(FMLPaths.CONFIGDIR.get().resolve(BlockCinema.MODID).resolve("positions.json"));
    private static final Consumer<Position> NOTHING = position -> {
    };
    private static MCEFClient client;
    private static int nextTextureId;
    private static boolean gamePaused;

    /** Where a video is: seconds in, its length (0 when live or not known yet) and whether it is paused. */
    public record Position(double time, double length, boolean paused) {
        public boolean seekable() {
            return length > 0;
        }
    }

    private static final class Playing {
        final MCEFBrowser browser;
        final ResourceLocation texture;
        /** What is loaded: the shared page (for Jellyfin, which video on which server). */
        String key;
        /** Key for {@link WatchPositions}, or null when the service keeps the position itself. */
        @Nullable
        String resumeKey;
        /** Runs once with the last position when this stops playing (turned off, replaced, broken or too far away). */
        Consumer<Position> onStop;
        /** The page's last report (see {@link StateReports}); null until the first one. */
        @Nullable
        Position position;
        /** This TV's own volume, 0 to 1, set from its menu. */
        float userVolume = 1;
        /** Volume from the sound settings and the player's distance (or full when watched fullscreen). */
        float worldGain = 1;
        boolean fullscreen;
        /** The browser's width now (its height is 9/16 of it). */
        int width = WIDTH;
        /** Last volume sent to the page; -1 sends it again. */
        float volume = -1;
        boolean visible = true;
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
            Position last = position != null ? position : new Position(0, 0, true);
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

    /** Where this player left the video on {@code page} last time, in seconds (0 if never or finished). */
    public static double resumeSeconds(String page) {
        return POSITIONS.get(page);
    }

    /** {@code pageUrl} starting {@code seconds} in; our pages read the start from the fragment. */
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
    }

    /** The picture for the screen at {@code origin}, or null when nothing is showing there yet. */
    @Nullable
    public static ResourceLocation texture(BlockPos origin) {
        Playing playing = PLAYING.get(origin.asLong());
        return playing == null || playing.browser.getRenderer().getTextureID() == 0 ? null : playing.texture;
    }

    /** Where the screen at {@code origin} is in its video, or null before its page first reports. */
    @Nullable
    public static Position position(BlockPos origin) {
        Playing playing = PLAYING.get(origin.asLong());
        return playing == null ? null : playing.position;
    }

    /** A page's report, on the client thread (from {@link StateReports}). */
    static void report(CefBrowser browser, double time, double length, boolean paused) {
        for (Playing playing : PLAYING.values()) {
            if (playing.browser == browser) {
                playing.position = new Position(time, length, paused);
            }
        }
    }

    /** The TV's own volume, 0 to 1 (1 when nothing plays). */
    public static float volume(BlockPos origin) {
        Playing playing = PLAYING.get(origin.asLong());
        return playing == null ? 1 : playing.userVolume;
    }

    /** Sets the TV's own volume, 0 to 1, on top of the sound settings and distance. Only for this player. */
    public static void setVolume(BlockPos origin, float volume) {
        Playing playing = PLAYING.get(origin.asLong());
        if (playing != null) {
            playing.userVolume = Math.max(0, Math.min(1, volume));
            sendState(playing, playing.worldGain * playing.userVolume, playing.visible);
        }
    }

    /** While watched fullscreen, the screen plays at full volume whatever the distance, and counts as in sight. */
    public static void setFullscreen(BlockPos origin, boolean fullscreen) {
        Playing playing = PLAYING.get(origin.asLong());
        if (playing != null) {
            playing.fullscreen = fullscreen;
            playing.volume = -1;
            playing.lastSeenMillis = Util.getMillis();
        }
    }

    /** Called while drawing a screen seen from the front. */
    public static void markSeen(BlockPos origin) {
        Playing playing = PLAYING.get(origin.asLong());
        if (playing != null) {
            playing.lastSeenMillis = Util.getMillis();
        }
    }

    /**
     * Starts, keeps in step and stops this client's browsers to match what the server says the screens
     * near the player play, and sets each one's volume and whether it is in sight. Run a few times a second.
     */
    public static void tick(ClientLevel level, Player player) {
        long now = Util.getMillis();
        // Singleplayer paused (Esc menu): the world's clock stands still, so the TVs pause with it.
        boolean gamePaused = Minecraft.getInstance().isPaused();
        if (gamePaused != TvPlayers.gamePaused) {
            TvPlayers.gamePaused = gamePaused;
            PLAYING.values().forEach(playing -> playing.lastPauseSyncMillis = 0);
        }
        LongSet wanted = new LongOpenHashSet();
        if (ready()) {
            List<TvBlockEntity> tvs = new ArrayList<>(TvBlockEntity.CLIENT_PLAYING);
            for (TvBlockEntity tv : tvs) {
                follow(level, player, tv, now, wanted);
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

    private static void follow(ClientLevel level, Player player, TvBlockEntity tv, long now, LongSet wanted) {
        TvSource source = tv.source();
        BlockPos origin = tv.getBlockPos();
        if (source == null || tv.isRemoved() || tv.getLevel() != level
                || origin.distToCenterSqr(player.position()) > MAX_DISTANCE * MAX_DISTANCE) {
            return;
        }
        TvLayout layout = tv.layout(level.getGameTime());
        if (layout.invalid() || !layout.origin().equals(origin)) {
            return;
        }
        String key = source.page();
        Playing playing = PLAYING.get(origin.asLong());
        if (playing == null || !playing.key.equals(key)) {
            String page = key;
            String resumeKey = key;
            Consumer<Position> onStop = NOTHING;
            if (source.service() == TvService.YOUTUBE && !Agreements.youtube()) {
                // YouTube plays only for players who agreed to its terms; the others see a card.
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
                // Jellyfin keeps the watch position itself.
                resumeKey = null;
                onStop = stream.onStop();
            }
            playing = start(origin, playing, withStart(page, source.position(level.getGameTime())), key, resumeKey, onStop);
        }
        wanted.add(origin.asLong());
        int width = playing.fullscreen || layout.cols() >= HD_COLUMNS ? HD_WIDTH : WIDTH;
        if (width != playing.width) {
            playing.width = width;
            playing.browser.resize(width, width == HD_WIDTH ? HD_HEIGHT : HEIGHT);
        }
        syncState(playing, source, level.getGameTime(), now);
        updateVolume(playing, layout, player, now);
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
        playing.volume = -1;
        playing.lastSeenMillis = Util.getMillis();
        playing.lastSeekSyncMillis = Util.getMillis();
        return playing;
    }

    /** Pauses, resumes or jumps the page to match the shared state, gently. */
    private static void syncState(Playing playing, TvSource source, long gameTime, long now) {
        Position position = playing.position;
        if (position == null) {
            return;
        }
        boolean paused = source.paused() || gamePaused;
        // An out-of-sight YouTube page pauses itself; it is brought back in step once seen again.
        if (paused != position.paused() && (playing.visible || paused)
                && now - playing.lastPauseSyncMillis > PAUSE_SYNC_MILLIS) {
            playing.lastPauseSyncMillis = now;
            run(playing, "window.blockcinemaSetPaused && blockcinemaSetPaused(" + paused + ")");
        }
        double expected = source.position(gameTime);
        if (!gamePaused && playing.visible && position.seekable() && expected < position.length()
                && Math.abs(position.time() - expected) > MAX_DRIFT_SECONDS && now - playing.lastSeekSyncMillis > SEEK_SYNC_MILLIS) {
            playing.lastSeekSyncMillis = now;
            run(playing, String.format(Locale.ROOT, "window.blockcinemaSeek && blockcinemaSeek(%.1f)", expected));
        }
    }

    private static void updateVolume(Playing playing, TvLayout layout, Player player, long now) {
        Options options = Minecraft.getInstance().options;
        float gain = options.getSoundSourceVolume(SoundSource.MASTER) * options.getSoundSourceVolume(SoundSource.RECORDS);
        playing.worldGain = gain * (playing.fullscreen ? 1 : TvVolume.falloff(distance(layout, player.getEyePosition())));
        updateState(playing, playing.worldGain * playing.userVolume,
                playing.fullscreen || now - playing.lastSeenMillis < OUT_OF_SIGHT_MILLIS);
    }

    private static double distance(TvLayout layout, Vec3 ear) {
        BlockPos far = layout.cell(layout.cols() - 1, layout.rows() - 1);
        AABB box = new AABB(Vec3.atLowerCornerOf(layout.origin()), Vec3.atLowerCornerOf(far).add(1, 1, 1));
        return TvVolume.distanceToBox(ear.x, ear.y, ear.z, box.minX, box.minY, box.minZ, box.maxX, box.maxY, box.maxZ);
    }

    /**
     * Hands the volume and whether the screen is in sight to our page, which applies them with the
     * service player's own controls.
     */
    private static void updateState(Playing playing, float volume, boolean visible) {
        if (Math.abs(volume - playing.volume) < 0.005f && visible == playing.visible
                && ++playing.checksSinceStateSent < STATE_RESEND_INTERVAL) {
            return;
        }
        sendState(playing, volume, visible);
    }

    private static void sendState(Playing playing, float volume, boolean visible) {
        playing.volume = volume;
        playing.visible = visible;
        playing.checksSinceStateSent = 0;
        run(playing, String.format(Locale.ROOT, "window.blockcinemaState && blockcinemaState(%.3f, %b)", volume, visible));
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
        }
        return client;
    }
}
