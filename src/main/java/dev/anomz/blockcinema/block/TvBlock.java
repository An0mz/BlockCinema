package dev.anomz.blockcinema.block;

import com.mojang.serialization.MapCodec;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
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
import net.minecraft.world.level.block.state.properties.BooleanProperty;

/**
 * A TV facing the player who placed it. TVs facing the same way in a filled rectangle act as one big
 * screen (see {@link TvLayout}). The connection properties hide the bezel on sides where another TV
 * continues the screen, so walls of TVs look seamless. Left and right are as seen from the front.
 * The picture is drawn on the client by the block entity renderer.
 */
public class TvBlock extends BaseEntityBlock {
    public static final MapCodec<TvBlock> CODEC = simpleCodec(TvBlock::new);
    public static final BooleanProperty UP = BooleanProperty.create("up");
    public static final BooleanProperty DOWN = BooleanProperty.create("down");
    public static final BooleanProperty LEFT = BooleanProperty.create("left");
    public static final BooleanProperty RIGHT = BooleanProperty.create("right");

    public TvBlock(Properties properties) {
        super(properties);
        registerDefaultState(stateDefinition.any().setValue(HorizontalDirectionalBlock.FACING, Direction.NORTH)
                .setValue(UP, false).setValue(DOWN, false).setValue(LEFT, false).setValue(RIGHT, false));
    }

    @Override
    protected MapCodec<TvBlock> codec() {
        return CODEC;
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(HorizontalDirectionalBlock.FACING, UP, DOWN, LEFT, RIGHT);
    }

    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        BlockState state = defaultBlockState()
                .setValue(HorizontalDirectionalBlock.FACING, context.getHorizontalDirection().getOpposite());
        return connect(state, context.getLevel(), context.getClickedPos());
    }

    @Override
    protected BlockState updateShape(BlockState state, Direction direction, BlockState neighborState,
            LevelAccessor level, BlockPos pos, BlockPos neighborPos) {
        return connect(state, level, pos);
    }

    private static BlockState connect(BlockState state, BlockGetter level, BlockPos pos) {
        Direction facing = state.getValue(HorizontalDirectionalBlock.FACING);
        // Seen from the front, the viewer's right is the TV's counter-clockwise side (as in TvLayout).
        Direction right = facing.getCounterClockWise();
        return state
                .setValue(UP, TvLayout.isTv(level.getBlockState(pos.above()), facing))
                .setValue(DOWN, TvLayout.isTv(level.getBlockState(pos.below()), facing))
                .setValue(LEFT, TvLayout.isTv(level.getBlockState(pos.relative(right.getOpposite())), facing))
                .setValue(RIGHT, TvLayout.isTv(level.getBlockState(pos.relative(right)), facing));
    }

    @Override
    protected BlockState rotate(BlockState state, Rotation rotation) {
        return state.setValue(HorizontalDirectionalBlock.FACING,
                rotation.rotate(state.getValue(HorizontalDirectionalBlock.FACING)));
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
