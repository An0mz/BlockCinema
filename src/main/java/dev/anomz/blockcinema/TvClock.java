package dev.anomz.blockcinema;

import net.minecraft.Util;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.saveddata.SavedData;
import net.neoforged.neoforge.network.PacketDistributor;

import dev.anomz.blockcinema.network.TvClockPayload;

/**
 * The clock shared videos run on, counted in ticks: the world's game time plus the ticks a lagging server
 * didn't get to. Videos play in real time, so on game time alone a server at 15 TPS would have every TV
 * jumping back again and again. It stands still with the world (server off, singleplayer paused), like
 * game time. Saved with the world, as the {@link TvSource}s saved in TVs are anchored to it. Server side;
 * clients follow it with {@link TvClockFollower}.
 */
public final class TvClock extends SavedData {
    private static final String NAME = BlockCinema.MODID + "_clock";
    private static final SavedData.Factory<TvClock> FACTORY = new SavedData.Factory<>(TvClock::new, TvClock::load, null);
    private static final String LOST_TAG = "lostTicks";
    /** A singleplayer world can pause, which stops ticks: a longer gap than this is that, not lag. */
    private static final long MAX_GAP_PAUSABLE_MILLIS = 1000;
    /** A longer gap than this is a suspended computer or the like, not lag. */
    private static final long MAX_GAP_MILLIS = 60_000;
    private static final long SEND_INTERVAL_MILLIS = 1000;

    private static long lastTickMillis;
    private static long lastSentMillis;

    private double lostTicks;

    private TvClock() {
    }

    private static TvClock load(CompoundTag tag, HolderLookup.Provider registries) {
        TvClock clock = new TvClock();
        clock.lostTicks = Math.max(0, tag.getDouble(LOST_TAG));
        return clock;
    }

    @Override
    public CompoundTag save(CompoundTag tag, HolderLookup.Provider registries) {
        tag.putDouble(LOST_TAG, lostTicks);
        return tag;
    }

    private static TvClock of(MinecraftServer server) {
        return server.overworld().getDataStorage().computeIfAbsent(FACTORY, NAME);
    }

    /** The clock now, in ticks. Server side. */
    public static long now(Level level) {
        return level.getGameTime() + (long) of(level.getServer()).lostTicks;
    }

    /** Call at the end of every server tick. */
    public static void onServerTick(MinecraftServer server) {
        long now = Util.getMillis();
        if (lastTickMillis != 0) {
            boolean pausable = !server.isDedicatedServer() && !server.isPublished();
            TvClock clock = of(server);
            double lost = TvClockLag.lostAfter(clock.lostTicks, now - lastTickMillis, pausable ? MAX_GAP_PAUSABLE_MILLIS : MAX_GAP_MILLIS);
            if (lost != clock.lostTicks) {
                clock.lostTicks = lost;
                clock.setDirty();
            }
        }
        lastTickMillis = now;
        if (now - lastSentMillis >= SEND_INTERVAL_MILLIS) {
            lastSentMillis = now;
            for (ServerPlayer player : server.getPlayerList().getPlayers()) {
                sendTo(player);
            }
        }
    }

    /** Tells a player where the clock is, unless their connection can't take it (a mock player, for one). */
    public static void sendTo(ServerPlayer player) {
        if (player.connection.hasChannel(TvClockPayload.TYPE)) {
            PacketDistributor.sendToPlayer(player, new TvClockPayload(now(player.level())));
        }
    }

    /** Call when a server stops: the next one in this game starts counting afresh. */
    public static void onServerStopped() {
        lastTickMillis = 0;
        lastSentMillis = 0;
    }
}
