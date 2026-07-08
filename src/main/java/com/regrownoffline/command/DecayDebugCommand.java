package com.regrownoffline.command;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.regrownoffline.data.ChunkDecayData;
import com.regrownoffline.worldgen.BaselineCache;
import com.regrownoffline.worldgen.BaselineGenerator;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.chunk.LevelChunk;

import java.util.Map;

/** {@code /decaydebug <show|reset> [x z]} - admin tooling for inspecting/resetting a chunk's decay state. */
public final class DecayDebugCommand {

    private static final int MAX_DIFFS_SHOWN = 20;

    private DecayDebugCommand() {
    }

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(Commands.literal("decaydebug")
                .requires(source -> source.hasPermission(2))
                .then(Commands.literal("show")
                        .executes(ctx -> show(ctx.getSource(), senderChunk(ctx.getSource())))
                        .then(Commands.argument("x", IntegerArgumentType.integer())
                                .then(Commands.argument("z", IntegerArgumentType.integer())
                                        .executes(ctx -> show(ctx.getSource(), new ChunkPos(
                                                IntegerArgumentType.getInteger(ctx, "x"),
                                                IntegerArgumentType.getInteger(ctx, "z")))))))
                .then(Commands.literal("reset")
                        .executes(ctx -> reset(ctx.getSource(), senderChunk(ctx.getSource())))
                        .then(Commands.argument("x", IntegerArgumentType.integer())
                                .then(Commands.argument("z", IntegerArgumentType.integer())
                                        .executes(ctx -> reset(ctx.getSource(), new ChunkPos(
                                                IntegerArgumentType.getInteger(ctx, "x"),
                                                IntegerArgumentType.getInteger(ctx, "z"))))))));
    }

    private static ChunkPos senderChunk(CommandSourceStack source) {
        return new ChunkPos(BlockPos.containing(source.getPosition()));
    }

    private static int show(CommandSourceStack source, ChunkPos pos) {
        ServerLevel level = source.getLevel();
        ChunkDecayData data = ChunkDecayData.get(level);
        ChunkDecayData.ChunkRecord record = data.getRecord(pos);

        if (record == null) {
            source.sendSuccess(() -> Component.literal("No decay data for chunk " + pos.x + ", " + pos.z), false);
            return 1;
        }

        long now = System.currentTimeMillis();
        double offlineHours = (now - record.getLastActivityTime()) / 3_600_000.0;
        source.sendSuccess(() -> Component.literal(String.format(
                "Chunk %d,%d - baseline:%s diffs:%d lastPlayer:%s offlineFor:%.1fh",
                pos.x, pos.z, record.isBaselineGenerated(), record.diffs().size(),
                record.getLastPlayer(), offlineHours)), false);

        int shown = 0;
        int total = record.diffs().size();
        for (Map.Entry<BlockPos, ChunkDecayData.TrackedDiff> entry : record.diffs().entrySet()) {
            if (shown >= MAX_DIFFS_SHOWN) {
                int remaining = total - shown;
                source.sendSuccess(() -> Component.literal("  ... (" + remaining + " more)"), false);
                break;
            }
            ChunkDecayData.TrackedDiff diff = entry.getValue();
            BlockPos blockPos = entry.getKey();
            source.sendSuccess(() -> Component.literal(String.format(
                    "  %s: %s -> %s (stage %d)",
                    blockPos.toShortString(),
                    BuiltInRegistries.BLOCK.getKey(diff.currentState().getBlock()),
                    BuiltInRegistries.BLOCK.getKey(diff.baselineState().getBlock()),
                    diff.decayStage())), false);
            shown++;
        }
        return 1;
    }

    private static int reset(CommandSourceStack source, ChunkPos pos) {
        ServerLevel level = source.getLevel();
        ChunkDecayData data = ChunkDecayData.get(level);
        data.removeRecord(pos);
        BaselineCache.remove(level, pos);

        LevelChunk chunk = level.getChunkSource().getChunkNow(pos.x, pos.z);
        if (chunk != null) {
            BaselineGenerator.generateAndDiff(level, chunk);
            source.sendSuccess(() -> Component.literal(
                    "Reset decay data for chunk " + pos.x + "," + pos.z + " and re-triggered baseline generation."), true);
        } else {
            source.sendSuccess(() -> Component.literal(
                    "Reset decay data for chunk " + pos.x + "," + pos.z + " (not currently loaded; baseline regenerates on next load)."), true);
        }
        return 1;
    }
}
