package dev.anomz.blockcinema.block;

import com.mojang.serialization.MapCodec;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.FrontAndTop;
import net.minecraft.core.Vec3i;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.BooleanProperty;

/**
 * A TV on a wall, floor or ceiling. Its orientation is which way the screen faces and where the top of
 * the picture is (for walls always up; on floors and ceilings the way the placing player faced). TVs
 * lying the same way in a filled rectangle act as one big screen (see {@link TvLayout}). The connection
 * properties hide the bezel on sides where another TV continues the screen, so walls of TVs look
 * seamless; up, down, left and right are as seen on the picture. The picture is drawn on the client by
 * the block entity renderer.
 */
public class TvBlock extends BaseEntityBlock {
    public static final MapCodec<TvBlock> CODEC = simpleCodec(TvBlock::new);
    public static final BooleanProperty UP = BooleanProperty.create("up");
    public static final BooleanProperty DOWN = BooleanProperty.create("down");
    public static final BooleanProperty LEFT = BooleanProperty.create("left");
    public static final BooleanProperty RIGHT = BooleanProperty.create("right");

    public TvBlock(Properties properties) {
        super(properties);
        registerDefaultState(stateDefinition.any().setValue(BlockStateProperties.ORIENTATION, FrontAndTop.NORTH_UP)
                .setValue(UP, false).setValue(DOWN, false).setValue(LEFT, false).setValue(RIGHT, false));
    }

    @Override
    protected MapCodec<? extends TvBlock> codec() {
        return CODEC;
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(BlockStateProperties.ORIENTATION, UP, DOWN, LEFT, RIGHT);
    }

    /**
     * Facing the player. Looking steeply down makes a floor screen and up a ceiling screen, with the top
     * of the picture the way the player faces; next to another floor or ceiling TV it lies the same way,
     * so they join.
     */
    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        Direction front = context.getNearestLookingDirection().getOpposite();
        Direction top = Direction.UP;
        if (front.getAxis() == Direction.Axis.Y) {
            top = context.getHorizontalDirection();
            for (Direction side : Direction.Plane.HORIZONTAL) {
                BlockState neighbor = context.getLevel().getBlockState(context.getClickedPos().relative(side));
                if (neighbor.is(this) && neighbor.getValue(BlockStateProperties.ORIENTATION).front() == front) {
                    top = neighbor.getValue(BlockStateProperties.ORIENTATION).top();
                    break;
                }
            }
        }
        BlockState state = defaultBlockState().setValue(BlockStateProperties.ORIENTATION, FrontAndTop.fromFrontAndTop(front, top));
        return connect(state, context.getLevel(), context.getClickedPos());
    }

    @Override
    protected BlockState updateShape(BlockState state, Direction direction, BlockState neighborState,
            LevelAccessor level, BlockPos pos, BlockPos neighborPos) {
        return connect(state, level, pos);
    }

    /** Sets the connection properties from which neighbors continue the screen. */
    public static BlockState connect(BlockState state, BlockGetter level, BlockPos pos) {
        ScreenFrame frame = ScreenFrame.of(state);
        if (frame == null) {
            return state;
        }
        Vec3i right = frame.right();
        Vec3i top = frame.top();
        return state
                .setValue(UP, continues(level, pos.offset(top), state, frame))
                .setValue(DOWN, continues(level, pos.subtract(top), state, frame))
                .setValue(LEFT, continues(level, pos.subtract(right), state, frame))
                .setValue(RIGHT, continues(level, pos.offset(right), state, frame));
    }

    private static boolean continues(BlockGetter level, BlockPos pos, BlockState state, ScreenFrame frame) {
        return TvLayout.sameScreen(level.getBlockState(pos), state, frame);
    }

    @Override
    protected BlockState rotate(BlockState state, Rotation rotation) {
        return state.setValue(BlockStateProperties.ORIENTATION,
                rotation.rotation().rotate(state.getValue(BlockStateProperties.ORIENTATION)));
    }

    @Override
    protected BlockState mirror(BlockState state, Mirror mirror) {
        return state.setValue(BlockStateProperties.ORIENTATION,
                mirror.rotation().rotate(state.getValue(BlockStateProperties.ORIENTATION)));
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
