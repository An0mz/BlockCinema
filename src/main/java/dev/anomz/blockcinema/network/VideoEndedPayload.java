package dev.anomz.blockcinema.network;

import dev.anomz.blockcinema.BlockCinema;
import dev.anomz.blockcinema.TvPageRules;
import dev.anomz.blockcinema.TvService;
import dev.anomz.blockcinema.TvSource;
import dev.anomz.blockcinema.block.TvBlockEntity;
import io.netty.buffer.ByteBuf;
import net.minecraft.core.BlockPos;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/**
 * Client to server, sent by the game itself: the video {@code page} on the screen at {@code origin} ended
 * here, so the playlist's video {@code nextInList} (or with -1, the next queued one) can start. Every viewer sends it; the first one for the video playing now
 * counts, and the others no longer match.
 */
public record VideoEndedPayload(BlockPos origin, String page, int nextInList) implements CustomPacketPayload {
    /** An "ended" this soon after a video started is ignored (a page that failed at once, or a hacked client). */
    private static final double MIN_SECONDS = 5;

    public static final Type<VideoEndedPayload> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(BlockCinema.MODID, "video_ended"));
    public static final StreamCodec<ByteBuf, VideoEndedPayload> STREAM_CODEC = StreamCodec.composite(
            BlockPos.STREAM_CODEC, VideoEndedPayload::origin,
            ByteBufCodecs.stringUtf8(TvPageRules.MAX_PAGE_LENGTH), VideoEndedPayload::page,
            ByteBufCodecs.VAR_INT, VideoEndedPayload::nextInList,
            VideoEndedPayload::new);

    /** A video (or a playlist's last one) ended: the queue goes on. */
    public VideoEndedPayload(BlockPos origin, String page) {
        this(origin, page, -1);
    }

    @Override
    public Type<VideoEndedPayload> type() {
        return TYPE;
    }

    /** Runs on the server thread. */
    public static void handle(VideoEndedPayload payload, IPayloadContext context) {
        if (context.player() instanceof ServerPlayer player) {
            apply(player, payload);
        }
    }

    /** What the server does with it from {@code player} (also used by the game tests). */
    public static void apply(ServerPlayer player, VideoEndedPayload payload) {
        if (!TvRequests.rateLimitAllows(player)) {
            return;
        }
        TvBlockEntity tv = TvRequests.screen(player, payload.origin());
        TvSource source = tv == null ? null : tv.source();
        long now = player.level().getGameTime();
        if (source == null || !source.page().equals(payload.page()) || source.position(now) < MIN_SECONDS) {
            return;
        }
        String list = source.service() == TvService.YOUTUBE ? TvPageRules.youtubeList(source.page()) : null;
        if (list != null && payload.nextInList() >= 0 && payload.nextInList() <= TvPageRules.MAX_LIST_INDEX) {
            // The playlist's next video, for everyone together.
            tv.setSource(new TvSource(source.service(), TvPageRules.youtubeListPage(list, payload.nextInList()), source.owner(),
                    source.ownerName(), now, 0, false));
        } else {
            tv.playNext(now);
        }
    }
}
