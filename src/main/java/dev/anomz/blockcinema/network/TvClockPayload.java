package dev.anomz.blockcinema.network;

import dev.anomz.blockcinema.BlockCinema;
import dev.anomz.blockcinema.TvClockFollower;
import io.netty.buffer.ByteBuf;
import net.minecraft.Util;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/** Server to client, once a second: where the shared {@link dev.anomz.blockcinema.TvClock} is. */
public record TvClockPayload(long ticks) implements CustomPacketPayload {
    public static final Type<TvClockPayload> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(BlockCinema.MODID, "tv_clock"));
    public static final StreamCodec<ByteBuf, TvClockPayload> STREAM_CODEC =
            ByteBufCodecs.VAR_LONG.map(TvClockPayload::new, TvClockPayload::ticks);

    @Override
    public Type<TvClockPayload> type() {
        return TYPE;
    }

    /** Runs on the client thread. */
    public static void handle(TvClockPayload payload, IPayloadContext context) {
        TvClockFollower.INSTANCE.report(payload.ticks(), Util.getMillis());
    }
}
