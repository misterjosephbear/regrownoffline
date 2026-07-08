package com.regrownoffline.decay;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.regrownoffline.RegrownOffline;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.server.packs.resources.SimpleJsonResourceReloadListener;
import net.minecraft.util.profiling.ProfilerFiller;
import net.minecraft.world.level.block.Block;

import java.util.HashMap;
import java.util.Map;

/**
 * Loads per-block decay chain definitions from
 * {@code data/<namespace>/decay_chains/*.json}, e.g.:
 * <pre>{@code
 * {
 *   "stages": [
 *     "minecraft:stone_bricks",
 *     "minecraft:cracked_stone_bricks",
 *     "minecraft:mossy_stone_bricks"
 *   ]
 * }
 * }</pre>
 * Reacts to {@code /reload} like any other datapack reload listener, so admins can tweak decay
 * chains without restarting the server.
 */
public final class DecayChainReloadListener extends SimpleJsonResourceReloadListener {

    private static final Gson GSON = new Gson();

    public DecayChainReloadListener() {
        super(GSON, "decay_chains");
    }

    @Override
    protected void apply(Map<ResourceLocation, JsonElement> resources, ResourceManager resourceManager, ProfilerFiller profiler) {
        Map<Block, Block> transitions = new HashMap<>();
        int fileCount = 0;

        for (Map.Entry<ResourceLocation, JsonElement> entry : resources.entrySet()) {
            try {
                JsonObject root = entry.getValue().getAsJsonObject();
                JsonArray stages = root.getAsJsonArray("stages");
                if (stages == null || stages.size() < 2) {
                    RegrownOffline.LOGGER.warn("Decay chain {} needs at least 2 'stages' entries, skipping", entry.getKey());
                    continue;
                }

                Block previous = null;
                for (JsonElement stageElement : stages) {
                    ResourceLocation blockId = ResourceLocation.parse(stageElement.getAsString());
                    Block block = BuiltInRegistries.BLOCK.getOptional(blockId)
                            .orElseThrow(() -> new IllegalArgumentException("Unknown block " + blockId));
                    if (previous != null) {
                        transitions.put(previous, block);
                    }
                    previous = block;
                }
                fileCount++;
            } catch (Exception e) {
                RegrownOffline.LOGGER.error("Failed to parse decay chain {}: {}", entry.getKey(), e.getMessage());
            }
        }

        DecayChainRegistry.set(transitions);
        RegrownOffline.LOGGER.info("Loaded {} decay chain transition(s) from {} file(s)", transitions.size(), fileCount);
    }
}
