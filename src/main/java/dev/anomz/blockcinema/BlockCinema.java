package dev.anomz.blockcinema;

import org.slf4j.Logger;

import com.mojang.logging.LogUtils;

import dev.anomz.blockcinema.network.ChangeTvPayload;
import dev.anomz.blockcinema.network.ControlTvPayload;
import dev.anomz.blockcinema.network.QueueTvPayload;
import dev.anomz.blockcinema.network.RotateTvPayload;
import dev.anomz.blockcinema.network.TvClockPayload;
import dev.anomz.blockcinema.network.TvRequests;
import dev.anomz.blockcinema.network.VideoEndedPayload;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.config.ModConfig;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;

@Mod(BlockCinema.MODID)
public final class BlockCinema {
    public static final String MODID = "blockcinema";
    public static final Logger LOGGER = LogUtils.getLogger();

    public BlockCinema(IEventBus modEventBus, ModContainer container) {
        container.registerConfig(ModConfig.Type.SERVER, TvServerConfig.SPEC);
        BlockCinemaRegistries.register(modEventBus);
        modEventBus.addListener(BlockCinema::onRegisterPayloads);
        NeoForge.EVENT_BUS.addListener(BlockCinema::onLoggedOut);
        NeoForge.EVENT_BUS.addListener(BlockCinema::onLoggedIn);
        NeoForge.EVENT_BUS.addListener(BlockCinema::onServerTick);
        NeoForge.EVENT_BUS.addListener(BlockCinema::onServerStopped);
    }

    private static void onRegisterPayloads(RegisterPayloadHandlersEvent event) {
        event.registrar("2")
                .playToClient(TvClockPayload.TYPE, TvClockPayload.STREAM_CODEC, TvClockPayload::handle)
                .playToServer(ChangeTvPayload.TYPE, ChangeTvPayload.STREAM_CODEC, ChangeTvPayload::handle)
                .playToServer(ControlTvPayload.TYPE, ControlTvPayload.STREAM_CODEC, ControlTvPayload::handle)
                .playToServer(RotateTvPayload.TYPE, RotateTvPayload.STREAM_CODEC, RotateTvPayload::handle)
                .playToServer(QueueTvPayload.TYPE, QueueTvPayload.STREAM_CODEC, QueueTvPayload::handle)
                .playToServer(VideoEndedPayload.TYPE, VideoEndedPayload.STREAM_CODEC, VideoEndedPayload::handle);
    }

    private static void onLoggedOut(PlayerEvent.PlayerLoggedOutEvent event) {
        TvRequests.forget(event.getEntity());
    }

    /** A new arrival knows the clock at once, so the first video it starts is at the right time. */
    private static void onLoggedIn(PlayerEvent.PlayerLoggedInEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            TvClock.sendTo(player);
        }
    }

    private static void onServerTick(ServerTickEvent.Post event) {
        TvClock.onServerTick(event.getServer());
    }

    private static void onServerStopped(ServerStoppedEvent event) {
        TvClock.onServerStopped();
    }
}
