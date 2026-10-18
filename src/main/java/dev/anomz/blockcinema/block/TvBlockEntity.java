package dev.anomz.blockcinema.block;

import dev.anomz.blockcinema.BlockCinemaRegistries;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

/** Exists so the TV gets a block entity renderer. Will later hold what the TV is set to show. */
public class TvBlockEntity extends BlockEntity {
    public TvBlockEntity(BlockPos pos, BlockState state) {
        super(BlockCinemaRegistries.TV_BLOCK_ENTITY.get(), pos, state);
    }
}
