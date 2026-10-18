package dev.anomz.blockcinema.block;

import com.mojang.serialization.MapCodec;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Vec3i;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * A TV whose thin screen runs corner to corner across the block, at 45 degrees: a line of them like
 * {@code \} or {@code /} (and stacked up) is one screen. {@code facing} turned 45 degrees clockwise is
 * where the screen faces (see {@link ScreenFrame#diagonal}).
 */
public class DiagonalTvBlock extends BaseEntityBlock {
    public static final MapCodec<DiagonalTvBlock> CODEC = simpleCodec(DiagonalTvBlock::new);
    /** Steps along the diagonal, roughly following the thin screen, for clicking and walking into. */
    private static final VoxelShape NORTH_WEST_TO_SOUTH_EAST = Shapes.or(
            box(0, 0, 0, 5, 16, 5), box(4, 0, 4, 9, 16, 9), box(8, 0, 8, 13, 16, 13), box(12, 0, 12, 16, 16, 16));
    private static final VoxelShape NORTH_EAST_TO_SOUTH_WEST = Shapes.or(
            box(11, 0, 0, 16, 16, 5), box(7, 0, 4, 12, 16, 9), box(3, 0, 8, 8, 16, 13), box(0, 0, 12, 4, 16, 16));

    public DiagonalTvBlock(Properties properties) {
        super(properties);
        registerDefaultState(stateDefinition.any().setValue(HorizontalDirectionalBlock.FACING, Direction.NORTH)
                .setValue(TvBlock.UP, false).setValue(TvBlock.DOWN, false)
                .setValue(TvBlock.LEFT, false).setValue(TvBlock.RIGHT, false));
    }

    @Override
    protected MapCodec<? extends DiagonalTvBlock> codec() {
        return CODEC;
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(HorizontalDirectionalBlock.FACING, TvBlock.UP, TvBlock.DOWN, TvBlock.LEFT, TvBlock.RIGHT);
    }

    /** Faces whichever of the four diagonals points most toward the player. */
    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        Vec3 look = context.getPlayer() != null ? context.getPlayer().getLookAngle() : new Vec3(0, 0, 1);
        boolean east = look.x < 0;
        boolean south = look.z < 0;
        // The screen faces facing + its clockwise neighbor: NE for north, SE for east, SW for south, NW for west.
        Direction facing = east ? (south ? Direction.EAST : Direction.NORTH) : (south ? Direction.SOUTH : Direction.WEST);
        BlockState state = defaultBlockState().setValue(HorizontalDirectionalBlock.FACING, facing);
        return TvBlock.connect(state, context.getLevel(), context.getClickedPos());
    }

    @Override
    protected BlockState updateShape(BlockState state, Direction direction, BlockState neighborState,
            LevelAccessor level, BlockPos pos, BlockPos neighborPos) {
        return TvBlock.connect(state, level, pos);
    }

    /** The TVs beside this one along the screen are diagonal neighbors, which Minecraft doesn't update. */
    @Override
    protected void onPlace(BlockState state, Level level, BlockPos pos, BlockState oldState, boolean movedByPiston) {
        super.onPlace(state, level, pos, oldState, movedByPiston);
        refreshSides(level, pos, state);
    }

    @Override
    protected void onRemove(BlockState state, Level level, BlockPos pos, BlockState newState, boolean movedByPiston) {
        super.onRemove(state, level, pos, newState, movedByPiston);
        if (!state.is(newState.getBlock())) {
            refreshSides(level, pos, state);
        }
    }

    private void refreshSides(Level level, BlockPos pos, BlockState state) {
        ScreenFrame frame = ScreenFrame.of(state);
        if (level.isClientSide || frame == null) {
            return;
        }
        Vec3i right = frame.right();
        for (BlockPos side : new BlockPos[] { pos.offset(right), pos.subtract(right) }) {
            BlockState sideState = level.getBlockState(side);
            if (sideState.is(this)) {
                BlockState updated = TvBlock.connect(sideState, level, side);
                if (updated != sideState) {
                    level.setBlock(side, updated, Block.UPDATE_ALL);
                }
            }
        }
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        Direction facing = state.getValue(HorizontalDirectionalBlock.FACING);
        // North and south lie from the north-west corner to the south-east one; east and west the other way.
        return facing.getAxis() == Direction.Axis.Z ? NORTH_WEST_TO_SOUTH_EAST : NORTH_EAST_TO_SOUTH_WEST;
    }

    @Override
    protected BlockState rotate(BlockState state, Rotation rotation) {
        return state.setValue(HorizontalDirectionalBlock.FACING, rotation.rotate(state.getValue(HorizontalDirectionalBlock.FACING)));
    }

    @Override
    protected BlockState mirror(BlockState state, Mirror mirror) {
        return state.rotate(mirror.getRotation(state.getValue(HorizontalDirectionalBlock.FACING)));
    }

    @Override
    protected RenderShape getRenderShape(BlockState state) {
        return RenderShape.MODEL;
    }

    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new TvBlockEntity(pos, state);
    }
}
