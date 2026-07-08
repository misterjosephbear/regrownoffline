package com.regrownoffline.worldgen;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.block.state.BlockState;

import javax.annotation.Nullable;
import java.util.Map;
import java.util.WeakHashMap;
import java.util.concurrent.ConcurrentHashMap;

/**
 * In-memory-only cache of the generated baseline chunk for each currently loaded chunk.
 *
 * <p>The persisted {@code ChunkDecayData} only stores positions that differ from baseline, not the
 * full baseline itself (storing every block of every chunk would bloat the save file enormously).
 * That means when a player places a brand-new block after the initial diff pass, we need somewhere
 * to look up "what was here originally" without re-running the (expensive) baseline generation.
 * This cache holds that data for as long as the chunk stays loaded; it is evicted on chunk unload
 * and is safe to lose entirely (e.g. on server restart) since it can always be recomputed from the
 * seed and {@code ChunkGenerator} on demand.
 */
public final class BaselineCache {

    private static final Map<ServerLevel, Map<Long, ChunkAccess>> CACHE = new WeakHashMap<>();

    static void put(ServerLevel level, ChunkPos pos, ChunkAccess baseline) {
        synchronized (CACHE) {
            CACHE.computeIfAbsent(level, l -> new ConcurrentHashMap<>()).put(pos.toLong(), baseline);
        }
    }

    public static boolean contains(ServerLevel level, ChunkPos pos) {
        Map<Long, ChunkAccess> levelCache;
        synchronized (CACHE) {
            levelCache = CACHE.get(level);
        }
        return levelCache != null && levelCache.containsKey(pos.toLong());
    }

    @Nullable
    public static BlockState get(ServerLevel level, BlockPos pos) {
        Map<Long, ChunkAccess> levelCache;
        synchronized (CACHE) {
            levelCache = CACHE.get(level);
        }
        if (levelCache == null) {
            return null;
        }
        ChunkAccess chunk = levelCache.get(new ChunkPos(pos).toLong());
        return chunk == null ? null : chunk.getBlockState(pos);
    }

    public static void remove(ServerLevel level, ChunkPos pos) {
        Map<Long, ChunkAccess> levelCache;
        synchronized (CACHE) {
            levelCache = CACHE.get(level);
        }
        if (levelCache != null) {
            levelCache.remove(pos.toLong());
        }
    }

    private BaselineCache() {
    }
}
