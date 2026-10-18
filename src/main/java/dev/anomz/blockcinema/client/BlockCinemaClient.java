package dev.anomz.blockcinema.client;

import dev.anomz.blockcinema.BlockCinema;
import dev.anomz.blockcinema.BlockCinemaRegistries;
import dev.anomz.blockcinema.client.render.TvRenderer;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.client.event.EntityRenderersEvent;

/**
 * Client entry point. Only constructed on the physical client, so every class under
 * {@code client} is reached from here and never from common code.
 */
@Mod(value = BlockCinema.MODID, dist = Dist.CLIENT)
public final class BlockCinemaClient {
    public BlockCinemaClient(IEventBus modEventBus, ModContainer container) {
        modEventBus.addListener(BlockCinemaClient::onRegisterRenderers);
    }

    private static void onRegisterRenderers(EntityRenderersEvent.RegisterRenderers event) {
        event.registerBlockEntityRenderer(BlockCinemaRegistries.TV_BLOCK_ENTITY.get(), TvRenderer::new);
    }
}
