package dev.anomz.blockcinema.client;

import dev.anomz.blockcinema.BlockCinema;
import dev.anomz.blockcinema.BlockCinemaRegistries;
import dev.anomz.blockcinema.block.TvBlockEntity;
import dev.anomz.blockcinema.block.TvLayout;
import dev.anomz.blockcinema.client.browser.TvPlayers;
import dev.anomz.blockcinema.client.render.TvRenderer;
import dev.anomz.blockcinema.client.screen.TvScreen;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.EntityRenderersEvent;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;

/**
 * Client entry point. Only constructed on the physical client, so every class under
 * {@code client} is reached from here and never from common code.
 */
@Mod(value = BlockCinema.MODID, dist = Dist.CLIENT)
public final class BlockCinemaClient {
    public BlockCinemaClient(IEventBus modEventBus, ModContainer container) {
        modEventBus.addListener(BlockCinemaClient::onRegisterRenderers);
        NeoForge.EVENT_BUS.addListener(BlockCinemaClient::onRightClickBlock);
        NeoForge.EVENT_BUS.addListener(BlockCinemaClient::onClientTick);
        NeoForge.EVENT_BUS.addListener(BlockCinemaClient::onLoggingOut);
    }

    /** Counts client ticks; game time can't be used, as it stands still while singleplayer is paused. */
    private static int ticks;

    private static void onClientTick(ClientTickEvent.Post event) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.level != null && minecraft.player != null && ++ticks % 5 == 0) {
            TvPlayers.tick(minecraft.level, minecraft.player);
        }
    }

    private static void onLoggingOut(ClientPlayerNetworkEvent.LoggingOut event) {
        TvPlayers.stopAll();
    }

    private static void onRegisterRenderers(EntityRenderersEvent.RegisterRenderers event) {
        event.registerBlockEntityRenderer(BlockCinemaRegistries.TV_BLOCK_ENTITY.get(), TvRenderer::new);
    }

    /** Right-clicking a TV with an empty hand opens its menu. */
    private static void onRightClickBlock(PlayerInteractEvent.RightClickBlock event) {
        Player player = event.getEntity();
        if (!event.getLevel().isClientSide() || event.getHand() != InteractionHand.MAIN_HAND
                || !player.getMainHandItem().isEmpty() || player.isShiftKeyDown()
                || !(event.getLevel().getBlockEntity(event.getPos()) instanceof TvBlockEntity tv)) {
            return;
        }
        event.setCanceled(true);
        event.setCancellationResult(InteractionResult.SUCCESS);
        TvLayout layout = tv.layout(event.getLevel().getGameTime());
        if (layout.invalid()) {
            player.displayClientMessage(Component.translatable("message." + BlockCinema.MODID + ".tv_shape",
                    TvLayout.MAX_COLS, TvLayout.MAX_ROWS), true);
            return;
        }
        Minecraft.getInstance().setScreen(new TvScreen(layout.origin()));
    }
}
