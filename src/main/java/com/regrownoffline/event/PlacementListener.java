package com.regrownoffline.event;

import com.regrownoffline.data.ChunkDecayData;
import com.regrownoffline.worldgen.BaselineCache;
import com.regrownoffline.worldgen.BaselineGenerator;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;
import net.neoforged.neoforge.event.level.ChunkEvent;
import net.neoforged.neoforge.event.level.BlockEvent;

/**
 * Wires the two ways a chunk's decay-tracking data gets populated:
 * <ul>
 *     <li>{@link #onChunkLoad}: first time a chunk loads (fresh world or retroactively on an existing
 *     world), kick off async baseline generation + a full diff of the chunk.</li>
 *     <li>{@link #onEntityPlace}: after that initial diff, every new player placement is diffed
 *     against the cached baseline for its position individually - much cheaper than re-diffing the
 *     whole chunk on every placement.</li>
 * </ul>
 */
public final class PlacementListener {

    private PlacementListener() {
    }

    public static void onChunkLoad(ChunkEvent.Load event) {
        if (!(event.getLevel() instanceof ServerLevel level)) {
            return;
        }
        if (!(event.getChunk() instanceof LevelChunk chunk)) {
            return;
        }

        ChunkDecayData data = ChunkDecayData.get(level);
        ChunkDecayData.ChunkRecord record = data.getOrCreateRecord(chunk.getPos());
        if (record.isBaselineGenerated()) {
            return;
        }

        BaselineGenerator.generateAndDiff(level, chunk);
    }

    public static void onChunkUnload(ChunkEvent.Unload event) {
        if (!(event.getLevel() instanceof ServerLevel level)) {
            return;
        }
        BaselineCache.remove(level, event.getChunk().getPos());
    }

    public static void onEntityPlace(BlockEvent.EntityPlaceEvent event) {
        if (!(event.getLevel() instanceof ServerLevel level)) {
            return;
        }

        BlockPos pos = event.getPos();
        ChunkPos chunkPos = new ChunkPos(pos);
        ChunkDecayData data = ChunkDecayData.get(level);
        ChunkDecayData.ChunkRecord record = data.getOrCreateRecord(chunkPos);

        long now = System.currentTimeMillis();
        if (event.getEntity() instanceof Player player) {
            record.touch(now, player.getUUID());
        }

        if (!record.isBaselineGenerated()) {
            // The chunk-load-triggered baseline pass hasn't finished yet; it will diff the chunk's
            // live state (which will already include this placement) once it completes, so recording
            // anything here would double count.
            return;
        }

        BlockState baseline = BaselineCache.get(level, pos);
        if (baseline == null) {
            // Baseline cache was evicted (e.g. the chunk unloaded and reloaded in an unusual order);
            // skip rather than guess at what should be here.
            return;
        }

        BlockState placed = level.getBlockState(pos);
        if (placed.equals(baseline)) {
            record.diffs().remove(pos);
        } else {
            record.diffs().put(pos, new ChunkDecayData.TrackedDiff(placed, baseline, now));
        }
        data.setDirty();
    }
}
