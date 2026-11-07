package dev.anomz.blockcinema.block;

import java.util.ArrayDeque;

import it.unimi.dsi.fastutil.longs.LongOpenHashSet;

import net.minecraft.core.BlockPos;
import net.minecraft.core.FrontAndTop;
import net.minecraft.core.Vec3i;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;

/**
 * Where one TV sits in its screen, seen from the front: {@code col} counts from the viewer's left, {@code row}
 * from the bottom of the picture. A screen is a filled rectangle of same-kind TVs lying the same way, at most
 * {@link #MAX_COLS} by {@link #MAX_ROWS} (16:9); any other group is {@code invalid} and shows nothing.
 */
public record TvLayout(BlockPos origin, ScreenFrame frame, int cols, int rows, int col, int row, boolean invalid) {
    /** Biggest screen in TVs; also bounds the work done per check. */
    public static final int MAX_COLS = 16;
    public static final int MAX_ROWS = 9;
    private static final int MAX_CELLS = MAX_COLS * MAX_ROWS;

    public static TvLayout of(BlockGetter level, BlockPos pos, BlockState state) {
        ScreenFrame frame = ScreenFrame.of(state);
        if (frame == null) {
            return new TvLayout(pos, ScreenFrame.of(FrontAndTop.NORTH_UP), 1, 1, 0, 0, true);
        }
        Vec3i right = frame.right();
        Vec3i top = frame.top();
        Vec3i[] steps = { right, multiply(right, -1), top, multiply(top, -1) };
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
            Vec3i offset = cell.subtract(pos);
            int c = along(offset, right);
            int r = along(offset, top);
            minCol = Math.min(minCol, c);
            maxCol = Math.max(maxCol, c);
            minRow = Math.min(minRow, r);
            maxRow = Math.max(maxRow, r);
            for (Vec3i step : steps) {
                BlockPos next = cell.offset(step);
                if (!seen.contains(next.asLong()) && sameScreen(level.getBlockState(next), state, frame)) {
                    if (seen.size() >= MAX_CELLS) {
                        return new TvLayout(pos, frame, 1, 1, 0, 0, true);
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
        BlockPos origin = pos.offset(multiply(right, minCol)).offset(multiply(top, minRow));
        return new TvLayout(origin, frame, cols, rows, -minCol, -minRow,
                !rectangle || cols > MAX_COLS || rows > MAX_ROWS);
    }

    static boolean sameScreen(BlockState other, BlockState state, ScreenFrame frame) {
        return other.getBlock() == state.getBlock() && frame.equals(ScreenFrame.of(other));
    }

    public AABB bounds() {
        return new AABB(origin).minmax(new AABB(cell(cols - 1, rows - 1)));
    }

    public boolean portrait() {
        return rows > cols * frame.cellWidth();
    }

    public BlockPos cell(int c, int r) {
        return origin.offset(multiply(frame.right(), c)).offset(multiply(frame.top(), r));
    }

    private static int along(Vec3i offset, Vec3i step) {
        int dot = offset.getX() * step.getX() + offset.getY() * step.getY() + offset.getZ() * step.getZ();
        int length = step.getX() * step.getX() + step.getY() * step.getY() + step.getZ() * step.getZ();
        return Math.floorDiv(dot, length);
    }

    private static Vec3i multiply(Vec3i v, int n) {
        return new Vec3i(v.getX() * n, v.getY() * n, v.getZ() * n);
    }
}
