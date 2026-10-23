package dev.anomz.blockcinema;

import org.slf4j.Logger;

import com.mojang.logging.LogUtils;

import dev.anomz.blockcinema.network.ChangeTvPayload;
import dev.anomz.blockcinema.network.ControlTvPayload;
import dev.anomz.blockcinema.network.RotateTvPayload;
import dev.anomz.blockcinema.network.TvRequests;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.config.ModConfig;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
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
    }

    private static void onRegisterPayloads(RegisterPayloadHandlersEvent event) {
        event.registrar("1")
                .playToServer(ChangeTvPayload.TYPE, ChangeTvPayload.STREAM_CODEC, ChangeTvPayload::handle)
                .playToServer(ControlTvPayload.TYPE, ControlTvPayload.STREAM_CODEC, ControlTvPayload::handle)
                .playToServer(RotateTvPayload.TYPE, RotateTvPayload.STREAM_CODEC, RotateTvPayload::handle);
    }

    private static void onLoggedOut(PlayerEvent.PlayerLoggedOutEvent event) {
        TvRequests.forget(event.getEntity());
    }
}
