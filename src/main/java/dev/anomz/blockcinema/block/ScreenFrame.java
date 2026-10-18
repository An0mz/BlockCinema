package dev.anomz.blockcinema.block;

import org.jetbrains.annotations.Nullable;

import net.minecraft.core.Direction;
import net.minecraft.core.FrontAndTop;
import net.minecraft.core.Vec3i;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.phys.Vec3;

/**
 * Which way a TV's screen lies, seen by someone in front of it: {@code right} and {@code top} are the
 * steps from one TV of the screen to the next one to the viewer's right and up the picture, and
 * {@code normal} points out of the screen at the viewer. A wall, floor or ceiling TV steps one block
 * at a time; a diagonal TV steps one block on two axes, so each of its cells is {@link #cellWidth()} wide.
 *
 * @param modelX blockstate X rotation of the TV's model (its picture faces north, top up, before rotating)
 * @param modelY blockstate Y rotation of the model
 * @param diagonal the screen runs corner to corner, 45 degrees off the block grid
 */
public record ScreenFrame(Vec3i right, Vec3i top, Vec3 normal, int modelX, int modelY, boolean diagonal) {
    private static final double SQRT2 = Math.sqrt(2);

    /** The frame of the TV in {@code state}, or null if it isn't a TV. */
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

    /** A wall, floor or ceiling TV whose screen faces {@code front}, with the top of the picture at {@code top}. */
    public static ScreenFrame of(FrontAndTop orientation) {
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

    /**
     * A diagonal TV: {@code facing} turned 45 degrees clockwise is where the screen faces, so north
     * faces north-east and the screen runs from the block's north-west corner to its south-east corner.
     */
    public static ScreenFrame diagonal(Direction facing) {
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

    /** How wide one TV of the screen is, in blocks. */
    public double cellWidth() {
        return diagonal ? SQRT2 : 1;
    }

    /** How far the TV's screen surface is from the block's center, along {@link #normal}. */
    public double frontDistance() {
        return diagonal ? 0.05 : 0.5;
    }

    /** Extra turn (degrees, around Y) after the model rotation: the diagonal screen is 45 degrees further. */
    public float extraYaw() {
        return diagonal ? -45 : 0;
    }
}
