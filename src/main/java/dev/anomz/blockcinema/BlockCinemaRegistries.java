package dev.anomz.blockcinema;

import dev.anomz.blockcinema.block.DiagonalTvBlock;
import dev.anomz.blockcinema.block.TvBlock;
import dev.anomz.blockcinema.block.TvBlockEntity;
import dev.anomz.blockcinema.item.RemoteItem;
import net.minecraft.core.GlobalPos;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
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
    private static final DeferredRegister<CreativeModeTab> CREATIVE_TABS =
            DeferredRegister.create(Registries.CREATIVE_MODE_TAB, BlockCinema.MODID);
    private static final DeferredRegister.DataComponents DATA_COMPONENTS =
            DeferredRegister.createDataComponents(Registries.DATA_COMPONENT_TYPE, BlockCinema.MODID);

    public static final DeferredBlock<TvBlock> TV = BLOCKS.register("tv", () -> new TvBlock(
            BlockBehaviour.Properties.of().mapColor(MapColor.COLOR_BLACK).strength(1.0f).sound(SoundType.METAL)));
    public static final DeferredItem<BlockItem> TV_ITEM = ITEMS.registerSimpleBlockItem("tv", TV);
    public static final DeferredBlock<DiagonalTvBlock> DIAGONAL_TV = BLOCKS.register("diagonal_tv", () -> new DiagonalTvBlock(
            BlockBehaviour.Properties.of().mapColor(MapColor.COLOR_BLACK).strength(1.0f).sound(SoundType.METAL).noOcclusion()));
    public static final DeferredItem<BlockItem> DIAGONAL_TV_ITEM = ITEMS.registerSimpleBlockItem("diagonal_tv", DIAGONAL_TV);
    public static final DeferredItem<RemoteItem> REMOTE = ITEMS.register("remote",
            () -> new RemoteItem(new Item.Properties().stacksTo(1)));

    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<TvBlockEntity>> TV_BLOCK_ENTITY =
            BLOCK_ENTITY_TYPES.register("tv",
                    () -> BlockEntityType.Builder.of(TvBlockEntity::new, TV.get(), DIAGONAL_TV.get()).build(null));

    /** Block Cinema's own creative tab, with everything the mod adds. */
    public static final DeferredHolder<CreativeModeTab, CreativeModeTab> TAB = CREATIVE_TABS.register("block_cinema",
            () -> CreativeModeTab.builder()
                    .title(Component.translatable("itemGroup." + BlockCinema.MODID))
                    .icon(() -> new ItemStack(TV_ITEM.get()))
                    .displayItems((parameters, output) -> {
                        output.accept(TV_ITEM);
                        output.accept(DIAGONAL_TV_ITEM);
                        output.accept(REMOTE);
                    })
                    .build());

    /** The TV a remote opens. */
    public static final DeferredHolder<DataComponentType<?>, DataComponentType<GlobalPos>> REMOTE_LINK =
            DATA_COMPONENTS.registerComponentType("remote_link",
                    builder -> builder.persistent(GlobalPos.CODEC).networkSynchronized(GlobalPos.STREAM_CODEC));

    private BlockCinemaRegistries() {
    }

    static void register(IEventBus modEventBus) {
        BLOCKS.register(modEventBus);
        ITEMS.register(modEventBus);
        BLOCK_ENTITY_TYPES.register(modEventBus);
        DATA_COMPONENTS.register(modEventBus);
        CREATIVE_TABS.register(modEventBus);
    }
}
