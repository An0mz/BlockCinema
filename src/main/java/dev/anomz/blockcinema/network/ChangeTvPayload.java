package dev.anomz.blockcinema.network;

import dev.anomz.blockcinema.BlockCinema;
import dev.anomz.blockcinema.TvPageRules;
import dev.anomz.blockcinema.TvQueueEntry;
import dev.anomz.blockcinema.TvServerConfig;
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
 * Client to server: "play this on the screen at {@code origin}, starting {@code startSeconds} in".
 * {@code page} is one of our page URLs, or for Jellyfin which video on which server (see {@code TvPageRules}).
 * With {@code queue}, it waits in the screen's queue instead (it plays now if nothing does); {@code title}
 * is what the queue shows for it.
 */
public record ChangeTvPayload(BlockPos origin, int service, String page, double startSeconds, boolean queue, String title)
        implements CustomPacketPayload {
    public static final Type<ChangeTvPayload> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(BlockCinema.MODID, "change_tv"));
    public static final StreamCodec<ByteBuf, ChangeTvPayload> STREAM_CODEC = StreamCodec.composite(
            BlockPos.STREAM_CODEC, ChangeTvPayload::origin,
            ByteBufCodecs.VAR_INT, ChangeTvPayload::service,
            ByteBufCodecs.stringUtf8(TvPageRules.MAX_PAGE_LENGTH), ChangeTvPayload::page,
            ByteBufCodecs.DOUBLE, ChangeTvPayload::startSeconds,
            ByteBufCodecs.BOOL, ChangeTvPayload::queue,
            ByteBufCodecs.stringUtf8(TvQueueEntry.MAX_TITLE_LENGTH * 4), ChangeTvPayload::title,
            ChangeTvPayload::new);

    public static ChangeTvPayload of(BlockPos origin, TvService service, String page, double startSeconds) {
        return new ChangeTvPayload(origin, service.ordinal(), page, startSeconds, false, "");
    }

    public static ChangeTvPayload queued(BlockPos origin, TvService service, String page, String title) {
        return new ChangeTvPayload(origin, service.ordinal(), page, 0, true, title);
    }

    @Override
    public Type<ChangeTvPayload> type() {
        return TYPE;
    }

    /** Runs on the server thread. */
    public static void handle(ChangeTvPayload payload, IPayloadContext context) {
        if (!(context.player() instanceof ServerPlayer player) || !TvRequests.rateLimitAllows(player)
                || payload.service() < 0 || payload.service() >= TvService.values().length) {
            return;
        }
        TvService service = TvService.values()[payload.service()];
        if (!TvServerConfig.enabled(service) || !pageAllowed(service, payload.page())) {
            return;
        }
        TvBlockEntity tv = TvRequests.screen(player, payload.origin());
        if (tv == null || !TvRequests.mayControl(player, tv.source())) {
            return;
        }
        if (payload.queue() && tv.source() != null) {
            tv.enqueue(new TvQueueEntry(service, payload.page(), TvQueueEntry.cleanTitle(payload.title()), player.getUUID(),
                    player.getGameProfile().getName()));
            return;
        }
        tv.setSource(new TvSource(service, payload.page(), player.getUUID(),
                player.getGameProfile().getName(), player.level().getGameTime(), TvRequests.seconds(payload.startSeconds()),
                false));
    }

    private static boolean pageAllowed(TvService service, String page) {
        if (!TvPageRules.valid(service, page)) {
            return false;
        }
        return service != TvService.DIRECT_LINK || TvServerConfig.linkHostAllowed(TvPageRules.linkHost(page));
    }
}
