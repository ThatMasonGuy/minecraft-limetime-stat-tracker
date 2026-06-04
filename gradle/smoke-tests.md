# Smoke Test Matrix

`gradle/smoke-tests.json` records client launch smoke-test status for each
release compatibility profile.

Status meanings:

- `pass`: this exact profile jar has launched on this exact Minecraft version.
- `pending`: the profile builds, but this Minecraft version still needs a
  client launch smoke test.
- `fail`: this Minecraft version has been tested and currently fails.

Only profiles listed in `supported_minecraft_version_profiles` are publishable.
`verifySmokeTestMatrix` fails if any supported profile/version is missing a
passing smoke record. Profiles listed in `candidate_minecraft_version_profiles`
must be present in the matrix, but may stay `pending` or `fail` while they are
not publishable.

## Current Status

The supported `1.21.11` profile has passed the automated packaged-jar client
smoke launch:

- install set: `lifetime-stat-tracker-client-only`
- task: `smokeTestSupportedClients`
- pass marker: `LIFETIMESTATTRACKER_SMOKE_TEST_PASS`
- result date: `2026-06-04`

Candidate release profiles are tracked in `gradle/smoke-tests.json` as
`pending` until every exact Minecraft runtime listed in `modrinth_game_versions`
has launched successfully.

## Runtime Profiles

Every supported compatibility-group profile must have passing launcher smoke
records for every exact Minecraft version listed in that profile's
`modrinth_game_versions`.

The exact runtime profiles, such as `gradle/version-profiles/1.20.1.properties`,
are smoke-launch inputs only. They are not extra Modrinth release artifacts and
must not be added to `supported_minecraft_version_profiles` or
`candidate_minecraft_version_profiles` unless we intentionally change the
release artifact map.

## Smoke Invariant

For Lifetime Stat Tracker, the matrix is simpler than Inventory Sort's matrix
because there is one public mod jar. Each automated client smoke launch must:

- install the packaged release jar
- launch the exact Minecraft runtime
- reach the client tick loop
- force-load the stat and advancement mixin target classes
- log a clear pass marker
- exit cleanly

The current smoke hook force-loads these mixin target classes:

- `net.minecraft.client.multiplayer.ClientPacketListener`
- `net.minecraft.client.multiplayer.ClientAdvancements`

## Smoke Coverage

Current install set:

- `lifetime-stat-tracker-client-only`: client jar installed in the client mods
  folder, no server-side jar.

Later optional install set:

- `lifetime-stat-tracker-client-server`: client jar plus a local integrated or
  dedicated Fabric server path that verifies the world identity payload.

The client-only smoke test should still cover the most important launch risks:
Mixin target method names, Mixin compatibility level, Fabric client commands,
stat request packet availability, Fabric networking registration, and JSON
initialization.

## Commands

```powershell
.\gradlew.bat verifySmokeTestMatrix
.\gradlew.bat smokeTestSupportedClients
.\gradlew.bat publishValidation
.\gradlew.bat buildValidationVersions
.\gradlew.bat smokeTestValidationClients
.\gradlew.bat ciValidation
```

For focused local checks, use `smokeTestSelectedClients` with one or more
filters:

```powershell
.\gradlew.bat smokeTestSelectedClients "-Plifetimestattracker_smoke_profiles=1.20.5-1.21.10" "-Plifetimestattracker_smoke_game_versions=1.21.10"
```

Accepted install set ids are currently:

- `lifetime-stat-tracker-client-only`

Nested smoke Gradle launches use `--no-daemon` by default. For local timing
experiments, pass `-Plifetimestattracker_smoke_nested_no_daemon=false`.

For Linux/headless CI:

```bash
./gradlew ciValidation -Plifetimestattracker_smoke_xvfb=true --no-daemon --console=plain
```

## Promotion Rule

After a candidate profile passes client smoke testing on every version in
`modrinth_game_versions`, update its records to `pass`. To make that profile
publishable, move it from `candidate_minecraft_version_profiles` to
`supported_minecraft_version_profiles`, then run the full release validation.
