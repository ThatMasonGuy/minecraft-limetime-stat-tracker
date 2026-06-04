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
- Added automated packaged-jar client smoke testing:
  - A no-source `smokelaunch` subproject launches exact Minecraft runtimes with
    the release jar injected through `fabric.addMods`.
  - `LifetimeStatTrackerSmokeTest` force-loads `ClientPacketListener` and
    `ClientAdvancements`, waits for the client tick loop, prints
    `LIFETIMESTATTRACKER_SMOKE_TEST_PASS`, and exits cleanly.
  - `verifySmokeTestMatrix`, `smokeTestSupportedClients`,
    `smokeTestSelectedClients`, `smokeTestValidationClients`,
    `publishValidation`, and `ciValidation` now exist.
  - Exact smoke runtime profiles cover every Minecraft version listed by the
    current supported and candidate release profiles without adding extra
    publishable release artifacts.
- Added dedicated-server packaged-jar smoke testing:
  - `LifetimeStatTrackerServerSmokeTest` launches only when the smoke-test
    system property is set, waits for the server tick loop, verifies
    `/lstserver` command registration, resolves the server world identity path,
    prints `LIFETIMESTATTRACKER_SERVER_SMOKE_TEST_PASS`, and stops the server.
  - Added `lifetime-stat-tracker-server-only`, `smokeTestSupportedServers`,
    `smokeTestValidationServers`, `smokeTestSelectedServers`, and aggregate
    `smokeTestSupported`, `smokeTestValidation`, and `smokeTestSelected` task
    roots.
  - Smoke tasks now capture `build/smoke-logs/` and require the expected pass
    marker instead of trusting process exit alone.
- Added guarded Modrinth publishing automation:
  - `prepareModrinthUploads` writes `build/modrinth/upload-plan.json`.
  - `publishModrinthDryRun` runs the full supported-profile build, smoke, and
    upload-plan path without calling the Modrinth API.
  - `publishModrinth` requires `-Pmodrinth_confirm_publish=true` and reads
    `MODRINTH_TOKEN` or `-Pmodrinth_token`.
  - `.github/workflows/modrinth-publish.yml` provides the manual dry-run or real
    publish path using the repository `MODRINTH_TOKEN` secret.
  - Added `gradle/release-notes/2.1.0.md` for the first Modrinth upload.
  - Added `gradle/release-notes/2.7.0.md` for the first live GitHub Actions
    publish test.
- Added a manual GitHub Actions `candidate smoke validation` workflow that can
  run the full supported-plus-candidate launcher smoke matrix or a focused
  profile/game-version subset.
- Added `gradle/modrinth-project-pages.md` as source-of-truth copy for the
  Modrinth project summary and description page.

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
- Recorded Fabric API's Modrinth project id `P7dR8mSH` so upload plans declare
  it as a required dependency.
- Bumped the Modrinth publish test version to `2.7.0`.
- Published `2.7.0` through the guarded GitHub Actions Modrinth workflow as
  Modrinth version `s24DbwkA`.
- Installed Java 17, Java 21, and Java 25 in the build, candidate smoke, and
  Modrinth publish workflows so GitHub can run the full profile matrix.
- Ran GitHub Actions candidate smoke run `26934205756`; all listed exact
  runtimes passed except `26.2-pre-3`, which exposed a Fabric Loader prerelease
  metadata mismatch rather than a source compatibility split.
- Updated the `26.1-26.2-pre-3` Minecraft dependency range to include Fabric
  Loader's `26.2-pre.3` runtime string while keeping Modrinth game version
  labels as `26.2-pre-3`.
- Ran focused GitHub Actions candidate smoke run `26935246770`, which passed
  `26.2-pre-3` after the prerelease dependency metadata fix.
- Promoted `1.20-1.20.4`, `1.20.5-1.21.10`, and `26.1-26.2-pre-3` from
  candidate to supported after every listed exact runtime had a passing
  packaged-jar client smoke record.
- Updated the user-facing README and `gradle/release-notes/2.7.0.md` for the
  full `1.20` through `26.2-pre-3` compatibility-group release.
- Published the promoted `2.7.0+mc...` compatibility-group Modrinth versions
  through GitHub Actions run `26935626612`:
  - `2.7.0+mc1.20-1.20.4` as Modrinth version `Jt9tTBaY`.
  - `2.7.0+mc1.20.5-1.21.10` as Modrinth version `W2Sk9t0L`.
  - `2.7.0+mc1.21.11` as Modrinth version `PErEJCCl`.
  - `2.7.0+mc26.1-26.2-pre-3` as Modrinth version `ZUmFCvMh`.
- Updated the Modrinth project-page copy and Fabric metadata summary to avoid
  overstating server-side behavior; client-only installs use safe server
  aggregates, while accurate reset/multi-world Fabric server tracking needs the
  optional server install.
- Updated the live Lifetime Stat Tracker Modrinth project summary and
  description page through the Modrinth API, with readback verification and
  ignored before/after snapshots under `build/modrinth/`.
- Documented the release checkpoint workflow for annotated `v<mod_version>` Git
  tags and one GitHub Release per mod version after successful Modrinth publish.
- Backfilled the `v2.7.0` release checkpoint to the all-profile publish commit
  from GitHub Actions run `26935626612`.
- Wired `ciValidation`, `publishValidation`, the manual candidate smoke workflow,
  and the Modrinth publish workflow artifact capture through the aggregate
  client-plus-server smoke gates.
- Ran focused GitHub Actions candidate smoke validation run `26942827630`, which
  passed the `1.21.11` `lifetime-stat-tracker-server-only` path and printed the
  dedicated-server pass marker.
- Changed the project license metadata and bundled license files to
  `LGPL-3.0-or-later`, replacing the previous README/fabric metadata versus
  `LICENSE` mismatch.
- Recorded the supported `1.21.11` smoke launch as passing in
  `gradle/smoke-tests.json`; candidate smoke records remain pending and
  non-publishable.
- Wired `check` to `verifySmokeTestMatrix`, so supported profiles must keep
  passing smoke records while candidates can remain tracked as pending.
- Replaced the packaged mod icon with the new Lifetime Stat Tracker book/stat
  artwork.
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
