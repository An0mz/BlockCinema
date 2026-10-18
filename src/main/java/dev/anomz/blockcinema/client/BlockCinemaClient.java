package dev.anomz.blockcinema.client;

import dev.anomz.blockcinema.BlockCinema;
import dev.anomz.blockcinema.BlockCinemaRegistries;
import dev.anomz.blockcinema.block.TvBlockEntity;
import dev.anomz.blockcinema.block.TvLayout;
import dev.anomz.blockcinema.client.browser.TvPlayers;
import dev.anomz.blockcinema.client.render.TvRenderer;
import dev.anomz.blockcinema.client.screen.TvScreen;
import dev.anomz.blockcinema.item.RemoteItem;
import net.minecraft.client.Minecraft;
import net.minecraft.core.GlobalPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.EntityRenderersEvent;
import net.neoforged.neoforge.client.gui.ConfigurationScreen;
import net.neoforged.neoforge.client.gui.IConfigScreenFactory;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;

/**
 * Client entry point. Only constructed on the physical client, so every class under
 * {@code client} is reached from here and never from common code.
 */
@Mod(value = BlockCinema.MODID, dist = Dist.CLIENT)
public final class BlockCinemaClient {
    public BlockCinemaClient(IEventBus modEventBus, ModContainer container) {
        // Mods menu > Block Cinema > Config: the server settings, editable in singleplayer.
        container.registerExtensionPoint(IConfigScreenFactory.class, ConfigurationScreen::new);
        modEventBus.addListener(BlockCinemaClient::onRegisterRenderers);
        NeoForge.EVENT_BUS.addListener(BlockCinemaClient::onRightClickBlock);
        NeoForge.EVENT_BUS.addListener(BlockCinemaClient::onRightClickItem);
        NeoForge.EVENT_BUS.addListener(BlockCinemaClient::onClientTick);
        NeoForge.EVENT_BUS.addListener(BlockCinemaClient::onLoggingOut);
    }

    /** A remote works this close to its TV (the server accepts requests from up to 64 blocks of a screen). */
    private static final double REMOTE_RANGE = 48;

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

    /**
     * Right-clicking a TV with an empty hand or a remote opens its menu. Sneaking with a remote is left
     * to the remote, which links to the TV.
     */
    private static void onRightClickBlock(PlayerInteractEvent.RightClickBlock event) {
        Player player = event.getEntity();
        ItemStack held = player.getMainHandItem();
        if (!event.getLevel().isClientSide() || event.getHand() != InteractionHand.MAIN_HAND
                || !(held.isEmpty() || held.is(BlockCinemaRegistries.REMOTE)) || player.isShiftKeyDown()
                || !(event.getLevel().getBlockEntity(event.getPos()) instanceof TvBlockEntity tv)) {
            return;
        }
        event.setCanceled(true);
        event.setCancellationResult(InteractionResult.SUCCESS);
        openMenu(player, tv);
    }

    /** Right-clicking with a linked remote anywhere in range opens its TV's menu. */
    private static void onRightClickItem(PlayerInteractEvent.RightClickItem event) {
        Player player = event.getEntity();
        ItemStack remote = event.getItemStack();
        if (!event.getLevel().isClientSide() || !remote.is(BlockCinemaRegistries.REMOTE) || player.isShiftKeyDown()) {
            return;
        }
        event.setCanceled(true);
        event.setCancellationResult(InteractionResult.SUCCESS);
        GlobalPos link = RemoteItem.link(remote);
        if (link == null) {
            player.displayClientMessage(Component.translatable("message." + BlockCinema.MODID + ".remote_unlinked"), true);
        } else if (!link.dimension().equals(event.getLevel().dimension())
                || !link.pos().closerToCenterThan(player.position(), REMOTE_RANGE)
                || !event.getLevel().isLoaded(link.pos())) {
            player.displayClientMessage(Component.translatable("message." + BlockCinema.MODID + ".remote_too_far"), true);
        } else if (event.getLevel().getBlockEntity(link.pos()) instanceof TvBlockEntity tv) {
            openMenu(player, tv);
        } else {
            player.displayClientMessage(Component.translatable("message." + BlockCinema.MODID + ".remote_lost"), true);
        }
    }

    private static void openMenu(Player player, TvBlockEntity tv) {
        TvLayout layout = tv.layout(player.level().getGameTime());
        if (layout.invalid()) {
            player.displayClientMessage(Component.translatable("message." + BlockCinema.MODID + ".tv_shape",
                    TvLayout.MAX_COLS, TvLayout.MAX_ROWS), true);
            return;
        }
        Minecraft.getInstance().setScreen(new TvScreen(layout.origin()));
    }
}
