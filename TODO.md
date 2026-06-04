# Lifetime Stat Tracker TODO

Current checkpoint: Automated packaged-jar client smoke testing is implemented
for the supported profile, with candidate exact-runtime smoke records tracked as
pending

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
3. Version-profile foundation:
   - Added dynamic profile loading through `settings.gradle` and `build.gradle`.
   - Added active profile metadata expansion for `fabric.mod.json` and
     `lifetime-stat-tracker.client.mixins.json`.
   - Added profile property files for supported `1.21.11` and candidates
     `1.20-1.20.4`, `1.20.5-1.21.10`, and `26.1-26.2-pre-3`.
   - Added `printVersionProfile`, `listVersionProfiles`, `buildAllVersions`,
     and `buildValidationVersions` Gradle tasks.
   - Upgraded the Gradle wrapper to `9.4.0` so the `26.x` Loom `1.16` profile
     can configure.
   - Verified `.\gradlew.bat build --no-daemon --console=plain`,
     `.\gradlew.bat buildAllVersions --no-daemon --console=plain`, and
     `printVersionProfile` for all candidate profiles.
4. Compatibility adapter layer:
   - Added profile-selected compatibility overlays for stat packet extraction,
     custom world-identity networking, and client command factories.
   - Added descriptor-safe shared helpers for advancement ids, registry keys,
     server directory names, server op checks, and optional integrated-world
     seed migration.
   - Added Java toolchain wiring so Java 17, Java 21, and Java 25 profiles
     compile with the active profile's requested JDK.
   - Verified `.\gradlew.bat buildValidationVersions --no-daemon --console=plain`
     across `1.21.11`, `1.20-1.20.4`, `1.20.5-1.21.10`, and
     `26.1-26.2-pre-3`.
5. Release artifact collection and metadata verification:
   - Added `buildRelease`, `collectReleaseJar`, and `verifyReleaseMetadata`.
   - Profile matrix tasks now call `buildRelease`, so release jars are collected
     under `build/release/<profile_id>/` during supported and validation builds.
   - Metadata verification reads the packaged jar and checks expanded
     `fabric.mod.json`, Mixin compatibility level, mod id, version, Minecraft
     and Java dependencies, Fabric API dependency, icon path, and required
     client mixins.
   - Recorded Modrinth project id `rJCvFZKh`; publishing should use the
     repository secret `MODRINTH_TOKEN`.
   - Verified `.\gradlew.bat buildRelease --no-daemon --console=plain` and
     `.\gradlew.bat buildValidationVersions --no-daemon --console=plain`.
6. License update:
   - Replaced the prior license mismatch with `LGPL-3.0-or-later` across
     `LICENSE`, `LICENSE-GPL-3.0`, `README.md`, and `fabric.mod.json`.
   - Updated release metadata verification to require
     `LGPL-3.0-or-later` and both bundled license files in packaged jars.
   - Verified `.\gradlew.bat buildRelease --no-daemon --console=plain` and
     `.\gradlew.bat buildValidationVersions --no-daemon --console=plain`.
7. Smoke-test automation foundation:
   - Added a no-source `smokelaunch` subproject that launches exact Minecraft
     runtimes with the packaged release jar injected through `fabric.addMods`.
   - Added `LifetimeStatTrackerSmokeTest`, which arms only when the
     `lifetimestattracker.smokeTest` system property is set, force-loads
     `ClientPacketListener` and `ClientAdvancements`, waits for the client tick
     loop, logs `LIFETIMESTATTRACKER_SMOKE_TEST_PASS`, and closes the client.
   - Added exact smoke runtime profiles for every Minecraft version currently
     listed in release-profile `modrinth_game_versions`. These profiles are
     test harness inputs, not extra publishable release artifacts.
   - Added `gradle/smoke-tests.json`, `verifySmokeTestMatrix`,
     `smokeTestSupportedClients`, `smokeTestSelectedClients`,
     `smokeTestValidationClients`, `publishValidation`, and `ciValidation`.
   - Verified `.\gradlew.bat smokeTestSupportedClients --no-daemon --console=plain`
     for supported `1.21.11`; candidate rows remain `pending`.

## Current Compatibility Conclusion

The drift audit is good news for this mod: the compatibility surface is narrow
enough that we should target the fewest unique build artifacts possible.
Candidate release profiles should align with source overlays by default, with
splits added only when one jar literally cannot support the combined range.

Use this initial map for the profile implementation:

- Release profile `1.20-1.20.4` uses source compat group `1.20-1.20.4`.
- Release profile `1.20.5-1.21.10` uses source compat group
  `1.20.5-1.21.10`.
- Release profile `1.21.11` stays separate as the current supported baseline;
  descriptor-safe helpers now exist, so a later release-planning pass can decide
  whether it should collapse into a broader tested profile.
- Release profile `26.1-26.2-pre-3` uses source compat group `26.x`.

Do not over-split by copying Inventory Sort's GUI-driven groups unless compile
probes, binary runtime checks, dependency metadata, or smoke tests show a real
Lifetime Stat Tracker break that prevents one jar from covering the combined
range. The first implementation pass now compiles for all four release profiles,
and the supported `1.21.11` profile has passed automated packaged-jar client
smoke testing. Candidate exact-runtime launches are still pending evidence
gates.

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
| `1.20.5-1.21.10` | `1.20.5-1.21.10` | 21 | Modern typed payloads with `PayloadTypeRegistry.playS2C/playC2S`, `ClientCommandManager`, `ResourceLocation` registry descriptors, and a `File`/`Path` server-directory helper. |
| `1.21.11` | `1.21.11` | 21 | Same typed payload and command APIs as earlier `1.21.x`, but `Registry#getKey` and `AdvancementHolder#id` now return `Identifier`. May collapse after descriptor-safe helpers exist. |
| `26.x` | `26.1-26.2-pre-3` | 25 | Java 25/non-remap lane with `ClientCommands` and `PayloadTypeRegistry.clientboundPlay/serverboundPlay`. |

Only move a profile to supported/publishable after its packaged jar launches on
every exact Minecraft version listed in `modrinth_game_versions`.

## Migration Roadmap

1. Baseline hygiene:
   - Verify the current `1.21.11` build on the existing code.
   - Decide whether to clean up mojibake/non-ASCII chat glyphs in command output
     before or after compatibility work.
   - Confirm the current license and Modrinth/project metadata.
   - Current status: build verified, Modrinth project id recorded, and license
     metadata aligned to `LGPL-3.0-or-later`. Cleanup still TODO.
2. Version-profile foundation:
   - Add `gradle/version-profiles/*.properties`.
   - Add default, supported, and candidate profile properties.
   - Start with candidate release profiles aligned to source compat groups:
     `1.20-1.20.4`, `1.20.5-1.21.10`, `1.21.11`, and
     `26.1-26.2-pre-3`.
   - Split a candidate only when compile probes, binary runtime checks,
     dependency metadata, or smoke tests prove that one jar cannot cover the
     proposed profile range.
   - Expand `fabric.mod.json` with active profile Minecraft dependency and Java
     dependency metadata.
   - Expand the Mixin config compatibility level from the active Java target.
   - Current status: DONE for the initial supported/candidate profile map.
3. Build tasks and release artifact collection:
   - Add profile-aware Gradle task wiring.
   - Collect release jars under `build/release/<profile_id>/`.
   - Add metadata verification for mod id, version, Minecraft dependency, Java
     dependency, icon path, and Mixin compatibility level.
   - Current status: DONE for compile-level release jars. Runtime smoke gates
     still block publish promotion.
4. Compile probe matrix:
   - Probe exact runtimes first, using the Inventory Sort version metadata as a
     starting point.
   - Record failures by API surface rather than by raw compiler error only.
   - Split or merge profile groups based on evidence.
   - Current status: DONE for the current four-profile compile matrix via
     `buildValidationVersions`; supported `1.21.11` runtime smoke passed, and
     candidate exact-version runtime smoke probes remain TODO.
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
   - Current status: DONE for the current compile matrix. Keep adding only
     small adapters if future compile or smoke probes prove another split.
6. Java toolchains and CI:
   - Configure Java 17 for `1.20-1.20.4`, Java 21 for `1.20.5+` and `1.21.x`,
     and Java 25 for `26.x`.
   - Keep regular push/PR CI on a fast default-profile build.
   - Add a manual compatibility validation workflow for targeted profiles.
   - Current status: Gradle wrapper upgraded to `9.4.0`, profile Java release
     values are wired, Gradle toolchains are active locally, and local
     `ciValidation`/`publishValidation` task roots exist; GitHub Actions
     workflows still TODO.
7. Minecraft `26.x` build lane:
   - Reuse Inventory Sort's proven model where applicable: non-remapping Loom
     for `26.x`, normal dependencies, and plain jar artifacts.
   - Start with one candidate profile, `26.1-26.2-pre-3`, mapped to source
     compat group `26.x`.
   - Split to `26.1-26.1.2` and `26.2-pre-3` only if Fabric dependency metadata,
     compile anchors, or smoke tests prove that one jar cannot cover both.
   - Probe payload, command, packet, advancement, registry key, and server
     reflection APIs under `26.1.2` and `26.2-pre-3`.
   - Current status: profile config resolves under Loom `1.16`, Java 25
     toolchain compile passes, and source compatibility probes are green for
     the current `26.2-pre-3` compile anchor. Launcher smoke probes still TODO.
8. Smoke-test automation:
   - Add a minimal client smoke launcher that installs the packaged release jar,
     reaches the client tick loop, force-loads mixin targets, and exits with a
     clear pass marker.
   - Later add optional client-plus-server identity smoke coverage if practical.
   - Current status: DONE for the supported `1.21.11` client-only baseline.
     Candidate exact-runtime launches are tracked as `pending` in
     `gradle/smoke-tests.json`, and optional client-plus-server identity smoke
     coverage remains TODO.
9. Modrinth publishing:
   - Add guarded dry-run and real publish tasks after profiles, metadata checks,
     and smoke records exist.
   - Publish supported profiles only.
   - Use `gradle/release-notes/<mod_version>.md` for Modrinth changelogs.
   - Current status: project id `rJCvFZKh` is recorded, GitHub repository
     secret `MODRINTH_TOKEN` is expected, and `publishValidation` now gates the
     supported profile build plus smoke test; Modrinth upload/dry-run tasks
     still TODO.
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
4. Run `verifySmokeTestMatrix`.
5. Run smoke tests for every exact Minecraft version claimed by a profile.
6. Run `publishValidation` before preparing uploads.
7. Dry-run Modrinth publishing.
8. Publish through a guarded manual workflow only after review.
