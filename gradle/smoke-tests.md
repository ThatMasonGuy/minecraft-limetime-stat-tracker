# Smoke Test Matrix

`gradle/smoke-tests.json` records historical client launch smoke-test status for
each release compatibility profile. Live validation now also runs a dedicated
server smoke install set before CI validation or publishing can pass.

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

All four release profiles have passing packaged-jar client smoke records:

- `1.20-1.20.4`
- `1.20.5-1.21.10`
- `1.21.11`
- `26.1-26.2-pre-3`

GitHub Actions candidate smoke run `26934205756` passed every listed exact
runtime except `26.2-pre-3`. After the Fabric Loader prerelease dependency
metadata fix, focused run `26935246770` passed `26.2-pre-3`.

Each recorded pass used install set `lifetime-stat-tracker-client-only`, reached
the client tick loop, force-loaded `ClientPacketListener` and
`ClientAdvancements`, printed `LIFETIMESTATTRACKER_SMOKE_TEST_PASS`, and exited
cleanly.

Dedicated server smoke coverage is not backfilled into the historical matrix
yet. It is a live Gradle gate: `ciValidation` and `publishValidation` now run
the server launch tasks as well as the client launch tasks. GitHub Actions
Modrinth publish run `26943407860` proved this gate for release `2.7.1`,
passing both client and dedicated-server smoke launches for every exact
Minecraft runtime published by the four supported compatibility-group profiles.

There are currently no candidate release profiles. New candidates should be
tracked in `gradle/smoke-tests.json` as `pending` until every exact Minecraft
runtime listed in `modrinth_game_versions` has launched successfully.

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

Each automated dedicated server smoke launch must:

- install the packaged release jar
- launch the exact dedicated Minecraft server runtime
- use an isolated smoke run directory with accepted EULA and low-cost
  `server.properties`
- reach the server tick loop
- verify `/lstserver` is registered
- resolve the advertised world identity through the server identity code path
- log `LIFETIMESTATTRACKER_SERVER_SMOKE_TEST_PASS`
- exit cleanly

## Smoke Coverage

Current install sets:

- `lifetime-stat-tracker-client-only`: client jar installed in the client mods
  folder, no server-side jar.
- `lifetime-stat-tracker-server-only`: jar installed on a dedicated Fabric
  server, no client connection.

Later optional install set:

- `lifetime-stat-tracker-client-server`: client jar plus a local integrated or
  dedicated Fabric server path that verifies the world identity payload.

The client-only smoke test should still cover the most important launch risks:
Mixin target method names, Mixin compatibility level, Fabric client commands,
stat request packet availability, Fabric networking registration, and JSON
initialization.

The server-only smoke test covers the optional server component: server
entrypoint loading, server payload registration, `/lstserver` registration,
server path/world identity reflection, EULA-safe dedicated launch, and clean
shutdown. It does not yet prove a full client-to-dedicated-server identity
handshake.

## Commands

```powershell
.\gradlew.bat verifySmokeTestMatrix
.\gradlew.bat smokeTestSupportedClients
.\gradlew.bat smokeTestSupportedServers
.\gradlew.bat smokeTestSupported
.\gradlew.bat publishValidation
.\gradlew.bat buildValidationVersions
.\gradlew.bat smokeTestValidationClients
.\gradlew.bat smokeTestValidationServers
.\gradlew.bat smokeTestValidation
.\gradlew.bat ciValidation
```

For focused local checks, use `smokeTestSelectedClients`,
`smokeTestSelectedServers`, or aggregate `smokeTestSelected` with one or more
filters:

```powershell
.\gradlew.bat smokeTestSelectedClients "-Plifetimestattracker_smoke_profiles=1.20.5-1.21.10" "-Plifetimestattracker_smoke_game_versions=1.21.10"
.\gradlew.bat smokeTestSelectedServers "-Plifetimestattracker_smoke_profiles=1.21.11" "-Plifetimestattracker_smoke_game_versions=1.21.11"
.\gradlew.bat smokeTestSelected "-Plifetimestattracker_smoke_profiles=1.21.11" "-Plifetimestattracker_smoke_game_versions=1.21.11" "-Plifetimestattracker_smoke_install_sets=lifetime-stat-tracker-server-only"
```

Accepted install set ids are currently:

- `lifetime-stat-tracker-client-only`
- `lifetime-stat-tracker-server-only`

Nested smoke Gradle launches use `--no-daemon` by default. For local timing
experiments, pass `-Plifetimestattracker_smoke_nested_no_daemon=false`.

Smoke logs are written under `build/smoke-logs/<profile>/<game_version>/<install_set>.log`.
Run directories are isolated under `build/smoke-run/`.

For Linux/headless CI:

```bash
./gradlew ciValidation -Plifetimestattracker_smoke_xvfb=true --no-daemon --console=plain
```

## Promotion Rule

After a candidate profile passes client smoke testing on every version in
`modrinth_game_versions`, update its matrix records to `pass`. To make that
profile publishable, move it from `candidate_minecraft_version_profiles` to
`supported_minecraft_version_profiles`, then run the full release validation,
including live dedicated-server smoke coverage.
