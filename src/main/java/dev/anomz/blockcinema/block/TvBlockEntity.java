package dev.anomz.blockcinema.block;

import dev.anomz.blockcinema.BlockCinemaRegistries;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Exists so the TV gets a block entity renderer. Nothing is saved or synced yet; the client caches the
 * TV's screen layout here between checks.
 */
public class TvBlockEntity extends BlockEntity {
    private static final long LAYOUT_CHECK_INTERVAL = 10;

    private TvLayout layout;
    private long layoutCheckedAt = Long.MIN_VALUE;

    public TvBlockEntity(BlockPos pos, BlockState state) {
        super(BlockCinemaRegistries.TV_BLOCK_ENTITY.get(), pos, state);
    }

    /** This TV's screen layout, re-checked twice a second so placing or breaking TVs updates it. */
    public TvLayout layout(long gameTime) {
        if (layout == null || gameTime - layoutCheckedAt >= LAYOUT_CHECK_INTERVAL || gameTime < layoutCheckedAt) {
            layout = TvLayout.of(level, worldPosition, getBlockState().getValue(HorizontalDirectionalBlock.FACING));
            layoutCheckedAt = gameTime;
        }
        return layout;
    }
}
