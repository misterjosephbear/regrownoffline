package com.regrownoffline.data;

import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderGetter;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.nbt.Tag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.saveddata.SavedData;

import javax.annotation.Nullable;
import java.util.Collections;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Per-level persistent store of which blocks have drifted from their generated baseline.
 *
 * Keyed by {@link ChunkPos#toLong()} rather than the {@link ChunkPos} object itself so the backing
 * map can be a plain {@code long} key without an extra wrapper allocation per chunk.
 */
public final class ChunkDecayData extends SavedData {

    private static final String DATA_NAME = "regrownoffline_chunk_decay";

    public static final SavedData.Factory<ChunkDecayData> FACTORY =
            new SavedData.Factory<>(ChunkDecayData::new, ChunkDecayData::load, null);

    private final Map<Long, ChunkRecord> chunks = new ConcurrentHashMap<>();

    public static ChunkDecayData get(ServerLevel level) {
        return level.getDataStorage().computeIfAbsent(FACTORY, DATA_NAME);
    }

    public ChunkRecord getOrCreateRecord(ChunkPos pos) {
        return chunks.computeIfAbsent(pos.toLong(), key -> new ChunkRecord());
    }

    @Nullable
    public ChunkRecord getRecord(ChunkPos pos) {
        return chunks.get(pos.toLong());
    }

    public void removeRecord(ChunkPos pos) {
        if (chunks.remove(pos.toLong()) != null) {
            setDirty();
        }
    }

    /** All tracked chunks, keyed by {@link ChunkPos#toLong()}. Safe to iterate and mutate values from the main thread. */
    public Iterable<Map.Entry<Long, ChunkRecord>> chunkEntries() {
        return Collections.unmodifiableSet(chunks.entrySet());
    }

    private static ChunkDecayData load(CompoundTag tag, HolderLookup.Provider registries) {
        ChunkDecayData data = new ChunkDecayData();
        HolderGetter<Block> blocks = registries.lookupOrThrow(Registries.BLOCK);
        ListTag chunkList = tag.getList("Chunks", Tag.TAG_COMPOUND);
        for (int i = 0; i < chunkList.size(); i++) {
            CompoundTag chunkTag = chunkList.getCompound(i);
            long chunkPosLong = chunkTag.getLong("Pos");
            data.chunks.put(chunkPosLong, ChunkRecord.load(chunkTag, blocks));
        }
        return data;
    }

    @Override
    public CompoundTag save(CompoundTag tag, HolderLookup.Provider registries) {
        ListTag chunkList = new ListTag();
        for (Map.Entry<Long, ChunkRecord> entry : chunks.entrySet()) {
            ChunkRecord record = entry.getValue();
            if (record.isEmpty()) {
                continue;
            }
            CompoundTag chunkTag = new CompoundTag();
            chunkTag.putLong("Pos", entry.getKey());
            record.save(chunkTag);
            chunkList.add(chunkTag);
        }
        tag.put("Chunks", chunkList);
        return tag;
    }

    /** Decay bookkeeping for a single chunk: activity tracking plus the set of blocks that differ from baseline. */
    public static final class ChunkRecord {
        private boolean baselineGenerated;
        private long lastActivityTime;
        @Nullable
        private UUID lastPlayer;
        private final Map<BlockPos, TrackedDiff> diffs = new ConcurrentHashMap<>();

        public boolean isBaselineGenerated() {
            return baselineGenerated;
        }

        public void markBaselineGenerated() {
            this.baselineGenerated = true;
        }

        public long getLastActivityTime() {
            return lastActivityTime;
        }

        @Nullable
        public UUID getLastPlayer() {
            return lastPlayer;
        }

        /** Marks this chunk as recently active, resetting its offline timer. {@code player} may be null for non-player touches. */
        public void touch(long time, @Nullable UUID player) {
            this.lastActivityTime = time;
            if (player != null) {
                this.lastPlayer = player;
            }
        }

        public Map<BlockPos, TrackedDiff> diffs() {
            return diffs;
        }

        public boolean isEmpty() {
            return !baselineGenerated && diffs.isEmpty();
        }

        private void save(CompoundTag tag) {
            tag.putBoolean("BaselineGenerated", baselineGenerated);
            tag.putLong("LastActivity", lastActivityTime);
            if (lastPlayer != null) {
                tag.putUUID("LastPlayer", lastPlayer);
            }
            ListTag diffList = new ListTag();
            for (Map.Entry<BlockPos, TrackedDiff> entry : diffs.entrySet()) {
                CompoundTag diffTag = new CompoundTag();
                diffTag.putLong("Pos", entry.getKey().asLong());
                entry.getValue().save(diffTag);
                diffList.add(diffTag);
            }
            tag.put("Diffs", diffList);
        }

        private static ChunkRecord load(CompoundTag tag, HolderGetter<Block> blocks) {
            ChunkRecord record = new ChunkRecord();
            record.baselineGenerated = tag.getBoolean("BaselineGenerated");
            record.lastActivityTime = tag.getLong("LastActivity");
            if (tag.hasUUID("LastPlayer")) {
                record.lastPlayer = tag.getUUID("LastPlayer");
            }
            ListTag diffList = tag.getList("Diffs", Tag.TAG_COMPOUND);
            for (int i = 0; i < diffList.size(); i++) {
                CompoundTag diffTag = diffList.getCompound(i);
                BlockPos pos = BlockPos.of(diffTag.getLong("Pos"));
                record.diffs.put(pos, TrackedDiff.load(diffTag, blocks));
            }
            return record;
        }
    }

    /** A single block that differs from its worldgen baseline: what's there now, what it should decay toward, and progress so far. */
    public static final class TrackedDiff {
        private BlockState currentState;
        private final BlockState baselineState;
        private long lastModifiedTime;
        private int decayStage;

        public TrackedDiff(BlockState currentState, BlockState baselineState, long lastModifiedTime) {
            this.currentState = currentState;
            this.baselineState = baselineState;
            this.lastModifiedTime = lastModifiedTime;
            this.decayStage = 0;
        }

        public BlockState currentState() {
            return currentState;
        }

        public BlockState baselineState() {
            return baselineState;
        }

        public long lastModifiedTime() {
            return lastModifiedTime;
        }

        public int decayStage() {
            return decayStage;
        }

        /** Records that this position was just advanced to {@code newState} at {@code time}. */
        public void advance(BlockState newState, long time) {
            this.currentState = newState;
            this.lastModifiedTime = time;
            this.decayStage++;
        }

        private void save(CompoundTag tag) {
            tag.put("Current", NbtUtils.writeBlockState(currentState));
            tag.put("Baseline", NbtUtils.writeBlockState(baselineState));
            tag.putLong("Modified", lastModifiedTime);
            tag.putInt("Stage", decayStage);
        }

        private static TrackedDiff load(CompoundTag tag, HolderGetter<Block> blocks) {
            BlockState current = NbtUtils.readBlockState(blocks, tag.getCompound("Current"));
            BlockState baseline = NbtUtils.readBlockState(blocks, tag.getCompound("Baseline"));
            TrackedDiff diff = new TrackedDiff(current, baseline, tag.getLong("Modified"));
            diff.decayStage = tag.getInt("Stage");
            return diff;
        }
    }
}
