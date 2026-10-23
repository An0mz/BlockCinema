package dev.anomz.blockcinema.network;

import dev.anomz.blockcinema.BlockCinema;
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

/** Client to server: pause, resume, jump in or turn off what the screen at {@code origin} plays. */
public record ControlTvPayload(BlockPos origin, int action, double seconds) implements CustomPacketPayload {
    public static final int PAUSE = 0;
    public static final int RESUME = 1;
    public static final int SEEK = 2;
    public static final int STOP = 3;

    public static final Type<ControlTvPayload> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(BlockCinema.MODID, "control_tv"));
    public static final StreamCodec<ByteBuf, ControlTvPayload> STREAM_CODEC = StreamCodec.composite(
            BlockPos.STREAM_CODEC, ControlTvPayload::origin,
            ByteBufCodecs.VAR_INT, ControlTvPayload::action,
            ByteBufCodecs.DOUBLE, ControlTvPayload::seconds,
            ControlTvPayload::new);

    @Override
    public Type<ControlTvPayload> type() {
        return TYPE;
    }

    /** Runs on the server thread. */
    public static void handle(ControlTvPayload payload, IPayloadContext context) {
        if (context.player() instanceof ServerPlayer player) {
            apply(player, payload);
        }
    }

    /** What the server does with it from {@code player} (also used by the game tests). */
    public static void apply(ServerPlayer player, ControlTvPayload payload) {
        if (!TvRequests.rateLimitAllows(player)) {
            return;
        }
        TvBlockEntity tv = TvRequests.screen(player, payload.origin());
        TvSource source = tv == null ? null : tv.source();
        if (source == null || !TvRequests.mayControl(player, source)) {
            return;
        }
        long now = player.level().getGameTime();
        switch (payload.action()) {
            case PAUSE -> tv.setSource(source.pausedAt(now));
            case RESUME -> tv.setSource(source.resumedAt(now));
            case SEEK -> tv.setSource(source.seekedTo(now, TvRequests.seconds(payload.seconds())));
            case STOP -> tv.turnOff();
            default -> {
            }
        }
    }
}
