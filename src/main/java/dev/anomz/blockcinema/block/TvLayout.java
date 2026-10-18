package dev.anomz.blockcinema.block;

import java.util.ArrayDeque;

import it.unimi.dsi.fastutil.longs.LongOpenHashSet;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Where one TV sits in its screen, seen from the front: {@code col} counts from the viewer's left,
 * {@code row} from the bottom. A screen is a whole group of touching TVs facing the same way that
 * fills a rectangle of at most {@link #MAX_COLS} by {@link #MAX_ROWS} (16:9). Every TV of the group
 * works out the same screen, so their slices of the picture line up. Any other group (a cross, an L,
 * one with a gap, or too big) is {@code invalid} and shows nothing.
 */
public record TvLayout(BlockPos origin, Direction right, int cols, int rows, int col, int row, boolean invalid) {
    /** Biggest screen in blocks; also bounds the work done per check. */
    public static final int MAX_COLS = 16;
    public static final int MAX_ROWS = 9;
    private static final int MAX_CELLS = MAX_COLS * MAX_ROWS;

    /** The screen {@code pos} belongs to. */
    public static TvLayout of(BlockGetter level, BlockPos pos, Direction facing) {
        // The viewer stands in front of the TV, looking at it; their right is the TV's counter-clockwise side.
        Direction right = facing.getCounterClockWise();
        Direction[] plane = { right, right.getOpposite(), Direction.UP, Direction.DOWN };
        // Walk the whole group, in screen coordinates relative to pos.
        LongOpenHashSet seen = new LongOpenHashSet();
        ArrayDeque<BlockPos> queue = new ArrayDeque<>();
        seen.add(pos.asLong());
        queue.add(pos);
        int minCol = 0;
        int maxCol = 0;
        int minRow = 0;
        int maxRow = 0;
        while (!queue.isEmpty()) {
            BlockPos cell = queue.poll();
            int c = (cell.getX() - pos.getX()) * right.getStepX() + (cell.getZ() - pos.getZ()) * right.getStepZ();
            int r = cell.getY() - pos.getY();
            minCol = Math.min(minCol, c);
            maxCol = Math.max(maxCol, c);
            minRow = Math.min(minRow, r);
            maxRow = Math.max(maxRow, r);
            for (Direction step : plane) {
                BlockPos next = cell.relative(step);
                if (!seen.contains(next.asLong()) && isTv(level.getBlockState(next), facing)) {
                    if (seen.size() >= MAX_CELLS) {
                        return new TvLayout(pos, right, 1, 1, 0, 0, true);
                    }
                    seen.add(next.asLong());
                    queue.add(next);
                }
            }
        }
        int cols = maxCol - minCol + 1;
        int rows = maxRow - minRow + 1;
        // Every cell of the bounding box was reached, so the group is a filled rectangle.
        boolean rectangle = seen.size() == cols * rows;
        BlockPos origin = pos.relative(right, minCol).above(minRow);
        return new TvLayout(origin, right, cols, rows, -minCol, -minRow,
                !rectangle || cols > MAX_COLS || rows > MAX_ROWS);
    }

    static boolean isTv(BlockState state, Direction facing) {
        return state.getBlock() instanceof TvBlock && state.getValue(HorizontalDirectionalBlock.FACING) == facing;
    }
}
