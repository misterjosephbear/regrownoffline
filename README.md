# Regrown Offline

A NeoForge 1.21.1 mod that makes player-built structures visually decay and eventually revert to
their natural, worldgen-baseline state when the players associated with that area have been offline
for an extended period.

## How it works

1. **Baseline generation** (`worldgen/BaselineGenerator.java`) - the first time a chunk loads, the
   mod regenerates that chunk's terrain in memory using the level's `ChunkGenerator`/`RandomState`
   (biomes, noise-based terrain, and the biome surface layer), off the main thread. It then diffs the
   live chunk against that baseline on the server thread and stores every differing block position.
2. **Persistence** (`data/ChunkDecayData.java`) - a `SavedData` attached to each `ServerLevel` stores,
   per chunk: whether its baseline has been generated, the last time a player was active there, the
   UUID of the last player who was active there, and for every diffed block: its current state, its
   baseline state, a last-modified timestamp, and its decay stage.
3. **New placements** (`event/PlacementListener.java`) - after the initial baseline diff, new player
   placements (`BlockEvent.EntityPlaceEvent`) are diffed individually against a per-chunk in-memory
   baseline cache (`worldgen/BaselineCache.java`), so we don't have to re-diff a whole chunk on every
   placement.
4. **Decay** (`decay/DecayProcessor.java` + `decay/DecayScheduler.java`) - once a chunk's last
   activity timestamp is older than the configured offline threshold, its diffed blocks become
   eligible to advance one decay stage at a time, at most `decayBlocksPerTick` blocks per dimension
   per tick, and only for chunks that are currently loaded. Each block walks the admin-defined decay
   chain (see below) for its current block type; once the chain runs out (or the block already
   matches its target), it snaps to its actual recorded baseline block.
5. **Admin tooling** (`command/DecayDebugCommand.java`) - `/decaydebug show [x z]` and
   `/decaydebug reset [x z]` for inspecting/clearing a chunk's tracked diffs.

Retroactive worlds are handled automatically: baseline generation and diffing run identically whether
a chunk is brand new or has pre-existing player builds - the first load after install just stamps
every discovered diff (and the chunk's activity clock) with the current time, so the offline countdown
starts from install/first-load rather than from whenever those blocks were originally placed.

## Configuration

`config/regrownoffline-common.toml` (generated on first run from `config/RegrownOfflineConfig.java`):

| Key | Default | Meaning |
|---|---|---|
| `decay.offlineDaysBeforeDecay` | `3` | Real-world days a chunk's players must be offline before it becomes decay-eligible. |
| `decay.decayBlocksPerTick` | `8` | Max blocks advanced one stage per server tick, per loaded dimension. |
| `decay.decayStageIntervalTicks` | `200` | Min ticks a block waits between stage advances once eligible (20 ticks = 1s). |
| `decay.playerActivityRadiusChunks` | `8` | Chunk radius around an online player that counts as "active", resetting the offline timer even without new placements. |

## Decay chain datapack

Per-block decay chains are data-driven JSON, reloadable via `/reload`, at:

```
data/<namespace>/decay_chains/<name>.json
```

```json
{
  "stages": [
    "minecraft:stone_bricks",
    "minecraft:cracked_stone_bricks",
    "minecraft:mossy_stone_bricks"
  ]
}
```

Each consecutive pair becomes a `block -> block` transition (`decay/DecayChainReloadListener.java`
flattens this into `decay/DecayChainRegistry.java`). A block with no chain entry - including the last
stage of a chain - snaps straight to whatever baseline block was actually recorded for that position,
so chains only need to describe the cosmetic intermediate stages; server admins don't need to know or
match the "correct" terminal block for every position. Example chains ship in
`src/main/resources/data/regrownoffline/decay_chains/`.

## Baseline generation scope

The regenerated baseline reproduces terrain shape (biomes + noise: stone/deepslate/water/air) and the
surface layer (grass/dirt/sand/etc.). It deliberately **does not** replay carvers (caves/ravines),
decorator features (trees, ore veins, vegetation), or structures - those steps read and write
neighboring chunks, and reproducing them deterministically for one isolated chunk would mean
reimplementing most of vanilla's multi-chunk chunk-generation pyramid. Practical effect: a player-dug
cave won't be filled back in, and a worldgen-generated village isn't treated as a player build.
Anything placed on top of raw terrain/surface - the overwhelming majority of what "decay" means here -
is still diffed correctly. See the doc comment on `BaselineGenerator` for details and where to extend
it if you need carvers/features included.

## Known limitations / things to verify before running this for real

This project was scaffolded and implemented in a sandboxed environment with **no network access to
`maven.neoforged.net`**, so none of the NeoForge/Minecraft dependencies could be downloaded and this
code has **not been compiled or run**. Everything here was written from API knowledge, not verified
against a compiler. Before relying on it:

1. Run `./gradlew build` (see "Building" below) and fix any compile errors. The highest-risk file by
   far is `worldgen/BaselineGenerator.java`, which calls internal `ChunkGenerator`/`ChunkStatus`
   generation-step methods - some of the most version-volatile APIs in the Minecraft codebase. Diff
   its calls against the real `net.minecraft.world.level.chunk.status.ChunkStatus` and
   `ChunkGenerator` source for your exact NeoForge build if it doesn't compile as-is.
2. Other moderate-risk spots, called out inline: the `NeoForge.EVENT_BUS`-dispatched event types
   (`ChunkEvent.Load/Unload`, `BlockEvent.EntityPlaceEvent`, `LevelTickEvent.Post`) and
   `NbtUtils.readBlockState`/`writeBlockState`. These are more stable than the worldgen internals but
   still worth a first-build sanity check.
3. `applyBaseline()` in `BaselineGenerator` does a full per-block scan of a newly-loaded chunk
   (skipping whole 16-block sections when both live and baseline are all-air) on the main thread.
   This is fine for normal play but could cause a hitch during a large retroactive scan burst (e.g. a
   player flying quickly through many never-loaded chunks on an old world right after install). If
   that turns out to matter in practice, the next step would be spreading the diff itself across ticks
   the same way `DecayProcessor` already spreads decay.

## Building

Gradle wrapper binaries are **not** checked in - generating them requires network access this sandbox
didn't have. Before building, run once (from a machine with normal internet access):

```
gradle wrapper --gradle-version 8.10.2
```

Then the usual NeoForge MDK tasks apply:

```
./gradlew build          # compile + build the mod jar
./gradlew runClient       # launch a dev client
./gradlew runServer       # launch a dev server
./gradlew runData         # run datagen
```

## Commands

- `/decaydebug show [x] [z]` - print baseline status, diff count, last player, and offline duration
  for a chunk (defaults to the chunk you're standing in), plus up to 20 individual tracked diffs.
- `/decaydebug reset [x] [z]` - clear all tracked decay data for a chunk and, if it's currently
  loaded, immediately re-trigger baseline generation. Requires permission level 2 (op).
