# Lifetime Stat Tracker TODO

Current checkpoint: `2.8.1` patch prep is implemented and locally verified. It
moves runtime persistence from the standalone Lifetime Stat Tracker app-data
folder into the shared Tempest Studios app-data namespace. Actual JSON files are
scoped under `profiles/<player profile>/`, so the same Minecraft account keeps
one running total across launchers and instances without merging different
player profiles. Singleplayer local-world handles include the active game
directory namespace so same-named worlds from different instances do not
collide. First-run migration copies unnamespaced shared data, then
instance-scoped `2.8.1` pre-release test data, then existing `2.8.0` app-data,
then older launcher-local `.minecraft/config/lifetime-stat-tracker/` data into
the active player-profile namespace when that namespace is empty. Each legacy
source is auto-imported only once. Verification passed with `git diff --check`
and `.\gradlew.bat buildAllVersions --no-daemon --console=plain`.

Last published release: GitHub Actions published `2.8.0` for every supported
compatibility-group profile from Minecraft `1.20` through `26.2-pre-3`.
Workflow run `27083542931` passed both client and dedicated-server packaged-jar
smoke launches for every exact runtime listed by the four supported profiles
before upload. The release is live on Modrinth as version ids `V3VoLsSk`,
`dzqk8kHN`, `CbC3i6YC`, and `Jqbl1MUu`. Annotated Git tag `v2.8.0` and the
matching GitHub Release point at publish source commit
`0856f289588256b2a68011c1eb3b64319bf2d96f`.

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
- Runtime persistence is under a shared per-user Tempest Studios data directory
  outside `.minecraft`: `%APPDATA%\TempestStudios\Lifetime-Stat-Tracker\` on
  Windows,
  `~/Library/Application Support/TempestStudios/Lifetime-Stat-Tracker/` on
  macOS, and `$XDG_DATA_HOME/tempest-studios/lifetime-stat-tracker/` or
  `~/.local/share/tempest-studios/lifetime-stat-tracker/` on Linux.
- Active JSON files live under `<shared root>/profiles/<player profile>/`, where
  the profile namespace is based on the current Minecraft profile id when
  available.
- Singleplayer local-world handles include the active Fabric game directory
  namespace, preventing same-named worlds from different instances from sharing
  one world snapshot while keeping the player profile's lifetime total merged
  across instances.
- Data files are `totals.json`, `snapshots.json`, `world_stats.json`, and
  `advancements.json`.
- Backup-backed destructive operations write under
  `<data folder>/backups/`.
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
     `printVersionProfile` for the initial validation profiles.
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
     for supported `1.21.11`; broader profile smoke proof was later completed
     in GitHub Actions.
8. Modrinth publishing automation:
   - Added `prepareModrinthUploads`, `publishModrinthDryRun`, and
     `publishModrinth`.
   - Added `.github/workflows/modrinth-publish.yml`, using the repository
     secret `MODRINTH_TOKEN` for real uploads and `xvfb` for CI smoke launches.
   - Added Modrinth release notes for `2.1.0`.
   - Added Modrinth release notes for `2.7.0` and bumped `mod_version` for the
     first live GitHub Actions publish test.
   - Added Fabric API's Modrinth project id `P7dR8mSH` as a required upload
     dependency.
   - Verified `.\gradlew.bat publishModrinthDryRun --no-daemon --console=plain`.
   - Pushed commit `ecb72d6` and ran GitHub Actions workflow
     `modrinth publish` with `dry_run=false`, publishing `2.7.0` as Modrinth
     version `s24DbwkA`.
9. Candidate smoke workflow and first evidence run:
   - Added the manual GitHub Actions `candidate smoke validation` workflow for
     full or filtered candidate launcher smoke runs.
   - Installed Java 17, Java 21, and Java 25 in the build and Modrinth publish
     workflows so GitHub can build every active profile.
   - Ran GitHub Actions candidate smoke run `26934205756`. It passed the
     packaged client-only launch for `1.21.11`, every `1.20-1.20.4` runtime,
     every `1.20.5-1.21.10` runtime, and `26.1`, `26.1.1`, and `26.1.2`.
   - The only failed runtime was `26.2-pre-3`, where Fabric Loader reported the
     runtime as `26.2-pre.3` and rejected the broader profile's
     `minecraft_dependency=>=26.1 <=26.2-` metadata.
   - Updated the `26.1-26.2-pre-3` and exact `26.2-pre-3` smoke profile
     dependency metadata to use Fabric Loader's `26.2-pre.3` runtime string
     while keeping Modrinth game version labels as `26.2-pre-3`.
   - Ran focused GitHub Actions candidate smoke run `26935246770`, which passed
     `26.2-pre-3` after the prerelease dependency metadata fix.
10. Release promotion:
   - Updated `gradle/smoke-tests.json` so every exact Minecraft version listed
     by the four release profiles has a passing packaged-jar client smoke
     record.
   - Promoted `1.20-1.20.4`, `1.20.5-1.21.10`, and `26.1-26.2-pre-3` from
     candidate to supported, leaving `candidate_minecraft_version_profiles`
     empty.
   - Updated `README.md`, `COMPATIBILITY.md`, `gradle/version-profiles/README.md`,
     `gradle/smoke-tests.md`, and `gradle/release-notes/2.7.0.md` for the
     supported `1.20` through `26.2-pre-3` release shape.
11. All-profile Modrinth publish:
   - Ran GitHub Actions `modrinth publish` run `26935626612` from commit
     `30fb089` with `dry_run=false`, `version_type=release`, and
     `requested_status=listed`.
   - The workflow repeated packaged client-only smoke launches for every exact
     Minecraft runtime listed by the four supported profiles before uploading.
   - Published the compatibility-group Modrinth versions:
     - `2.7.0+mc1.20-1.20.4` as version `Jt9tTBaY`.
     - `2.7.0+mc1.20.5-1.21.10` as version `W2Sk9t0L`.
     - `2.7.0+mc1.21.11` as version `PErEJCCl`.
     - `2.7.0+mc26.1-26.2-pre-3` as version `ZUmFCvMh`.
   - The earlier unsuffixed `2.7.0` publish for `1.21.11` remains Modrinth
     version `s24DbwkA`.
12. Modrinth project page copy:
   - Added `gradle/modrinth-project-pages.md` as the source-of-truth file for
     the Lifetime Stat Tracker project summary and description page.
   - Rewrote the page copy to lead with personal lifetime stat history and to
     describe server behavior accurately: client-only installs use safe server
     aggregates, while accurate per-world tracking on reset, multi-world, or
     proxy-routed Fabric servers needs the server-side install.
   - Updated `AGENTS.md`, `README.md`, and `gradle/modrinth-publishing.md` so
     future agents know project-page metadata is separate from version uploads.
   - Updated `fabric.mod.json` to use the same short summary as the Modrinth
     source copy.
   - Updated the live Lifetime Stat Tracker Modrinth project summary and
     description page through the Modrinth API, with readback verification.
   - Saved before/after API snapshots under ignored `build/modrinth/` artifacts.
13. Git tag and GitHub Release process:
   - Documented that successful real Modrinth publishes must be followed by an
     annotated `v<mod_version>` tag on the exact publish workflow commit.
   - Documented that GitHub Releases are one release page per `mod_version`,
     linking to Modrinth rather than replacing Modrinth as the primary download
     surface.
   - Backfilled the `v2.7.0` release checkpoint against publish workflow commit
     `30fb089`, the `headSha` for successful GitHub Actions run `26935626612`.
14. Dedicated server smoke pipeline:
   - Added `LifetimeStatTrackerServerSmokeTest`, armed only by the smoke-test
     system property, which waits for the dedicated server tick loop, verifies
     `/lstserver` is registered, resolves the advertised world identity through
     the same server code path, prints
     `LIFETIMESTATTRACKER_SERVER_SMOKE_TEST_PASS`, and stops the server.
   - Added `lifetime-stat-tracker-server-only` smoke install set, server smoke
     task roots, aggregate client-plus-server smoke roots, isolated server run
     directories, EULA/server.properties setup, smoke logs, and mandatory pass
     marker checks for both client and server smoke tasks.
   - Wired `ciValidation`, `publishValidation`, the manual candidate smoke
     workflow, and the Modrinth publish workflow artifact capture through the
     aggregate smoke gates.
   - Verified focused `1.21.11` server smoke, focused `1.21.11` client smoke,
     workflow-shaped filtered `smokeTestSelected` server smoke, and
     `buildValidationVersions` across all supported profiles.
   - Ran focused GitHub Actions candidate smoke validation run `26942827630` from
     commit `ca7967d`; the filtered `1.21.11` `lifetime-stat-tracker-server-only`
     run printed `LIFETIMESTATTRACKER_SERVER_SMOKE_TEST_PASS`.
   - Remaining future hardening: add a true client-to-dedicated-server handshake
     smoke if we decide the heavier local two-process test is worth the runtime.
15. `2.7.1` validation-refresh release:
   - Bumped `mod_version` to `2.7.1`.
   - Added `gradle/release-notes/2.7.1.md`, focused on the upgraded
     dedicated-server release validation rather than code or data changes.
   - Ran guarded live Modrinth publish workflow `26943407860` from commit
     `ea929a91e23fdaafbbeea19733a3136ad1a06b10`.
   - The workflow passed client and dedicated-server smoke launches for every
     exact runtime listed by the supported profiles before upload.
   - Published the compatibility-group Modrinth versions:
     - `2.7.1+mc1.20-1.20.4` as Modrinth version `qYHIQ8Sd`.
     - `2.7.1+mc1.20.5-1.21.10` as Modrinth version `n6br1EMY`.
     - `2.7.1+mc1.21.11` as Modrinth version `zgxySlhY`.
     - `2.7.1+mc26.1-26.2-pre-3` as Modrinth version `b0rtBSzi`.
   - Created annotated tag `v2.7.1` and GitHub Release
     `Lifetime Stat Tracker 2.7.1`, both pointing at the publish source commit.
16. Launcher-agnostic data storage:
   - Bumped the development version to `2.8.0`.
   - Changed runtime persistence from Fabric's launcher-local config directory
     to a fixed per-user app-data directory outside `.minecraft`.
   - Added guarded first-run migration from
     `.minecraft/config/lifetime-stat-tracker/` into the app-data directory
     when the app-data directory does not already contain tracker data.
   - Left legacy launcher-local files untouched during migration to avoid data
     loss, and skipped automatic import when global data already exists to avoid
     accidental duplicate counting from another launcher folder.
   - Verified `git diff --check` and
     `.\gradlew.bat buildAllVersions --no-daemon --console=plain`.
17. Modrinth project-page install note update:
   - Updated `gradle/modrinth-project-pages.md` so the Install note explains
     that the mod reads client stat packets, writes JSON to the fixed app-data
     folder outside `.minecraft`, shares data across launchers, and copies
     legacy launcher-local data only when the app-data folder is empty.
   - Updated the live Modrinth project page through the Modrinth API with
     readback verification.
   - Saved before/after API snapshots under ignored `build/modrinth/` artifacts.
18. `2.8.0` launcher-agnostic storage release:
   - Pushed publish source commit `0856f28` to `main`.
   - Ran guarded live Modrinth publish workflow `27083542931` from commit
     `0856f289588256b2a68011c1eb3b64319bf2d96f`.
   - The workflow passed client and dedicated-server smoke launches for every
     exact runtime listed by the supported profiles before upload.
   - Published the compatibility-group Modrinth versions:
     - `2.8.0+mc1.20-1.20.4` as Modrinth version `V3VoLsSk`.
     - `2.8.0+mc1.20.5-1.21.10` as Modrinth version `dzqk8kHN`.
     - `2.8.0+mc1.21.11` as Modrinth version `CbC3i6YC`.
     - `2.8.0+mc26.1-26.2-pre-3` as Modrinth version `Jqbl1MUu`.
   - Created annotated tag `v2.8.0` and GitHub Release
     `Lifetime Stat Tracker 2.8.0`, both pointing at the publish source commit.
19. `2.8.1` shared Tempest Studios data folder prep:
   - Bumped `mod_version` to `2.8.1`.
   - Changed runtime persistence to the shared Tempest Studios data namespace:
     `%APPDATA%\TempestStudios\Lifetime-Stat-Tracker\` on Windows,
     `~/Library/Application Support/TempestStudios/Lifetime-Stat-Tracker/` on
     macOS, and `$XDG_DATA_HOME/tempest-studios/lifetime-stat-tracker/` or
     `~/.local/share/tempest-studios/lifetime-stat-tracker/` on Linux.
   - Scoped active JSON under `profiles/<player profile>/` so a Minecraft
     account keeps one running total across launchers and instances without
     merging different profiles.
   - Namespaced singleplayer local-world handles by the active game directory,
     preventing two instances with the same local world name from sharing one
     world snapshot.
   - Added guarded migration into the active player-profile namespace from
     unnamespaced shared data first, then instance-scoped `2.8.1` pre-release
     test data, then the `2.8.0` app-data folder, then older
     `.minecraft/config/lifetime-stat-tracker/` data.
   - Added migration claim markers so each legacy source is auto-imported into
     only one player-profile namespace, while leaving every legacy source
     untouched as a backup.
   - Added `gradle/release-notes/2.8.1.md`.
   - Verified `git diff --check` and
     `.\gradlew.bat buildAllVersions --no-daemon --console=plain`.

## Current Compatibility Conclusion

The drift audit is good news for this mod: the compatibility surface is narrow
enough that we can target the fewest unique build artifacts possible. Supported
release profiles align with source overlays, with splits added only when one jar
literally cannot support the combined range.

Use this map for the promoted profile implementation:

- Release profile `1.20-1.20.4` uses source compat group `1.20-1.20.4`.
- Release profile `1.20.5-1.21.10` uses source compat group
  `1.20.5-1.21.10`.
- Release profile `1.21.11` stays separate as its own supported profile;
  descriptor-safe helpers now exist, so a later release-planning pass can decide
  whether it should collapse into a broader tested profile.
- Release profile `26.1-26.2-pre-3` uses source compat group `26.x`.

Do not over-split by copying Inventory Sort's GUI-driven groups unless compile
probes, binary runtime checks, dependency metadata, or smoke tests show a real
Lifetime Stat Tracker break that prevents one jar from covering the combined
range. The first implementation pass now compiles and smoke-tests successfully
for all four release profiles.

## Migration Goal

Take this repo from a targeted Minecraft `1.21.11` build to a single monorepo
that can build and publish compatibility-group jars from Minecraft `1.20.x`
through the current `26.x` target lane, following the Inventory Sort pipeline
where it applies.

This mod is simpler than Inventory Sort because it has one public jar and no
large GUI surface. It still has version-sensitive APIs around packets,
advancements, commands, networking, Java levels, and `26.x` build mechanics.

## Proposed Compatibility Groups

These are source compatibility groups, mapped to the current supported release
profiles:

| Source compat group | Release profiles using it | Java | Expected role |
| --- | --- | ---: | --- |
| `1.20-1.20.4` | `1.20-1.20.4` | 17 | Legacy stat packet accessor, legacy networking, no `RegistryFriendlyByteBuf`/`StreamCodec`; advancement key shape changes inside this range and needs a raw/reflection adapter. |
| `1.20.5-1.21.10` | `1.20.5-1.21.10` | 21 | Modern typed payloads with `PayloadTypeRegistry.playS2C/playC2S`, `ClientCommandManager`, `ResourceLocation` registry descriptors, and a `File`/`Path` server-directory helper. |
| `1.21.11` | `1.21.11` | 21 | Same typed payload and command APIs as earlier `1.21.x`, but `Registry#getKey` and `AdvancementHolder#id` now return `Identifier`. May collapse after descriptor-safe helpers exist. |
| `26.x` | `26.1-26.2-pre-3` | 25 | Java 25/non-remap lane with `ClientCommands` and `PayloadTypeRegistry.clientboundPlay/serverboundPlay`. |

Only keep a profile supported/publishable while its packaged jar has launched on
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
   - Current status: DONE for release jars, metadata verification, and
     smoke-gated publish promotion.
4. Compile probe matrix:
   - Probe exact runtimes first, using the Inventory Sort version metadata as a
     starting point.
   - Record failures by API surface rather than by raw compiler error only.
   - Split or merge profile groups based on evidence.
   - Current status: DONE for the current four-profile compile matrix via
     `buildValidationVersions`; GitHub Actions smoke proof now covers every
     exact runtime listed by the four supported release profiles.
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
     values are wired, Gradle toolchains are active locally, local
     `ciValidation`/`publishValidation` task roots exist, and GitHub Actions now
     installs the Java 17, Java 21, and Java 25 toolchains needed by the profile
     matrix.
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
     toolchain compile passes, source compatibility probes are green for the
     current `26.2-pre-3` compile anchor, and launcher smoke proof has passed
     for `26.1`, `26.1.1`, `26.1.2`, and `26.2-pre-3`.
8. Smoke-test automation:
   - Add a minimal client smoke launcher that installs the packaged release jar,
     reaches the client tick loop, force-loads mixin targets, and exits with a
     clear pass marker.
   - Add dedicated server smoke coverage for the optional server component.
   - Later add a true client-plus-dedicated-server identity handshake smoke if
     practical.
   - Current status: DONE for client-only packaged-jar launcher smoke coverage
     across every exact Minecraft version listed by the four supported release
     profiles. Dedicated server smoke task roots are now wired into
     `ciValidation` and `publishValidation`, with focused `1.21.11` proof
     completed locally. Full all-version server smoke proof should come from
     the manual GitHub Actions smoke workflow.
9. Modrinth publishing:
   - Add guarded dry-run and real publish tasks after profiles, metadata checks,
     and smoke records exist.
   - Publish supported profiles only.
   - Use `gradle/release-notes/<mod_version>.md` for Modrinth changelogs.
   - Current status: project id `rJCvFZKh` is recorded, GitHub repository
     secret `MODRINTH_TOKEN` is expected, Fabric API dependency id `P7dR8mSH`
     is recorded, guarded upload/dry-run tasks exist, client GitHub smoke proof
     is in place for every supported profile, dedicated server smoke is now part
     of the publish gate, and the first all-profile publish is complete.
   - Project-page summary and long-description copy now live in
     `gradle/modrinth-project-pages.md`; version publish tasks still upload
     only jars and per-version changelogs.
   - Successful real publishes should now be followed by an annotated
     `v<mod_version>` Git tag and one GitHub Release that links the Modrinth
     project/version ids.
10. Release promotion:
   - Keep new groups in `candidate_minecraft_version_profiles` until compile and
     smoke testing pass.
   - Promote only smoke-passed groups into `supported_minecraft_version_profiles`.
   - Run the guarded publish workflow when release approval is given.
   - Current status: all four release profiles are promoted, and the guarded
     all-profile Modrinth publish workflow has uploaded the `2.8.0+mc...`
     compatibility entries after client and dedicated-server smoke validation.

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
