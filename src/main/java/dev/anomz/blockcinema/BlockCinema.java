package dev.anomz.blockcinema;

import org.slf4j.Logger;

import com.mojang.logging.LogUtils;

import net.minecraft.world.item.CreativeModeTabs;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.event.BuildCreativeModeTabContentsEvent;

@Mod(BlockCinema.MODID)
public final class BlockCinema {
    public static final String MODID = "blockcinema";
    public static final Logger LOGGER = LogUtils.getLogger();

    public BlockCinema(IEventBus modEventBus, ModContainer container) {
        BlockCinemaRegistries.register(modEventBus);
        modEventBus.addListener(BlockCinema::onBuildCreativeTabs);
    }

    private static void onBuildCreativeTabs(BuildCreativeModeTabContentsEvent event) {
        if (event.getTabKey() == CreativeModeTabs.FUNCTIONAL_BLOCKS) {
            event.accept(BlockCinemaRegistries.TV_ITEM);
        }
    }
}
