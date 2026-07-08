package com.regrownoffline.event;

import com.regrownoffline.config.RegrownOfflineConfig;
import com.regrownoffline.data.ChunkDecayData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.ChunkPos;
import net.neoforged.neoforge.event.tick.LevelTickEvent;

/**
 * Periodically refreshes {@link ChunkDecayData.ChunkRecord#getLastActivityTime()} for every tracked
 * chunk within {@code playerActivityRadiusChunks} of an online player, even if that player never
 * places a block. Without this, a player who logs in and simply stands in/explores a chunk (without
 * placing anything) would still have it start decaying under them once the offline threshold from
 * their *last placement* elapsed, which is not the intent of "offline for extended periods".
 */
public final class PlayerActivityListener {

    private static final int TOUCH_INTERVAL_TICKS = 200; // ~10s at 20 TPS

    private PlayerActivityListener() {
    }

    public static void onLevelTick(LevelTickEvent.Post event) {
        if (!(event.getLevel() instanceof ServerLevel level)) {
            return;
        }
        if (level.getGameTime() % TOUCH_INTERVAL_TICKS != 0) {
            return;
        }
        if (level.players().isEmpty()) {
            return;
        }

        int radius = RegrownOfflineConfig.PLAYER_ACTIVITY_RADIUS_CHUNKS.get();
        long now = System.currentTimeMillis();
        ChunkDecayData data = ChunkDecayData.get(level);

        for (ServerPlayer player : level.players()) {
            ChunkPos center = player.chunkPosition();
            for (int dx = -radius; dx <= radius; dx++) {
                for (int dz = -radius; dz <= radius; dz++) {
                    ChunkDecayData.ChunkRecord record = data.getRecord(new ChunkPos(center.x + dx, center.z + dz));
                    if (record != null) {
                        record.touch(now, player.getUUID());
                    }
                }
            }
        }
    }
}
