package dev.anomz.blockcinema;

import dev.anomz.blockcinema.block.TvBlock;
import dev.anomz.blockcinema.block.TvBlockEntity;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.material.MapColor;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredBlock;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.registries.DeferredRegister;

public final class BlockCinemaRegistries {
    private static final DeferredRegister.Blocks BLOCKS = DeferredRegister.createBlocks(BlockCinema.MODID);
    private static final DeferredRegister.Items ITEMS = DeferredRegister.createItems(BlockCinema.MODID);
    private static final DeferredRegister<BlockEntityType<?>> BLOCK_ENTITY_TYPES =
            DeferredRegister.create(Registries.BLOCK_ENTITY_TYPE, BlockCinema.MODID);

    public static final DeferredBlock<TvBlock> TV = BLOCKS.register("tv", () -> new TvBlock(
            BlockBehaviour.Properties.of().mapColor(MapColor.COLOR_BLACK).strength(1.0f).sound(SoundType.METAL)));
    public static final DeferredItem<BlockItem> TV_ITEM = ITEMS.registerSimpleBlockItem("tv", TV);

    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<TvBlockEntity>> TV_BLOCK_ENTITY =
            BLOCK_ENTITY_TYPES.register("tv",
                    () -> BlockEntityType.Builder.of(TvBlockEntity::new, TV.get()).build(null));

    private BlockCinemaRegistries() {
    }

    static void register(IEventBus modEventBus) {
        BLOCKS.register(modEventBus);
        ITEMS.register(modEventBus);
        BLOCK_ENTITY_TYPES.register(modEventBus);
    }
}
