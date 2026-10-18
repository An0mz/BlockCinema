package dev.anomz.blockcinema.client;

import org.jetbrains.annotations.Nullable;

import dev.anomz.blockcinema.TvPageRules;
import dev.anomz.blockcinema.TvService;
import dev.anomz.blockcinema.TvSource;
import dev.anomz.blockcinema.block.TvBlockEntity;
import dev.anomz.blockcinema.client.browser.TvPlayers;
import dev.anomz.blockcinema.network.ChangeTvPayload;
import dev.anomz.blockcinema.network.ControlTvPayload;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.neoforged.neoforge.network.PacketDistributor;

/**
 * Asks the server to change or control what a screen plays; everybody near it, this player included,
 * then follows the server's answer. Client thread only.
 */
public final class TvRemote {
    private TvRemote() {
    }

    /** What the screen at {@code origin} plays, as the server last said, or null when it is off. */
    @Nullable
    public static TvSource source(BlockPos origin) {
        Minecraft minecraft = Minecraft.getInstance();
        return minecraft.level != null && minecraft.level.getBlockEntity(origin) instanceof TvBlockEntity tv ? tv.source() : null;
    }

    /** Plays one of our pages, from where this player left it last time. */
    public static void play(BlockPos origin, TvService service, String page) {
        PacketDistributor.sendToServer(ChangeTvPayload.of(origin, service, page, TvPlayers.resumeSeconds(page)));
    }

    /**
     * Plays a Jellyfin video. Only which video on which server is shared: everybody logged in to that
     * server streams it with their own login, this player included.
     */
    public static void playJellyfin(BlockPos origin, String serverId, String itemId, double startSeconds) {
        PacketDistributor.sendToServer(ChangeTvPayload.of(origin, TvService.JELLYFIN,
                TvPageRules.jellyfinPage(serverId, itemId), startSeconds));
    }

    public static void togglePause(BlockPos origin) {
        TvSource source = source(origin);
        if (source != null) {
            control(origin, source.paused() ? ControlTvPayload.RESUME : ControlTvPayload.PAUSE, 0);
        }
    }

    public static void seek(BlockPos origin, double seconds) {
        control(origin, ControlTvPayload.SEEK, seconds);
    }

    /** Jumps {@code seconds} forward (or back, when negative) from where the screen is. */
    public static void skip(BlockPos origin, double seconds) {
        TvPlayers.Position position = TvPlayers.position(origin);
        TvSource source = source(origin);
        Minecraft minecraft = Minecraft.getInstance();
        if (position != null && position.seekable()) {
            seek(origin, position.time() + seconds);
        } else if (source != null && minecraft.level != null) {
            seek(origin, source.position(minecraft.level.getGameTime()) + seconds);
        }
    }

    public static void turnOff(BlockPos origin) {
        control(origin, ControlTvPayload.STOP, 0);
    }

    private static void control(BlockPos origin, int action, double seconds) {
        PacketDistributor.sendToServer(new ControlTvPayload(origin, action, seconds));
    }
}
