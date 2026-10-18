package dev.anomz.blockcinema.network;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import org.jetbrains.annotations.Nullable;

import dev.anomz.blockcinema.TvServerConfig;
import dev.anomz.blockcinema.TvSource;
import dev.anomz.blockcinema.block.TvBlockEntity;
import dev.anomz.blockcinema.block.TvLayout;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.phys.Vec3;

/** The checks every TV request from a client goes through on the server. Server thread only. */
public final class TvRequests {
    /** Players farther than this from a screen's origin can't change it (screens are up to 16 wide). */
    private static final double MAX_DISTANCE = 64;
    /** A hacked client could send requests every tick; this is plenty for menu clicks and the time bar. */
    private static final long MIN_INTERVAL_NANOS = 100_000_000L;
    private static final Map<UUID, Long> LAST_ACCEPTED = new HashMap<>();

    private TvRequests() {
    }

    /** The origin TV of a valid screen at {@code origin} near the player, or null. */
    @Nullable
    static TvBlockEntity screen(ServerPlayer player, BlockPos origin) {
        Level level = player.level();
        if (!level.isLoaded(origin) || player.distanceToSqr(Vec3.atCenterOf(origin)) > MAX_DISTANCE * MAX_DISTANCE
                || !(level.getBlockEntity(origin) instanceof TvBlockEntity tv)) {
            return null;
        }
        TvLayout layout = TvLayout.of(level, origin, tv.getBlockState().getValue(HorizontalDirectionalBlock.FACING));
        return !layout.invalid() && layout.origin().equals(origin) ? tv : null;
    }

    /** Whether the player may change what plays: anyone by default, else its starter and operators. */
    static boolean mayControl(ServerPlayer player, @Nullable TvSource current) {
        return current == null || TvServerConfig.anyoneCanControl() || current.owner().equals(player.getUUID())
                || player.hasPermissions(2);
    }

    static boolean rateLimitAllows(Player player) {
        long now = System.nanoTime();
        Long last = LAST_ACCEPTED.get(player.getUUID());
        if (last != null && now - last < MIN_INTERVAL_NANOS) {
            return false;
        }
        LAST_ACCEPTED.put(player.getUUID(), now);
        return true;
    }

    /** Seconds from a client, kept to something sensible. */
    static double seconds(double value) {
        return Double.isFinite(value) ? Math.max(0, Math.min(value, 7 * 24 * 3600)) : 0;
    }

    public static void forget(Player player) {
        LAST_ACCEPTED.remove(player.getUUID());
    }
}
