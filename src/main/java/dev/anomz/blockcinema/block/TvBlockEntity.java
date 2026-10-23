package dev.anomz.blockcinema.block;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Deque;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.WeakHashMap;

import org.jetbrains.annotations.Nullable;

import dev.anomz.blockcinema.BlockCinemaRegistries;
import dev.anomz.blockcinema.TvPageRules;
import dev.anomz.blockcinema.TvQueueEntry;
import dev.anomz.blockcinema.TvService;
import dev.anomz.blockcinema.TvSource;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

/**
 * A TV. The screen's origin TV (see {@link TvLayout}) holds what the screen plays: saved with the world
 * and sent to the clients near it, which play it themselves. Clients also cache the screen layout here.
 */
public class TvBlockEntity extends BlockEntity {
    private static final long LAYOUT_CHECK_INTERVAL = 10;
    /** Most videos waiting in a screen's queue. */
    public static final int MAX_QUEUE = 32;
    /**
     * TVs with something to play on this client, for the client's players to follow (client thread only).
     * Weak, so unloaded TVs never linger.
     */
    public static final Set<TvBlockEntity> CLIENT_PLAYING = Collections.newSetFromMap(new WeakHashMap<>());

    private TvLayout layout;
    private long layoutCheckedAt = Long.MIN_VALUE;
    @Nullable
    private TvSource source;
    /** What plays after the current video, in order (origin only). */
    private final List<TvQueueEntry> queue = new ArrayList<>();
    /** What played before, latest first, for "previous" (origin only, server side; not saved). */
    private final Deque<TvQueueEntry> history = new ArrayDeque<>();
    /** Whether a TV of the screen had a redstone signal at the last check (origin only, server side). */
    private boolean powered;
    /** Whether the signal paused what plays, so it resumes when the signal stops (a manual pause doesn't). */
    private boolean pausedByRedstone;

    public TvBlockEntity(BlockPos pos, BlockState state) {
        super(BlockCinemaRegistries.TV_BLOCK_ENTITY.get(), pos, state);
    }

    /** This TV's screen layout, re-checked twice a second so placing or breaking TVs updates it. */
    public TvLayout layout(long gameTime) {
        if (layout == null || gameTime - layoutCheckedAt >= LAYOUT_CHECK_INTERVAL || gameTime < layoutCheckedAt) {
            layout = TvLayout.of(level, worldPosition, getBlockState());
            layoutCheckedAt = gameTime;
        }
        return layout;
    }

    /** What this TV's screen plays, if this is the screen's origin and something is on. */
    @Nullable
    public TvSource source() {
        return source;
    }

    /** Server side: sets what the screen plays (null: nothing, the queue stays) and sends it to nearby clients. */
    public void setSource(@Nullable TvSource source) {
        this.source = source;
        sync();
    }

    /** What plays after the current video (unmodifiable). */
    public List<TvQueueEntry> queue() {
        return Collections.unmodifiableList(queue);
    }

    /** Server side: adds to the end of the queue; false when it is full. */
    public boolean enqueue(TvQueueEntry entry) {
        if (queue.size() >= MAX_QUEUE) {
            return false;
        }
        queue.add(entry);
        sync();
        return true;
    }

    /** Server side: plays the next queued video; false if the queue is empty (then nothing changes). */
    public boolean playNext(long gameTime) {
        if (queue.isEmpty()) {
            return false;
        }
        if (source != null) {
            history.push(entry(source));
            if (history.size() > MAX_QUEUE) {
                history.removeLast();
            }
        }
        source = queue.remove(0).start(gameTime);
        sync();
        return true;
    }

    /**
     * Server side: plays what played before the current video, which goes back to the front of the queue.
     * With nothing before it, starts the current video over.
     */
    public void playPrevious(long gameTime) {
        if (source == null) {
            return;
        }
        if (history.isEmpty()) {
            source = new TvSource(source.service(), source.page(), source.owner(), source.ownerName(), gameTime, 0, source.paused());
        } else {
            if (queue.size() >= MAX_QUEUE) {
                queue.remove(queue.size() - 1);
            }
            queue.add(0, entry(source));
            source = history.pop().start(gameTime);
        }
        sync();
    }

    private static TvQueueEntry entry(TvSource source) {
        return new TvQueueEntry(source.service(), source.page(), "", source.owner(), source.ownerName());
    }

    /** Server side: takes one entry out of the queue. */
    public void removeQueued(int index) {
        if (index >= 0 && index < queue.size()) {
            queue.remove(index);
            sync();
        }
    }

    /** Server side: turns the screen off and empties its queue. */
    public void turnOff() {
        source = null;
        queue.clear();
        history.clear();
        sync();
    }

    /** Server side: the screen's origin moved to {@code target} (it was turned): what plays and the queue go along. */
    public void moveScreenTo(TvBlockEntity target) {
        target.source = source;
        target.queue.clear();
        target.queue.addAll(queue);
        target.history.clear();
        target.history.addAll(history);
        target.sync();
        turnOff();
    }

    private void sync() {
        setChanged();
        if (level != null && !level.isClientSide) {
            level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), 3);
        }
    }

    /**
     * Server side: called when a neighbor of a TV changes. A redstone signal on any TV of the screen
     * pauses what it plays; when the signal stops, it plays on if the signal paused it.
     */
    public static void checkRedstone(Level level, BlockPos pos, BlockState state) {
        if (level.isClientSide) {
            return;
        }
        TvLayout layout = TvLayout.of(level, pos, state);
        if (layout.invalid() || !(level.getBlockEntity(layout.origin()) instanceof TvBlockEntity origin)) {
            return;
        }
        boolean powered = false;
        for (int r = 0; r < layout.rows() && !powered; r++) {
            for (int c = 0; c < layout.cols() && !powered; c++) {
                powered = level.hasNeighborSignal(layout.cell(c, r));
            }
        }
        origin.redstone(powered, level.getGameTime());
    }

    private void redstone(boolean powered, long gameTime) {
        if (powered == this.powered) {
            return;
        }
        this.powered = powered;
        if (powered && source != null && !source.paused()) {
            pausedByRedstone = true;
            setSource(source.pausedAt(gameTime));
        } else if (!powered && pausedByRedstone) {
            pausedByRedstone = false;
            if (source != null && source.paused()) {
                setSource(source.resumedAt(gameTime));
            }
        }
        setChanged();
    }

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        tag.putBoolean("powered", powered);
        tag.putBoolean("pausedByRedstone", pausedByRedstone);
        if (source != null) {
            CompoundTag saved = new CompoundTag();
            saved.putString("service", source.service().id());
            saved.putString("page", source.page());
            saved.putUUID("owner", source.owner());
            saved.putString("ownerName", source.ownerName());
            saved.putLong("anchorTick", source.anchorTick());
            saved.putDouble("anchorSeconds", source.anchorSeconds());
            saved.putBoolean("paused", source.paused());
            tag.put("source", saved);
        }
        if (!queue.isEmpty()) {
            ListTag list = new ListTag();
            for (TvQueueEntry entry : queue) {
                CompoundTag saved = new CompoundTag();
                saved.putString("service", entry.service().id());
                saved.putString("page", entry.page());
                saved.putString("title", entry.title());
                saved.putUUID("owner", entry.owner());
                saved.putString("ownerName", entry.ownerName());
                list.add(saved);
            }
            tag.put("queue", list);
        }
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        source = tag.contains("source") ? readSource(tag.getCompound("source")) : null;
        queue.clear();
        for (Tag element : tag.getList("queue", Tag.TAG_COMPOUND)) {
            TvQueueEntry entry = readQueueEntry((CompoundTag) element);
            if (entry != null && queue.size() < MAX_QUEUE) {
                queue.add(entry);
            }
        }
        powered = tag.getBoolean("powered");
        pausedByRedstone = tag.getBoolean("pausedByRedstone");
        if (level != null && level.isClientSide) {
            trackOnClient();
        }
    }

    /** The service saved under "service" if its page is still valid for it, else null. */
    @Nullable
    private static TvService readService(CompoundTag saved) {
        for (TvService candidate : TvService.values()) {
            if (candidate.id().equals(saved.getString("service"))) {
                return saved.hasUUID("owner") && TvPageRules.valid(candidate, saved.getString("page")) ? candidate : null;
            }
        }
        return null;
    }

    @Nullable
    private static TvQueueEntry readQueueEntry(CompoundTag saved) {
        TvService service = readService(saved);
        return service == null ? null : new TvQueueEntry(service, saved.getString("page"),
                TvQueueEntry.cleanTitle(saved.getString("title")), saved.getUUID("owner"), saved.getString("ownerName"));
    }

    /** Null if the saved source is damaged or no longer allowed in this form. */
    @Nullable
    private static TvSource readSource(CompoundTag saved) {
        TvService service = readService(saved);
        if (service == null) {
            return null;
        }
        String page = saved.getString("page");
        UUID owner = saved.getUUID("owner");
        return new TvSource(service, page, owner, saved.getString("ownerName"), saved.getLong("anchorTick"),
                Math.max(0, saved.getDouble("anchorSeconds")), saved.getBoolean("paused"));
    }

    @Override
    public void onLoad() {
        super.onLoad();
        if (level != null && level.isClientSide) {
            trackOnClient();
        }
    }

    @Override
    public void setRemoved() {
        super.setRemoved();
        CLIENT_PLAYING.remove(this);
    }

    private void trackOnClient() {
        if (source != null) {
            CLIENT_PLAYING.add(this);
        } else {
            CLIENT_PLAYING.remove(this);
        }
    }

    @Override
    public CompoundTag getUpdateTag(HolderLookup.Provider registries) {
        CompoundTag tag = saveWithoutMetadata(registries);
        // Never empty, so clients also load a "turned off" update (NeoForge skips empty ones).
        tag.putBoolean("synced", true);
        return tag;
    }

    @Override
    public Packet<ClientGamePacketListener> getUpdatePacket() {
        return ClientboundBlockEntityDataPacket.create(this);
    }
}
