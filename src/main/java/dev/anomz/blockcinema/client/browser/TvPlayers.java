package dev.anomz.blockcinema.client.browser;

import java.util.Locale;

import java.util.function.Consumer;

import org.cef.CefClient;
import org.cef.browser.CefBrowser;
import org.jetbrains.annotations.Nullable;

import com.cinemamod.mcef.MCEF;
import com.cinemamod.mcef.MCEFBrowser;
import com.cinemamod.mcef.MCEFClient;

import dev.anomz.blockcinema.BlockCinema;
import dev.anomz.blockcinema.block.TvBlockEntity;
import dev.anomz.blockcinema.block.TvLayout;
import it.unimi.dsi.fastutil.longs.Long2ObjectMap;
import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
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
 * The browsers playing on TV screens, one per screen, keyed by the screen's origin (its bottom-left TV
 * seen from the front). Only on this client for now; client thread only.
 */
public final class TvPlayers {
    public static final int WIDTH = 1280;
    public static final int HEIGHT = 720;
    /** A screen this far from the player stops. */
    private static final double MAX_DISTANCE = 96;

    private static final Long2ObjectMap<Playing> PLAYING = new Long2ObjectOpenHashMap<>();
    private static MCEFClient client;
    private static int nextTextureId;

    /** State updates sent at least this often (in checks), in case the page wasn't ready for the last one. */
    private static final int STATE_RESEND_INTERVAL = 8;
    /**
     * A screen not seen from the front for this long counts as out of sight. YouTube's terms don't allow
     * playing in a player the user can't see, so our YouTube page pauses until it is seen again.
     */
    private static final long OUT_OF_SIGHT_MILLIS = 3000;

    private static final WatchPositions POSITIONS =
            new WatchPositions(FMLPaths.CONFIGDIR.get().resolve(BlockCinema.MODID).resolve("positions.json"));

    /** Where a video is: seconds in, its length (0 when live or not known yet) and whether it is paused. */
    public record Position(double time, double length, boolean paused) {
        public boolean seekable() {
            return length > 0;
        }
    }

    private static final class Playing {
        final MCEFBrowser browser;
        final ResourceLocation texture;
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
        /** Last volume sent to the page; -1 sends it again. */
        float volume = -1;
        boolean visible = true;
        int checksSinceStateSent;
        /** Written by the renderer, read by the tick; both on the client thread. */
        long lastSeenMillis = Util.getMillis();

        Playing(MCEFBrowser browser, ResourceLocation texture, @Nullable String resumeKey, Consumer<Position> onStop) {
            this.browser = browser;
            this.texture = texture;
            this.resumeKey = resumeKey;
            this.onStop = onStop;
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

    private static final Consumer<Position> NOTHING = position -> {
    };

    /**
     * Plays {@code url} (one of our pages) on the screen at {@code origin}, resuming where this video
     * was left last time.
     */
    public static void play(BlockPos origin, String url) {
        play(origin, url, true, NOTHING);
    }

    /**
     * Like {@link #play(BlockPos, String)}. {@code resumeHere} false leaves resuming to the service (the
     * URL then has its own start); {@code onStop} gets the last position once it stops playing.
     */
    public static void play(BlockPos origin, String pageUrl, boolean resumeHere, Consumer<Position> onStop) {
        String resumeKey = resumeHere ? withoutFragment(pageUrl) : null;
        String url = resumeKey != null ? withStart(pageUrl, POSITIONS.get(resumeKey)) : pageUrl;
        Playing playing = PLAYING.get(origin.asLong());
        if (playing != null) {
            playing.stopped();
            playing.resumeKey = resumeKey;
            playing.onStop = onStop;
            playing.position = null;
            playing.browser.loadURL(url);
            playing.volume = -1;
            playing.lastSeenMillis = Util.getMillis();
            return;
        }
        MCEFBrowser browser = new MCEFBrowser(client(), url, false);
        // A TV in the world must never change the player's mouse cursor (MCEF's default does).
        browser.setCursorChangeListener(cursor -> {
        });
        browser.setCloseAllowed();
        browser.createImmediately();
        browser.resize(WIDTH, HEIGHT);
        ResourceLocation texture = ResourceLocation.fromNamespaceAndPath(BlockCinema.MODID, "tv_browser_" + nextTextureId++);
        Minecraft.getInstance().getTextureManager().register(texture, new BrowserTexture(browser));
        PLAYING.put(origin.asLong(), new Playing(browser, texture, resumeKey, onStop));
    }

    /** {@code pageUrl} starting {@code seconds} in; our pages read the start from the fragment. */
    public static String withStart(String pageUrl, double seconds) {
        if (seconds < 1) {
            return pageUrl;
        }
        return pageUrl + (pageUrl.contains("#") ? "&" : "#") + "start=" + (long) seconds;
    }

    private static String withoutFragment(String url) {
        int hash = url.indexOf('#');
        return hash < 0 ? url : url.substring(0, hash);
    }

    public static boolean isPlaying(BlockPos origin) {
        return PLAYING.containsKey(origin.asLong());
    }

    public static void stop(BlockPos origin) {
        Playing playing = PLAYING.remove(origin.asLong());
        if (playing != null) {
            close(playing);
        }
    }

    public static void stopAll() {
        PLAYING.values().forEach(TvPlayers::close);
        PLAYING.clear();
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

    /** Jumps to {@code seconds} into the video. */
    public static void seek(BlockPos origin, double seconds) {
        Playing playing = PLAYING.get(origin.asLong());
        if (playing == null || playing.position == null || !playing.position.seekable()) {
            return;
        }
        double target = Math.max(0, Math.min(seconds, playing.position.length() - 1));
        playing.position = new Position(target, playing.position.length(), playing.position.paused());
        playing.browser.executeJavaScript(String.format(Locale.ROOT, "window.blockcinemaSeek && blockcinemaSeek(%.1f)", target),
                playing.browser.getURL(), 0);
    }

    /** A page's report, on the client thread (from {@link StateReports}). */
    static void report(CefBrowser browser, double time, double length, boolean paused) {
        for (Playing playing : PLAYING.values()) {
            if (playing.browser == browser) {
                playing.position = new Position(time, length, paused);
            }
        }
    }

    /** Pauses or resumes what the screen at {@code origin} plays, with the service player's own controls. */
    public static void togglePause(BlockPos origin) {
        Playing playing = PLAYING.get(origin.asLong());
        if (playing != null) {
            playing.browser.executeJavaScript("window.blockcinemaToggle && blockcinemaToggle()",
                    playing.browser.getURL(), 0);
        }
    }

    /** The TV's own volume, 0 to 1 (1 when nothing plays). */
    public static float volume(BlockPos origin) {
        Playing playing = PLAYING.get(origin.asLong());
        return playing == null ? 1 : playing.userVolume;
    }

    /** Sets the TV's own volume, 0 to 1, on top of the sound settings and distance. */
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
     * Stops screens that were broken, changed shape or are too far away, sets each screen's volume
     * from how far the player is from it, and tells it whether it is in sight. Run a few times a second.
     */
    public static void tick(ClientLevel level, Player player) {
        if (PLAYING.isEmpty()) {
            return;
        }
        Options options = Minecraft.getInstance().options;
        float gain = options.getSoundSourceVolume(SoundSource.MASTER) * options.getSoundSourceVolume(SoundSource.RECORDS);
        Vec3 ear = player.getEyePosition();
        long now = Util.getMillis();
        PLAYING.long2ObjectEntrySet().removeIf(entry -> {
            BlockPos origin = BlockPos.of(entry.getLongKey());
            TvLayout layout = origin.distToCenterSqr(player.position()) < MAX_DISTANCE * MAX_DISTANCE
                    && level.getBlockEntity(origin) instanceof TvBlockEntity tv ? tv.layout(level.getGameTime()) : null;
            if (layout == null || !isOrigin(layout, origin)) {
                close(entry.getValue());
                return true;
            }
            Playing playing = entry.getValue();
            playing.worldGain = gain * (playing.fullscreen ? 1 : TvVolume.falloff(distance(layout, ear)));
            updateState(playing, playing.worldGain * playing.userVolume,
                    playing.fullscreen || now - playing.lastSeenMillis < OUT_OF_SIGHT_MILLIS);
            return false;
        });
    }

    private static double distance(TvLayout layout, Vec3 ear) {
        BlockPos far = layout.origin().relative(layout.right(), layout.cols() - 1).above(layout.rows() - 1);
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
        playing.browser.executeJavaScript(
                String.format(Locale.ROOT, "window.blockcinemaState && blockcinemaState(%.3f, %b)", volume, visible),
                playing.browser.getURL(), 0);
    }

    private static boolean isOrigin(TvLayout layout, BlockPos origin) {
        return !layout.invalid() && layout.origin().equals(origin);
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
