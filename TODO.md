# Lifetime Stat Tracker TODO

Current checkpoint: Compatibility drift map complete; profile foundation is next

## Project Workflow

- After every major change, update `TODO.md`, update `CHANGELOG.md`, verify the
  relevant Gradle build/task, and commit before starting the next major step.
- If a session includes more than one major change, stop between major
  boundaries to update notes, verify, and commit each checkpoint separately.
- Keep user-facing release notes in `gradle/release-notes/<mod_version>.md`.
  Internal CI, shim, refactor, and docs details belong in this TODO and
  `CHANGELOG.md` unless they affect install, compatibility, commands, or visible
  behavior.

## Confirmed Current Shape

- Current source and metadata target Minecraft `1.21.11`.
- Build system is a compact single-project Fabric Loom setup with split main and
  client source sets.
- Client install is useful on singleplayer, LAN/integrated play, unmodded
  multiplayer, and Realms.
- Optional server install improves per-world server identity by sending a custom
  world identity payload to the client.
- Runtime persistence is under `config/lifetime-stat-tracker/`.
- Data files are `totals.json`, `snapshots.json`, `world_stats.json`, and
  `advancements.json`.
- Backup-backed destructive operations write under
  `config/lifetime-stat-tracker/backups/`.
- The helper script `tools/rebuild_lifetime_stats.py` can rebuild tracker JSON
  from backed-up Minecraft world `stats` and `advancements` files.

## Current Command Roots

- Client commands:
  - `/lifetimestats`
  - `/lst`
- Main client subcommands:
  - `/lst time`
  - `/lst worlds`
  - `/lst world <name>`
  - `/lst advancements`
  - `/lst current`
  - `/lst debug`
  - `/lst seed world <name>`
  - `/lst remove world <name>`
  - `/lst clear`
  - `/lst help`
- Optional server command:
  - `/lstserver identity`

## Recently Completed

1. Documentation handoff foundation:
   - Read the current Lifetime Stat Tracker repo and identified the core
     migration surfaces: Fabric split source sets, client stat packet mixins,
     advancement progress mixins, custom payload networking, client/server
     commands, JSON persistence, and the rebuild script.
   - Read the Inventory Sort documentation set and pulled over the reusable
     workflow shape: fresh-agent docs, compatibility-group profiles, candidate
     vs supported profiles, smoke-test gates, Modrinth release-note split, and
     guarded publishing.
   - Added repo-local docs for this mod without claiming support that has not
     yet been compile-probed or smoke-tested.
2. Compatibility drift audit:
   - Compared this mod's source touchpoints against Minecraft `1.20` through
     `26.2-pre-3` using local Minecraft jars, Fabric API module jars, and the
     Inventory Sort profile model.
   - Confirmed this mod's relevant breakpoints: `1.20.2` advancement map shape,
     `1.20.5` stat packet/networking and Java 21 boundary, `1.21` server
     directory return type, `1.21.11` `ResourceLocation` to `Identifier`
     descriptor change, and `26.x` Fabric command/payload registration rename.
   - Drafted the source compat group map in `COMPATIBILITY.md`.

## Current Compatibility Conclusion

The drift audit is good news for this mod: the compatibility surface is narrow
enough that release profiles and source overlays do not need to be one-to-one.

Use this initial map for the profile implementation:

- Release profile `1.20-1.20.4` uses source compat group `1.20-1.20.4`.
- Release profiles `1.20.5-1.20.6` and `1.21-1.21.10` can start on source
  compat group `1.20.5-1.21.10`.
- Release profile `1.21.11` stays separate until `Registry#getKey(...)` and
  advancement id extraction are descriptor-safe across `ResourceLocation` and
  `Identifier`.
- Release profiles `26.1-26.1.2` and `26.2-pre-3` can start on shared source
  compat group `26.x`.

Do not over-split by copying Inventory Sort's GUI-driven groups unless compile
probes show a real Lifetime Stat Tracker break. The first implementation pass
should add the profile system, then the adapter layer, then compile probes for
the groups above.

## Migration Goal

Take this repo from a targeted Minecraft `1.21.11` build to a single monorepo
that can build and publish compatibility-group jars from Minecraft `1.20.x`
through the current `26.x` target lane, following the Inventory Sort pipeline
where it applies.

This mod is simpler than Inventory Sort because it has one public jar and no
large GUI surface. It still has version-sensitive APIs around packets,
advancements, commands, networking, Java levels, and `26.x` build mechanics.

## Proposed Compatibility Groups

These are starting source compatibility groups, not supported releases:

| Source compat group | Release profiles using it | Java | Expected role |
| --- | --- | ---: | --- |
| `1.20-1.20.4` | `1.20-1.20.4` | 17 | Legacy stat packet accessor, legacy networking, no `RegistryFriendlyByteBuf`/`StreamCodec`; advancement key shape changes inside this range and needs a raw/reflection adapter. |
| `1.20.5-1.21.10` | `1.20.5-1.20.6`, `1.21-1.21.10` | 21 | Modern typed payloads with `PayloadTypeRegistry.playS2C/playC2S`, `ClientCommandManager`, `ResourceLocation` registry descriptors, and a `File`/`Path` server-directory helper. |
| `1.21.11` | `1.21.11` | 21 | Same typed payload and command APIs as earlier `1.21.x`, but `Registry#getKey` and `AdvancementHolder#id` now return `Identifier`. May collapse after descriptor-safe helpers exist. |
| `26.x` | `26.1-26.1.2`, `26.2-pre-3` | 25 | Java 25/non-remap lane with `ClientCommands` and `PayloadTypeRegistry.clientboundPlay/serverboundPlay`. |

Only move a profile to supported/publishable after its packaged jar launches on
every exact Minecraft version listed in `modrinth_game_versions`.

## Migration Roadmap

1. Baseline hygiene:
   - Verify the current `1.21.11` build on the existing code.
   - Decide whether to clean up mojibake/non-ASCII chat glyphs in command output
     before or after compatibility work.
   - Confirm the current license and Modrinth/project metadata.
   - Current status: TODO.
2. Version-profile foundation:
   - Add `gradle/version-profiles/*.properties`.
   - Add default, supported, and candidate profile properties.
   - Start with candidate release profiles `1.20-1.20.4`,
     `1.20.5-1.20.6`, `1.21-1.21.10`, `1.21.11`, `26.1-26.1.2`, and
     `26.2-pre-3`.
   - Map those release profiles to source compat groups `1.20-1.20.4`,
     `1.20.5-1.21.10`, `1.21.11`, and `26.x`.
   - Expand `fabric.mod.json` with active profile Minecraft dependency and Java
     dependency metadata.
   - Expand the Mixin config compatibility level from the active Java target.
   - Current status: TODO.
3. Build tasks and release artifact collection:
   - Add profile-aware Gradle task wiring.
   - Collect release jars under `build/release/<profile_id>/`.
   - Add metadata verification for mod id, version, Minecraft dependency, Java
     dependency, icon path, and Mixin compatibility level.
   - Current status: TODO.
4. Compile probe matrix:
   - Probe exact runtimes first, using the Inventory Sort version metadata as a
     starting point.
   - Record failures by API surface rather than by raw compiler error only.
   - Split or merge profile groups based on evidence.
   - Current status: drift audit complete; build-profile probes still TODO.
5. Compatibility shims:
   - Keep shared behavior in `src/main/java` and `src/client/java`.
   - Add `src/compat/<compat_group>/main/java` or
     `src/compat/<compat_group>/client/java` only for APIs that cannot compile
     across the full target range.
   - Prefer small adapter classes and target-specific mixins over copying
     `LifetimeStatsManager`.
   - Prioritize `StatsPacketCompat`, `AdvancementIdCompat`,
     `RegistryKeyCompat`, `NetworkPayloadCompat`, `ClientCommandCompat`, and
     `ServerPathCompat`.
   - Current status: TODO.
6. Java toolchains and CI:
   - Configure Java 17 for `1.20-1.20.4`, Java 21 for `1.20.5+` and `1.21.x`,
     and Java 25 for `26.x`.
   - Keep regular push/PR CI on a fast default-profile build.
   - Add a manual compatibility validation workflow for targeted profiles.
   - Current status: TODO.
7. Minecraft `26.x` build lane:
   - Reuse Inventory Sort's proven model where applicable: non-remapping Loom
     for `26.x`, normal dependencies, and plain jar artifacts.
   - Use `26.1.2` as the compile anchor for grouped `26.1-26.1.2` release
     metadata, with `26.1` and `26.1.1` as exact smoke runtimes.
   - Keep `26.2-pre-3` exact until newer `26.2` release metadata proves a
     broader range.
   - Probe payload, command, packet, advancement, registry key, and server
     reflection APIs under `26.1.2` and `26.2-pre-3`.
   - Current status: TODO.
8. Smoke-test automation:
   - Add a minimal client smoke launcher that installs the packaged release jar,
     reaches the client tick loop, force-loads mixin targets, and exits with a
     clear pass marker.
   - Later add optional client-plus-server identity smoke coverage if practical.
   - Current status: TODO.
9. Modrinth publishing:
   - Add guarded dry-run and real publish tasks after profiles, metadata checks,
     and smoke records exist.
   - Publish supported profiles only.
   - Use `gradle/release-notes/<mod_version>.md` for Modrinth changelogs.
   - Current status: TODO.
10. Release promotion:
   - Keep new groups in `candidate_minecraft_version_profiles` until compile and
     smoke testing pass.
   - Promote only smoke-passed groups into `supported_minecraft_version_profiles`.
   - Run the guarded publish workflow when release approval is given.
   - Current status: TODO.

## Compatibility Risk Surfaces

- `ClientPacketListenerMixin` injects into `ClientPacketListener.handleAwardStats`
  and currently reads `ClientboundAwardStatsPacket.stats()`. `1.20-1.20.4`
  requires `getStats()` handling.
- `ClientAdvancementsMixin` shadows `ClientAdvancements.progress` as
  `Map<AdvancementHolder, AdvancementProgress>`. `1.20-1.20.1` uses
  `Advancement` keys, and `1.21.11+` changes the `id()` return descriptor.
- `LifetimeStatsManager` serializes stat keys through built-in registries.
  `Registry#getKey` returns `ResourceLocation` through `1.21.10` and
  `Identifier` in `1.21.11+`, so direct calls need a descriptor-safe helper.
- `LifetimeStatsManager.requestStatsNow` sends
  `ServerboundClientCommandPacket.Action.REQUEST_STATS`.
- `LifetimeStatTrackerNetworking` uses `CustomPacketPayload`,
  `RegistryFriendlyByteBuf`, `PayloadTypeRegistry`, and `StreamCodec`.
  `1.20-1.20.4` needs legacy channel networking; `26.x` needs renamed Fabric
  payload registry methods.
- Client command registration uses `ClientCommandManager`, which becomes
  `ClientCommands` in `26.x`.
- Server identity detection reflects `MinecraftServer.storageSource` and calls
  `getLevelId`; this was still present in the inspected range, but remains a
  smoke-test surface.
- Server directory fallback must normalize `MinecraftServer#getServerDirectory`
  returning `File` in `1.20-1.20.6` and `Path` in `1.21+`.
- `fabric.mod.json` currently declares `minecraft: ~1.21.11`,
  `fabricloader >=0.18.4`, `java >=21`, and a wildcard Fabric API dependency.
- `lifetime-stat-tracker.client.mixins.json` currently hardcodes
  `compatibilityLevel: JAVA_21`, which must become profile-driven for Java 17
  and Java 25 lanes.

## Backlog

- Add tests or validation for JSON repair behavior before changing persistence.
- Decide whether the rebuild script should be covered by a small fixture-based
  Python test.
- Document any data migration if handle formats change.
- Add release artifact verification before publishing.
- Keep README command documentation aligned with actual client/server command
  roots after any compatibility shims.

## Release Process

Current baseline:

1. Run `.\gradlew.bat build --no-daemon --console=plain`.
2. Inspect `build/libs/`.
3. Update `README.md`, `TODO.md`, and `CHANGELOG.md` for the completed scope.

After the pipeline exists:

1. Run the default-profile build for normal development.
2. Run targeted profile builds for any touched compatibility group.
3. Run `buildAllVersions` before moving candidate groups toward support.
4. Run smoke tests for every exact Minecraft version claimed by a profile.
5. Dry-run Modrinth publishing.
6. Publish through a guarded manual workflow only after review.
