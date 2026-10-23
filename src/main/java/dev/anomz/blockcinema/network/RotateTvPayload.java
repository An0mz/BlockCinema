package dev.anomz.blockcinema.network;

import dev.anomz.blockcinema.BlockCinema;
import dev.anomz.blockcinema.TvSource;
import dev.anomz.blockcinema.block.TvBlock;
import dev.anomz.blockcinema.block.TvBlockEntity;
import dev.anomz.blockcinema.block.TvLayout;
import io.netty.buffer.ByteBuf;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.FrontAndTop;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/**
 * Client to server: turn the picture of the floor or ceiling screen at {@code origin} a quarter turn
 * clockwise, as seen by someone looking at it (half a turn if a quarter turn would make it too big).
 * Every TV of the screen turns, and what it plays moves to the turned screen's new origin.
 */
public record RotateTvPayload(BlockPos origin) implements CustomPacketPayload {
    public static final Type<RotateTvPayload> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(BlockCinema.MODID, "rotate_tv"));
    public static final StreamCodec<ByteBuf, RotateTvPayload> STREAM_CODEC =
            BlockPos.STREAM_CODEC.map(RotateTvPayload::new, RotateTvPayload::origin);

    @Override
    public Type<RotateTvPayload> type() {
        return TYPE;
    }

    /** Runs on the server thread. */
    public static void handle(RotateTvPayload payload, IPayloadContext context) {
        if (context.player() instanceof ServerPlayer player) {
            apply(player, payload);
        }
    }

    /** What the server does with it from {@code player} (also used by the game tests). */
    public static void apply(ServerPlayer player, RotateTvPayload payload) {
        if (!TvRequests.rateLimitAllows(player)) {
            return;
        }
        TvBlockEntity tv = TvRequests.screen(player, payload.origin());
        if (tv == null || !TvRequests.mayControl(player, tv.source()) || !(tv.getBlockState().getBlock() instanceof TvBlock)) {
            return;
        }
        FrontAndTop orientation = tv.getBlockState().getValue(BlockStateProperties.ORIENTATION);
        Direction front = orientation.front();
        if (front.getAxis() != Direction.Axis.Y) {
            // Wall screens always have the picture's top up.
            return;
        }
        Level level = player.level();
        TvLayout layout = TvLayout.of(level, payload.origin(), tv.getBlockState());
        // A quarter turn swaps width and height; if that would be too big, turn it around instead.
        boolean quarter = layout.rows() <= TvLayout.MAX_COLS && layout.cols() <= TvLayout.MAX_ROWS;
        Direction top = orientation.top();
        // Clockwise as seen from above for a floor; a ceiling is seen from below.
        Direction turnedTop = !quarter ? top.getOpposite() : front == Direction.UP ? top.getClockWise() : top.getCounterClockWise();
        FrontAndTop turned = FrontAndTop.fromFrontAndTop(front, turnedTop);

        TvSource source = tv.source();
        // Turn every TV first (no shape updates yet, the screen is half turned), then join them up again.
        for (int r = 0; r < layout.rows(); r++) {
            for (int c = 0; c < layout.cols(); c++) {
                BlockPos cell = layout.cell(c, r);
                level.setBlock(cell, level.getBlockState(cell).setValue(BlockStateProperties.ORIENTATION, turned), Block.UPDATE_CLIENTS);
            }
        }
        for (int r = 0; r < layout.rows(); r++) {
            for (int c = 0; c < layout.cols(); c++) {
                BlockPos cell = layout.cell(c, r);
                BlockState joined = TvBlock.connect(level.getBlockState(cell), level, cell);
                level.setBlock(cell, joined, Block.UPDATE_ALL);
            }
        }
        // What it plays and its queue live on the screen's origin TV, which is a different corner now.
        TvLayout turnedLayout = TvLayout.of(level, payload.origin(), level.getBlockState(payload.origin()));
        if ((source != null || !tv.queue().isEmpty()) && !turnedLayout.origin().equals(payload.origin())
                && level.getBlockEntity(turnedLayout.origin()) instanceof TvBlockEntity newOrigin) {
            tv.moveScreenTo(newOrigin);
        }
    }
}
