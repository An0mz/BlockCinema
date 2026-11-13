package dev.anomz.blockcinema.client;

import java.util.List;

import org.jetbrains.annotations.Nullable;

import dev.anomz.blockcinema.BlockCinema;
import dev.anomz.blockcinema.TvClockFollower;
import dev.anomz.blockcinema.TvPageRules;
import dev.anomz.blockcinema.TvQueueEntry;
import dev.anomz.blockcinema.TvService;
import dev.anomz.blockcinema.TvSource;
import dev.anomz.blockcinema.block.TvBlockEntity;
import dev.anomz.blockcinema.client.browser.TvPlayers;
import dev.anomz.blockcinema.network.ChangeTvPayload;
import dev.anomz.blockcinema.network.ControlTvPayload;
import dev.anomz.blockcinema.network.QueueTvPayload;
import dev.anomz.blockcinema.network.RotateTvPayload;
import dev.anomz.blockcinema.network.VideoEndedPayload;
import net.minecraft.Util;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.neoforged.neoforge.network.PacketDistributor;

/** Asks the server to change or control what a screen plays; everybody near it then follows the answer. Client thread only. */
public final class TvRemote {
    private TvRemote() {
    }

    @Nullable
    public static TvSource source(BlockPos origin) {
        Minecraft minecraft = Minecraft.getInstance();
        return minecraft.level != null && minecraft.level.getBlockEntity(origin) instanceof TvBlockEntity tv ? tv.source() : null;
    }

    public static void play(BlockPos origin, TvService service, String page) {
        SavedVideos.get().played(service, page);
        PacketDistributor.sendToServer(ChangeTvPayload.of(origin, service, page, TvPlayers.resumeSeconds(page)));
    }

    public static void playJellyfin(BlockPos origin, String serverId, String itemId, int audio, int subtitle, double startSeconds) {
        PacketDistributor.sendToServer(ChangeTvPayload.of(origin, TvService.JELLYFIN,
                TvPageRules.jellyfinPage(serverId, itemId, audio, subtitle), startSeconds));
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

    public static void skip(BlockPos origin, double seconds) {
        TvPlayers.Position position = TvPlayers.position(origin);
        TvSource source = source(origin);
        Minecraft minecraft = Minecraft.getInstance();
        if (position != null && position.seekable()) {
            seek(origin, position.time() + seconds);
        } else if (position == null && source != null && minecraft.level != null) {
            // Not reported yet; live streams (which report no length) have nowhere to jump to.
            seek(origin, source.position(TvClockFollower.INSTANCE.now(minecraft.level.getGameTime(), Util.getMillis())) + seconds);
        }
    }

    public static void enqueue(BlockPos origin, TvService service, String page, String title) {
        SavedVideos.get().played(service, page);
        PacketDistributor.sendToServer(ChangeTvPayload.queued(origin, service, page, title));
    }

    public static void next(BlockPos origin) {
        PacketDistributor.sendToServer(new QueueTvPayload(origin, QueueTvPayload.NEXT, 0));
    }

    public static void removeQueued(BlockPos origin, int index) {
        PacketDistributor.sendToServer(new QueueTvPayload(origin, QueueTvPayload.REMOVE, index));
    }

    public static void ended(BlockPos origin, String page, int nextInList) {
        PacketDistributor.sendToServer(new VideoEndedPayload(origin, page, nextInList));
    }

    /**
     * Shorts-style scrolling: the next ({@code direction} 1) or previous (-1) video. In a YouTube playlist
     * that moves through the playlist; otherwise next plays the queued video and previous goes back to what
     * played before (or starts over).
     *
     * @return why nothing happens (nothing is queued next), to show the player; null otherwise
     */
    @Nullable
    public static Component skipVideo(BlockPos origin, int direction) {
        TvSource source = source(origin);
        if (source == null) {
            return null;
        }
        String list = source.service() == TvService.YOUTUBE ? TvPageRules.youtubeList(source.page()) : null;
        if (list != null) {
            // Until the page says where the playlist is, it is where the page started.
            int at = TvPlayers.listIndex(origin);
            if (at < 0) {
                at = TvPageRules.youtubeListIndex(source.page());
            }
            PacketDistributor.sendToServer(ChangeTvPayload.of(origin, TvService.YOUTUBE,
                    TvPageRules.youtubeListPage(list, Math.max(0, at + direction)), 0));
        } else if (direction < 0) {
            PacketDistributor.sendToServer(new QueueTvPayload(origin, QueueTvPayload.PREVIOUS, 0));
        } else if (queue(origin).isEmpty()) {
            return Component.translatable("screen." + BlockCinema.MODID + ".nothing_next");
        } else {
            next(origin);
        }
        return null;
    }

    public static List<TvQueueEntry> queue(BlockPos origin) {
        Minecraft minecraft = Minecraft.getInstance();
        return minecraft.level != null && minecraft.level.getBlockEntity(origin) instanceof TvBlockEntity tv ? tv.queue() : List.of();
    }

    public static void rotate(BlockPos origin) {
        PacketDistributor.sendToServer(new RotateTvPayload(origin));
    }

    public static void turnOff(BlockPos origin) {
        control(origin, ControlTvPayload.STOP, 0);
    }

    private static void control(BlockPos origin, int action, double seconds) {
        PacketDistributor.sendToServer(new ControlTvPayload(origin, action, seconds));
    }
}
