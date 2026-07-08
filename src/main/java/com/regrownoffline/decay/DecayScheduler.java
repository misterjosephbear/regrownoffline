package com.regrownoffline.decay;

import net.minecraft.server.level.ServerLevel;
import net.neoforged.neoforge.event.tick.LevelTickEvent;

/** Hooks decay processing into the server tick loop, once per loaded dimension per tick. */
public final class DecayScheduler {

    private DecayScheduler() {
    }

    public static void onLevelTick(LevelTickEvent.Post event) {
        if (event.getLevel() instanceof ServerLevel level) {
            DecayProcessor.tick(level);
        }
    }
}
