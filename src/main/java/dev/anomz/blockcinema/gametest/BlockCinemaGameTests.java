package dev.anomz.blockcinema.gametest;

import dev.anomz.blockcinema.BlockCinema;
import dev.anomz.blockcinema.BlockCinemaRegistries;
import dev.anomz.blockcinema.TvPageRules;
import dev.anomz.blockcinema.TvService;
import dev.anomz.blockcinema.TvSource;
import dev.anomz.blockcinema.block.TvBlock;
import dev.anomz.blockcinema.block.TvBlockEntity;
import dev.anomz.blockcinema.block.TvLayout;
import dev.anomz.blockcinema.network.ChangeTvPayload;
import dev.anomz.blockcinema.network.ControlTvPayload;
import dev.anomz.blockcinema.network.QueueTvPayload;
import dev.anomz.blockcinema.network.RotateTvPayload;
import dev.anomz.blockcinema.network.TvRequests;
import dev.anomz.blockcinema.network.VideoEndedPayload;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.FrontAndTop;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/**
 * Server-side tests in a real world: screens joining, what the server does with players' requests,
 * the queue, turning floor screens and redstone. Run with {@code ./gradlew runGameTestServer}.
 */
@GameTestHolder(BlockCinema.MODID)
@PrefixGameTestTemplate(false)
public final class BlockCinemaGameTests {
    private static final String YOUTUBE_A = "https://blockcinema.anomz.dev/youtube.html?v=dQw4w9WgXcQ";
    private static final String YOUTUBE_B = "https://blockcinema.anomz.dev/youtube.html?v=jNQXAC9IVRw";

    private BlockCinemaGameTests() {
    }

    /** TVs facing north in a {@code cols} by {@code rows} wall, from (1, 1, 4) going east and up. */
    private static void wall(GameTestHelper helper, int cols, int rows) {
        BlockState tv = BlockCinemaRegistries.TV.get().defaultBlockState()
                .setValue(BlockStateProperties.ORIENTATION, FrontAndTop.NORTH_UP);
        for (int r = 0; r < rows; r++) {
            for (int c = 0; c < cols; c++) {
                helper.setBlock(new BlockPos(1 + c, 1 + r, 4), tv);
            }
        }
    }

    private static TvLayout layoutAt(GameTestHelper helper, BlockPos relative) {
        BlockPos pos = helper.absolutePos(relative);
        return TvLayout.of(helper.getLevel(), pos, helper.getLevel().getBlockState(pos));
    }

    private static TvBlockEntity origin(GameTestHelper helper, TvLayout layout) {
        return (TvBlockEntity) helper.getLevel().getBlockEntity(layout.origin());
    }

    /** A player standing in front of the screen. */
    private static ServerPlayer viewer(GameTestHelper helper) {
        ServerPlayer player = helper.makeMockServerPlayerInLevel();
        Vec3 spot = Vec3.atCenterOf(helper.absolutePos(new BlockPos(2, 1, 1)));
        player.moveTo(spot.x, spot.y, spot.z);
        return player;
    }

    /** Requests from one player are rate limited; tests send several at once. */
    private static void next(ServerPlayer player) {
        TvRequests.forget(player);
    }

    @GameTest(template = "empty")
    public static void tvsInARectangleJoin(GameTestHelper helper) {
        wall(helper, 3, 2);
        TvLayout layout = layoutAt(helper, new BlockPos(2, 1, 4));
        helper.assertTrue(!layout.invalid() && layout.cols() == 3 && layout.rows() == 2, "a 3x2 wall should be one screen");
        BlockState middle = helper.getBlockState(new BlockPos(2, 1, 4));
        helper.assertTrue(middle.getValue(TvBlock.LEFT) && middle.getValue(TvBlock.RIGHT) && middle.getValue(TvBlock.UP)
                && !middle.getValue(TvBlock.DOWN), "the bottom middle TV should join its neighbors on three sides");
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void otherShapesShowNothing(GameTestHelper helper) {
        wall(helper, 2, 1);
        helper.setBlock(new BlockPos(1, 2, 4), helper.getBlockState(new BlockPos(1, 1, 4)));
        helper.assertTrue(layoutAt(helper, new BlockPos(1, 1, 4)).invalid(), "an L of TVs isn't a screen");
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void playPauseSeekAndTurnOff(GameTestHelper helper) {
        wall(helper, 2, 1);
        TvLayout layout = layoutAt(helper, new BlockPos(1, 1, 4));
        ServerPlayer player = viewer(helper);
        long now = helper.getLevel().getGameTime();

        ChangeTvPayload.apply(player, ChangeTvPayload.of(layout.origin(), TvService.YOUTUBE, YOUTUBE_A, 30));
        TvSource source = origin(helper, layout).source();
        helper.assertTrue(source != null && YOUTUBE_A.equals(source.page()) && source.position(now) == 30,
                "playing should start at the asked time");

        next(player);
        ControlTvPayload.apply(player, new ControlTvPayload(layout.origin(), ControlTvPayload.PAUSE, 0));
        helper.assertTrue(origin(helper, layout).source().paused(), "pause should pause for everyone");

        next(player);
        ControlTvPayload.apply(player, new ControlTvPayload(layout.origin(), ControlTvPayload.SEEK, 100));
        helper.assertTrue(origin(helper, layout).source().position(now + 1000) == 100, "a paused jump stays where it jumped");

        next(player);
        ControlTvPayload.apply(player, new ControlTvPayload(layout.origin(), ControlTvPayload.STOP, 0));
        helper.assertTrue(origin(helper, layout).source() == null, "turning off should clear the screen");
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void badRequestsAreRefused(GameTestHelper helper) {
        wall(helper, 1, 1);
        TvLayout layout = layoutAt(helper, new BlockPos(1, 1, 4));
        ServerPlayer player = viewer(helper);

        ChangeTvPayload.apply(player, ChangeTvPayload.of(layout.origin(), TvService.YOUTUBE, "https://example.com/watch?v=x", 0));
        helper.assertTrue(origin(helper, layout).source() == null, "a page that isn't ours is refused");

        next(player);
        ChangeTvPayload.apply(player, ChangeTvPayload.of(layout.origin(), TvService.JELLYFIN, "http://192.168.1.5:8096", 0));
        helper.assertTrue(origin(helper, layout).source() == null, "a Jellyfin address instead of IDs is refused");

        next(player);
        player.moveTo(player.getX() + 200, player.getY(), player.getZ());
        ChangeTvPayload.apply(player, ChangeTvPayload.of(layout.origin(), TvService.YOUTUBE, YOUTUBE_A, 0));
        helper.assertTrue(origin(helper, layout).source() == null, "a player far away can't change the screen");
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void queueGoesOnWhenAVideoEnds(GameTestHelper helper) {
        wall(helper, 1, 1);
        TvLayout layout = layoutAt(helper, new BlockPos(1, 1, 4));
        ServerPlayer player = viewer(helper);
        ChangeTvPayload.apply(player, ChangeTvPayload.of(layout.origin(), TvService.YOUTUBE, YOUTUBE_A, 60));
        next(player);
        ChangeTvPayload.apply(player, ChangeTvPayload.queued(layout.origin(), TvService.YOUTUBE, YOUTUBE_B, "B"));
        TvBlockEntity tv = origin(helper, layout);
        helper.assertTrue(tv.queue().size() == 1 && YOUTUBE_A.equals(tv.source().page()), "a queued video waits its turn");

        next(player);
        VideoEndedPayload.apply(player, new VideoEndedPayload(layout.origin(), YOUTUBE_B));
        helper.assertTrue(YOUTUBE_A.equals(tv.source().page()), "an end report for another video is ignored");

        next(player);
        VideoEndedPayload.apply(player, new VideoEndedPayload(layout.origin(), YOUTUBE_A));
        helper.assertTrue(YOUTUBE_B.equals(tv.source().page()) && tv.queue().isEmpty(), "the next video plays when one ends");
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void previousGoesBackThroughTheQueue(GameTestHelper helper) {
        wall(helper, 1, 1);
        TvLayout layout = layoutAt(helper, new BlockPos(1, 1, 4));
        ServerPlayer player = viewer(helper);
        ChangeTvPayload.apply(player, ChangeTvPayload.of(layout.origin(), TvService.YOUTUBE, YOUTUBE_A, 0));
        next(player);
        ChangeTvPayload.apply(player, ChangeTvPayload.queued(layout.origin(), TvService.YOUTUBE, YOUTUBE_B, "B"));
        next(player);
        QueueTvPayload.apply(player, new QueueTvPayload(layout.origin(), QueueTvPayload.NEXT, 0));
        TvBlockEntity tv = origin(helper, layout);
        helper.assertTrue(YOUTUBE_B.equals(tv.source().page()) && tv.queue().isEmpty(), "next plays the queued video");

        next(player);
        QueueTvPayload.apply(player, new QueueTvPayload(layout.origin(), QueueTvPayload.PREVIOUS, 0));
        helper.assertTrue(YOUTUBE_A.equals(tv.source().page()) && tv.queue().size() == 1
                && YOUTUBE_B.equals(tv.queue().get(0).page()), "previous goes back, and what played returns to the queue");

        next(player);
        QueueTvPayload.apply(player, new QueueTvPayload(layout.origin(), QueueTvPayload.PREVIOUS, 0));
        helper.assertTrue(YOUTUBE_A.equals(tv.source().page()) && tv.source().position(helper.getLevel().getGameTime()) == 0,
                "with nothing before, previous starts over");
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void playlistMovesOnTogether(GameTestHelper helper) {
        wall(helper, 1, 1);
        TvLayout layout = layoutAt(helper, new BlockPos(1, 1, 4));
        ServerPlayer player = viewer(helper);
        String list = TvPageRules.youtubeListPage("PL1234567890", 0);
        ChangeTvPayload.apply(player, ChangeTvPayload.of(layout.origin(), TvService.YOUTUBE, list, 60));
        next(player);
        ChangeTvPayload.apply(player, ChangeTvPayload.queued(layout.origin(), TvService.YOUTUBE, YOUTUBE_B, "B"));

        next(player);
        VideoEndedPayload.apply(player, new VideoEndedPayload(layout.origin(), list, 1));
        TvBlockEntity tv = origin(helper, layout);
        helper.assertTrue(TvPageRules.youtubeListPage("PL1234567890", 1).equals(tv.source().page()) && tv.queue().size() == 1,
                "the playlist's next video plays before the queue");
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void turningAFloorScreenKeepsWhatPlays(GameTestHelper helper) {
        BlockState floor = BlockCinemaRegistries.TV.get().defaultBlockState()
                .setValue(BlockStateProperties.ORIENTATION, FrontAndTop.UP_NORTH);
        helper.setBlock(new BlockPos(3, 1, 3), floor);
        helper.setBlock(new BlockPos(4, 1, 3), floor);
        TvLayout layout = layoutAt(helper, new BlockPos(3, 1, 3));
        helper.assertTrue(!layout.invalid() && layout.cols() == 2 && layout.rows() == 1, "two floor TVs side by side join");
        ServerPlayer player = viewer(helper);
        ChangeTvPayload.apply(player, ChangeTvPayload.of(layout.origin(), TvService.YOUTUBE, YOUTUBE_A, 0));

        next(player);
        RotateTvPayload.apply(player, new RotateTvPayload(layout.origin()));
        TvLayout turned = layoutAt(helper, new BlockPos(3, 1, 3));
        helper.assertTrue(!turned.invalid() && turned.cols() == 1 && turned.rows() == 2, "a quarter turn swaps width and height");
        helper.assertTrue(helper.getBlockState(new BlockPos(3, 1, 3)).getValue(BlockStateProperties.ORIENTATION).top() == Direction.EAST,
                "the picture's top turned clockwise as seen from above");
        TvSource source = origin(helper, turned).source();
        helper.assertTrue(source != null && YOUTUBE_A.equals(source.page()), "what plays moves to the turned screen");
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void redstonePausesAndResumes(GameTestHelper helper) {
        wall(helper, 1, 1);
        TvLayout layout = layoutAt(helper, new BlockPos(1, 1, 4));
        ServerPlayer player = viewer(helper);
        ChangeTvPayload.apply(player, ChangeTvPayload.of(layout.origin(), TvService.YOUTUBE, YOUTUBE_A, 0));

        helper.setBlock(new BlockPos(1, 1, 5), Blocks.REDSTONE_BLOCK);
        helper.assertTrue(origin(helper, layout).source().paused(), "a redstone signal pauses the screen");
        helper.setBlock(new BlockPos(1, 1, 5), Blocks.AIR);
        helper.assertTrue(!origin(helper, layout).source().paused(), "it plays on when the signal stops");
        helper.succeed();
    }
}
