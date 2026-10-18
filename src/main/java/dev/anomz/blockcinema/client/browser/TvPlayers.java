package dev.anomz.blockcinema.client.browser;

import org.cef.CefClient;
import org.jetbrains.annotations.Nullable;

import com.cinemamod.mcef.MCEF;
import com.cinemamod.mcef.MCEFBrowser;
import com.cinemamod.mcef.MCEFClient;

import dev.anomz.blockcinema.BlockCinema;
import dev.anomz.blockcinema.block.TvBlockEntity;
import dev.anomz.blockcinema.block.TvLayout;
import it.unimi.dsi.fastutil.longs.Long2ObjectMap;
import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Player;

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

    private record Playing(MCEFBrowser browser, ResourceLocation texture) {
    }

    private TvPlayers() {
    }

    /** Whether Chromium is ready; false while MCEF is still downloading it, or if it failed to start. */
    public static boolean ready() {
        return MCEF.isInitialized();
    }

    /** Plays {@code url} (one of our pages) on the screen at {@code origin}. */
    public static void play(BlockPos origin, String url) {
        Playing playing = PLAYING.get(origin.asLong());
        if (playing != null) {
            playing.browser().loadURL(url);
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
        PLAYING.put(origin.asLong(), new Playing(browser, texture));
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
        return playing == null || playing.browser().getRenderer().getTextureID() == 0 ? null : playing.texture();
    }

    /** Stops screens that were broken, changed shape or are too far away. Run about once a second. */
    public static void tick(ClientLevel level, Player player) {
        if (PLAYING.isEmpty()) {
            return;
        }
        PLAYING.long2ObjectEntrySet().removeIf(entry -> {
            BlockPos origin = BlockPos.of(entry.getLongKey());
            boolean keep = origin.distToCenterSqr(player.position()) < MAX_DISTANCE * MAX_DISTANCE
                    && level.getBlockEntity(origin) instanceof TvBlockEntity tv
                    && isOrigin(tv.layout(level.getGameTime()), origin);
            if (!keep) {
                close(entry.getValue());
            }
            return !keep;
        });
    }

    private static boolean isOrigin(TvLayout layout, BlockPos origin) {
        return !layout.invalid() && layout.origin().equals(origin);
    }

    private static void close(Playing playing) {
        playing.browser().close();
        Minecraft.getInstance().getTextureManager().release(playing.texture());
    }

    /**
     * Our own CEF client, so the navigation guard applies to TV browsers only and never clashes with
     * other mods using MCEF (java-cef keeps one request handler per client).
     */
    private static MCEFClient client() {
        if (client == null) {
            MCEF.getApp().getHandle().registerSchemeHandlerFactory("https", TvPages.HOST, new TvPages());
            CefClient handle = MCEF.getApp().getHandle().createClient();
            handle.addRequestHandler(new NavigationGuard.Requests());
            handle.addLifeSpanHandler(new NavigationGuard.Popups());
            client = new MCEFClient(handle);
        }
        return client;
    }
}
