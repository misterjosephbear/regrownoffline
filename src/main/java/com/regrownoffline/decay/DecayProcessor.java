package com.regrownoffline.decay;

import com.regrownoffline.config.RegrownOfflineConfig;
import com.regrownoffline.data.ChunkDecayData;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;

import java.util.Iterator;
import java.util.Map;

/**
 * Advances tracked diffs one decay stage at a time, budgeted per server tick.
 *
 * A chunk only decays once it has been offline (per {@link ChunkDecayData.ChunkRecord#getLastActivityTime()})
 * for at least {@code offlineDaysBeforeDecay}, and only while the chunk is actually loaded - unloaded
 * chunks are left untouched entirely (their diffs simply wait).
 */
public final class DecayProcessor {

    private DecayProcessor() {
    }

    public static void tick(ServerLevel level) {
        int budget = RegrownOfflineConfig.DECAY_BLOCKS_PER_TICK.get();
        if (budget <= 0) {
            return;
        }

        ChunkDecayData data = ChunkDecayData.get(level);
        long now = System.currentTimeMillis();
        long offlineThresholdMillis = RegrownOfflineConfig.OFFLINE_DAYS_BEFORE_DECAY.get() * 86_400_000L;
        long stageIntervalMillis = RegrownOfflineConfig.DECAY_STAGE_INTERVAL_TICKS.get() * 50L;

        int consumed = 0;
        for (Map.Entry<Long, ChunkDecayData.ChunkRecord> entry : data.chunkEntries()) {
            if (consumed >= budget) {
                break;
            }
            ChunkDecayData.ChunkRecord record = entry.getValue();
            if (record.diffs().isEmpty()) {
                continue;
            }
            if (now - record.getLastActivityTime() < offlineThresholdMillis) {
                continue; // still within the "recently active" grace period
            }

            ChunkPos pos = new ChunkPos(entry.getKey());
            LevelChunk chunk = level.getChunkSource().getChunkNow(pos.x, pos.z);
            if (chunk == null) {
                continue; // only process currently loaded chunks
            }

            consumed += tickChunk(level, data, record, budget - consumed, now, stageIntervalMillis);
        }
    }

    private static int tickChunk(ServerLevel level, ChunkDecayData data, ChunkDecayData.ChunkRecord record,
                                  int budget, long now, long stageIntervalMillis) {
        int consumed = 0;
        Iterator<Map.Entry<BlockPos, ChunkDecayData.TrackedDiff>> it = record.diffs().entrySet().iterator();

        while (it.hasNext() && consumed < budget) {
            Map.Entry<BlockPos, ChunkDecayData.TrackedDiff> diffEntry = it.next();
            ChunkDecayData.TrackedDiff diff = diffEntry.getValue();

            if (now - diff.lastModifiedTime() < stageIntervalMillis) {
                continue;
            }

            BlockPos blockPos = diffEntry.getKey();
            if (!level.getBlockState(blockPos).equals(diff.currentState())) {
                // Something else changed this block since our last write (a player editing it again,
                // another mod, etc.) - drop it from tracking rather than fighting over it.
                it.remove();
                data.setDirty();
                continue;
            }

            Block nextBlock = DecayChainRegistry.nextBlock(diff.currentState().getBlock());
            BlockState nextState;
            boolean settled;
            if (nextBlock == null || nextBlock == diff.baselineState().getBlock()) {
                nextState = diff.baselineState();
                settled = true;
            } else {
                nextState = nextBlock.defaultBlockState();
                settled = false;
            }

            level.setBlock(blockPos, nextState, Block.UPDATE_CLIENTS | Block.UPDATE_KNOWN_SHAPE);
            consumed++;

            if (settled) {
                it.remove();
            } else {
                diff.advance(nextState, now);
            }
            data.setDirty();
        }

        return consumed;
    }
}
