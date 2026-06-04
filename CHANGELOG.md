# Changelog

All notable project changes will be documented here.

## Unreleased

### Added

- Added the initial Gradle version-profile foundation:
  - Dynamic active-profile loading from `gradle/version-profiles/*.properties`.
  - Supported profile `1.21.11` and candidate profiles `1.20-1.20.4`,
    `1.20.5-1.21.10`, and `26.1-26.2-pre-3`.
  - Active profile expansion for `fabric.mod.json` Minecraft/Java dependencies
    and client Mixin compatibility level.
  - `printVersionProfile`, `listVersionProfiles`, `buildAllVersions`, and
    `buildValidationVersions` Gradle tasks.
- Added the project documentation foundation for the upcoming multi-version
  compatibility pipeline:
  - Fresh-agent workflow and verification guidance in `AGENTS.md`.
  - Repo-facing history in `CHANGELOG.md`.
  - Migration roadmap and current checkpoint tracking in `TODO.md`.
  - Current and planned Minecraft compatibility notes in `COMPATIBILITY.md`.
  - Planned version-profile, smoke-test, Modrinth publishing, release-note, and
    compatibility-source documentation under `gradle/` and `src/compat/`.
- Documented that the current repo still targets Minecraft `1.21.11` only, and
  that the broader `1.20.x` through `26.2-pre-3` release lane must be earned by
  compile probes and launcher smoke tests before publishing.
- Documented the compatibility drift audit from Minecraft `1.20` through
  `26.2-pre-3`, including the proposed source compat groups and required shims
  for stat packets, advancement ids, registry keys, custom payload networking,
  client commands, server directory fallback, Java toolchains, and the `26.x`
  build lane.
- Added the first compatibility adapter layer:
  - Legacy `1.20-1.20.4` stat packet conversion and raw channel networking.
  - Modern typed payload overlays for `1.20.5-1.21.10` and `1.21.11`.
  - `26.x` overlays for `ClientCommands` and
    `PayloadTypeRegistry.clientboundPlay/serverboundPlay`.
  - Shared descriptor-safe helpers for advancement ids, registry keys, server
    paths, server op checks, and optional local seed migration.

### Changed

- Upgraded the Gradle wrapper to `9.4.0` so the `26.x` Loom `1.16` profile can
  configure.
- Configured Gradle Java toolchains from the active profile so Java 17, Java 21,
  and Java 25 profiles can compile in one validation matrix.
- Verified `buildValidationVersions` across supported `1.21.11` and candidate
  profiles `1.20-1.20.4`, `1.20.5-1.21.10`, and `26.1-26.2-pre-3`.
- Added release artifact collection and metadata verification:
  - `buildRelease` builds, collects, and verifies the active profile jar.
  - `buildAllVersions` and `buildValidationVersions` now collect release jars
    under `build/release/<profile_id>/`.
  - Packaged jar metadata checks cover expanded Minecraft/Java dependencies,
    Mixin compatibility level, icon path, mod id, version, Fabric API
    dependency, and required client mixins.
- Recorded Modrinth project id `rJCvFZKh` and documented the expected
  `MODRINTH_TOKEN` repository secret for future publishing automation.
- Replaced flat Minecraft/Fabric dependency properties with
  `minecraft_version_profile`, `supported_minecraft_version_profiles`, and
  `candidate_minecraft_version_profiles`.
- Linked the new project-process docs from `README.md` so future migration work
  can start from the repository instead of chat history.
- Tightened `TODO.md` and `AGENTS.md` around the compatibility drift audit:
  release profiles can be broader/narrower than source compat groups, this mod
  should not copy Inventory Sort's GUI-driven split points without compile
  evidence, and the next major step is the version-profile foundation.
- Matched the checkpoint/commit guidance to Inventory Sort: each major change
  should update docs, verify, and commit before the next major step.
- Corrected the profile plan so candidate release profiles align with source
  compatibility groups by default, splitting only when compile probes, runtime
  checks, dependency metadata, or smoke tests prove a broader jar cannot be
  published honestly.
- Clarified the core compatibility principle: prefer the fewest unique build
  artifacts possible, and split builds only when one jar cannot cover the
  combined range.

## 2.1.0 Current Baseline

### Current Behavior

- Tracks lifetime stat totals across worlds, servers, Realms, and deleted saves
  by persisting JSON under `config/lifetime-stat-tracker/`.
- Tracks per-world and per-server breakdowns, unique non-recipe advancements,
  additive stat deltas, and high-water snapshots for unmodded server aggregates.
- Provides client command roots `/lifetimestats` and `/lst`.
- Supports manual unmodded-server seeding through `/lst seed world <name>` and
  backup-backed data removal through `/lst remove world <name>` and `/lst clear`.
- Provides optional server-side world identity support through custom Fabric
  payloads and `/lstserver identity`.
- Includes `tools/rebuild_lifetime_stats.py` for rebuilding tracker JSON files
  from backed-up Minecraft world stats.

### Build

- Current Gradle setup builds one Fabric jar for Minecraft `1.21.11` with Java
  21, Fabric Loader `0.18.4`, Fabric API `0.140.2+1.21.11`, and Fabric Loom
  remap `1.14-SNAPSHOT`.
