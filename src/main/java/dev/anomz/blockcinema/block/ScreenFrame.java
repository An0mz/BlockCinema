package dev.anomz.blockcinema.block;

import java.util.Arrays;
import java.util.stream.IntStream;

import org.jetbrains.annotations.Nullable;

import net.minecraft.core.Direction;
import net.minecraft.core.FrontAndTop;
import net.minecraft.core.Vec3i;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.phys.Vec3;

/**
 * Which way a TV's screen lies, seen by someone in front of it: {@code right} and {@code top} step to the next TV
 * of the screen to the viewer's right and up the picture; {@code normal} points out of the screen at the viewer.
 * {@code modelX} and {@code modelY} are the blockstate rotations of the model, whose picture faces north, top up.
 */
public record ScreenFrame(Vec3i right, Vec3i top, Vec3 normal, int modelX, int modelY, boolean diagonal) {
    private static final double SQRT2 = Math.sqrt(2);
    private static final ScreenFrame[] ORIENTED = Arrays.stream(FrontAndTop.values())
            .map(ScreenFrame::oriented).toArray(ScreenFrame[]::new);
    private static final ScreenFrame[] DIAGONAL = IntStream.range(0, 4)
            .mapToObj(Direction::from2DDataValue).map(ScreenFrame::diagonalFacing).toArray(ScreenFrame[]::new);

    @Nullable
    public static ScreenFrame of(BlockState state) {
        if (state.getBlock() instanceof DiagonalTvBlock) {
            return diagonal(state.getValue(HorizontalDirectionalBlock.FACING));
        }
        if (state.getBlock() instanceof TvBlock) {
            return of(state.getValue(BlockStateProperties.ORIENTATION));
        }
        return null;
    }

    public static ScreenFrame of(FrontAndTop orientation) {
        return ORIENTED[orientation.ordinal()];
    }

    /**
     * A diagonal TV: {@code facing} turned 45 degrees clockwise is where the screen faces, so north
     * faces north-east and the screen runs from the block's north-west corner to its south-east corner.
     */
    public static ScreenFrame diagonal(Direction facing) {
        return DIAGONAL[facing.get2DDataValue()];
    }

    private static ScreenFrame oriented(FrontAndTop orientation) {
        Direction front = orientation.front();
        Direction top = orientation.top();
        Vec3i right = top.getNormal().cross(front.getNormal());
        int x;
        int y;
        if (front == Direction.UP) {
            // Floor: the model is tipped over (north face up, its top then points south), then turned.
            x = 270;
            y = switch (top) {
                case WEST -> 90;
                case NORTH -> 180;
                case EAST -> 270;
                default -> 0;
            };
        } else if (front == Direction.DOWN) {
            // Ceiling: tipped the other way (north face down, its top then points north), then turned.
            x = 90;
            y = horizontalTurn(top);
        } else {
            x = 0;
            y = horizontalTurn(front);
        }
        return new ScreenFrame(right, top.getNormal(), Vec3.atLowerCornerOf(front.getNormal()), x, y, false);
    }

    private static ScreenFrame diagonalFacing(Direction facing) {
        Direction clockwise = facing.getClockWise();
        Vec3i right = facing.getNormal().offset(facing.getCounterClockWise().getNormal());
        Vec3 normal = Vec3.atLowerCornerOf(facing.getNormal().offset(clockwise.getNormal())).normalize();
        return new ScreenFrame(right, Direction.UP.getNormal(), normal, 0, horizontalTurn(facing), true);
    }

    private static int horizontalTurn(Direction direction) {
        return switch (direction) {
            case EAST -> 90;
            case SOUTH -> 180;
            case WEST -> 270;
            default -> 0;
        };
    }

    public double cellWidth() {
        return diagonal ? SQRT2 : 1;
    }

    public double frontDistance() {
        return diagonal ? 0.05 : 0.5;
    }

    /** Extra turn (degrees, around Y) after the model rotation: the diagonal screen is 45 degrees further. */
    public float extraYaw() {
        return diagonal ? -45 : 0;
    }
}
