package dev.anomz.blockcinema.network;

import dev.anomz.blockcinema.BlockCinema;
import dev.anomz.blockcinema.block.TvBlockEntity;
import io.netty.buffer.ByteBuf;
import net.minecraft.core.BlockPos;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/** Client to server: skip to the next queued video, or take entry {@code index} out of the queue. */
public record QueueTvPayload(BlockPos origin, int action, int index) implements CustomPacketPayload {
    public static final int NEXT = 0;
    public static final int REMOVE = 1;

    public static final Type<QueueTvPayload> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(BlockCinema.MODID, "queue_tv"));
    public static final StreamCodec<ByteBuf, QueueTvPayload> STREAM_CODEC = StreamCodec.composite(
            BlockPos.STREAM_CODEC, QueueTvPayload::origin,
            ByteBufCodecs.VAR_INT, QueueTvPayload::action,
            ByteBufCodecs.VAR_INT, QueueTvPayload::index,
            QueueTvPayload::new);

    @Override
    public Type<QueueTvPayload> type() {
        return TYPE;
    }

    /** Runs on the server thread. */
    public static void handle(QueueTvPayload payload, IPayloadContext context) {
        if (context.player() instanceof ServerPlayer player) {
            apply(player, payload);
        }
    }

    /** What the server does with it from {@code player} (also used by the game tests). */
    public static void apply(ServerPlayer player, QueueTvPayload payload) {
        if (!TvRequests.rateLimitAllows(player)) {
            return;
        }
        TvBlockEntity tv = TvRequests.screen(player, payload.origin());
        if (tv == null || !TvRequests.mayControl(player, tv.source())) {
            return;
        }
        switch (payload.action()) {
            case NEXT -> tv.playNext(player.level().getGameTime());
            case REMOVE -> tv.removeQueued(payload.index());
            default -> {
            }
        }
    }
}
