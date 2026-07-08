package com.regrownoffline.decay;

import net.minecraft.world.level.block.Block;

import javax.annotation.Nullable;
import java.util.Map;

/**
 * Data-driven "which block decays into which" table, reloaded from the
 * {@code data/<namespace>/decay_chains/*.json} datapack directory by
 * {@link DecayChainReloadListener}.
 *
 * <p>Each chain is flattened into direct block-to-block transitions (e.g. a datapack chain
 * {@code stone_bricks -> cracked_stone_bricks -> mossy_stone_bricks} becomes the two entries
 * {@code stone_bricks -> cracked_stone_bricks} and {@code cracked_stone_bricks -> mossy_stone_bricks}).
 * If a block currently being decayed has no entry here, {@link com.regrownoffline.decay.DecayProcessor}
 * snaps it straight to its recorded baseline - so admin-authored chains only need to describe the
 * cosmetic intermediate stages; the final "revert to what was actually there" step always happens.
 */
public final class DecayChainRegistry {

    private static volatile Map<Block, Block> transitions = Map.of();

    static void set(Map<Block, Block> newTransitions) {
        transitions = Map.copyOf(newTransitions);
    }

    /** Returns the block {@code current} decays into next, or {@code null} if no chain covers it. */
    @Nullable
    public static Block nextBlock(Block current) {
        return transitions.get(current);
    }

    private DecayChainRegistry() {
    }
}
