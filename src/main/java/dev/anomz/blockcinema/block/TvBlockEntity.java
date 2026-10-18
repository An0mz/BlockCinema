package dev.anomz.blockcinema.block;

import java.util.Collections;
import java.util.Set;
import java.util.UUID;
import java.util.WeakHashMap;

import org.jetbrains.annotations.Nullable;

import dev.anomz.blockcinema.BlockCinemaRegistries;
import dev.anomz.blockcinema.TvPageRules;
import dev.anomz.blockcinema.TvService;
import dev.anomz.blockcinema.TvSource;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

/**
 * A TV. The screen's origin TV (see {@link TvLayout}) holds what the screen plays: saved with the world
 * and sent to the clients near it, which play it themselves. Clients also cache the screen layout here.
 */
public class TvBlockEntity extends BlockEntity {
    private static final long LAYOUT_CHECK_INTERVAL = 10;
    /**
     * TVs with something to play on this client, for the client's players to follow (client thread only).
     * Weak, so unloaded TVs never linger.
     */
    public static final Set<TvBlockEntity> CLIENT_PLAYING = Collections.newSetFromMap(new WeakHashMap<>());

    private TvLayout layout;
    private long layoutCheckedAt = Long.MIN_VALUE;
    @Nullable
    private TvSource source;

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

    /** Server side: sets what the screen plays (null turns it off) and sends it to nearby clients. */
    public void setSource(@Nullable TvSource source) {
        this.source = source;
        setChanged();
        if (level != null && !level.isClientSide) {
            level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), 3);
        }
    }

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
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
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        source = tag.contains("source") ? readSource(tag.getCompound("source")) : null;
        if (level != null && level.isClientSide) {
            trackOnClient();
        }
    }

    /** Null if the saved source is damaged or no longer allowed in this form. */
    @Nullable
    private static TvSource readSource(CompoundTag saved) {
        TvService service = null;
        for (TvService candidate : TvService.values()) {
            if (candidate.id().equals(saved.getString("service"))) {
                service = candidate;
            }
        }
        if (service == null || !saved.hasUUID("owner")) {
            return null;
        }
        String page = saved.getString("page");
        if (!TvPageRules.valid(service, page)) {
            return null;
        }
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
