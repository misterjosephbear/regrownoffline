package com.regrownoffline.config;

import net.neoforged.neoforge.common.ModConfigSpec;

/** Common (server-authoritative) config: {@code config/regrownoffline-common.toml}. */
public final class RegrownOfflineConfig {

    public static final ModConfigSpec SPEC;

    public static final ModConfigSpec.IntValue OFFLINE_DAYS_BEFORE_DECAY;
    public static final ModConfigSpec.IntValue DECAY_BLOCKS_PER_TICK;
    public static final ModConfigSpec.IntValue DECAY_STAGE_INTERVAL_TICKS;
    public static final ModConfigSpec.IntValue PLAYER_ACTIVITY_RADIUS_CHUNKS;

    static {
        ModConfigSpec.Builder builder = new ModConfigSpec.Builder();

        builder.push("decay");

        OFFLINE_DAYS_BEFORE_DECAY = builder
                .comment("Number of real-world days a chunk's associated players must be offline (and not merely nearby",
                        "while online) before that chunk becomes eligible to start decaying.")
                .defineInRange("offlineDaysBeforeDecay", 3, 0, 3650);

        DECAY_BLOCKS_PER_TICK = builder
                .comment("Maximum number of blocks advanced one decay stage per server tick, per loaded dimension.",
                        "Higher values decay faster but risk lag spikes.")
                .defineInRange("decayBlocksPerTick", 8, 1, 4096);

        DECAY_STAGE_INTERVAL_TICKS = builder
                .comment("Minimum number of ticks a block waits after its last change before it may advance to its",
                        "next decay stage, once its chunk is offline-eligible. 20 ticks = 1 second.")
                .defineInRange("decayStageIntervalTicks", 200, 1, Integer.MAX_VALUE);

        PLAYER_ACTIVITY_RADIUS_CHUNKS = builder
                .comment("Radius in chunks around an online player within which chunks are considered actively",
                        "watched, resetting their offline timer even without new placements.")
                .defineInRange("playerActivityRadiusChunks", 8, 1, 64);

        builder.pop();

        SPEC = builder.build();
    }

    private RegrownOfflineConfig() {
    }
}
