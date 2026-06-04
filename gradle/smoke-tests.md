# Smoke Test Matrix

This file documents the planned smoke-test model. Automated smoke tasks and
`gradle/smoke-tests.json` do not exist in this repo yet.

## Goal

Every supported compatibility-group profile must have passing launcher smoke
records for every exact Minecraft version listed in that profile's
`modrinth_game_versions`.

For Lifetime Stat Tracker, the first smoke matrix can be simpler than Inventory
Sort's matrix because there is one public mod jar. The initial invariant is:

- install the packaged release jar
- launch the exact Minecraft runtime
- reach the client tick loop
- force-load the stat and advancement mixin target classes
- log a clear pass marker
- exit cleanly

## Status Meanings

When a matrix file is added, use these statuses:

- `pass`: this exact profile jar has launched on this exact Minecraft version.
- `pending`: the profile builds, but this Minecraft version still needs a
  client launch smoke test.
- `fail`: this Minecraft version has been tested and currently fails.

Only supported profiles should be publishable. Candidate profiles may remain
`pending` or `fail` while under investigation.

## Planned Smoke Coverage

Initial install set:

- `lifetime-stat-tracker-client-only`: client jar installed in the client mods
  folder, no server-side jar.

Later optional install set:

- `lifetime-stat-tracker-client-server`: client jar plus a local integrated or
  dedicated Fabric server path that verifies the world identity payload.

The client-only smoke test should still cover the most important launch risks:
Mixin target method names, Mixin compatibility level, Fabric client commands,
stat request packet availability, Fabric networking registration, and JSON
initialization.

## Planned Commands

These commands are placeholders for the eventual Gradle implementation:

```powershell
.\gradlew.bat verifySmokeTestMatrix
.\gradlew.bat smokeTestSupportedClients
.\gradlew.bat smokeTestSelectedClients "-Plifetimestattracker_smoke_profiles=1.21.9-1.21.11" "-Plifetimestattracker_smoke_game_versions=1.21.11"
.\gradlew.bat ciValidation
```

Current verification command:

```powershell
.\gradlew.bat build --no-daemon --console=plain
```

## Promotion Rule

After a candidate profile passes client smoke testing on every version in
`modrinth_game_versions`, update its records to `pass`. To make that profile
publishable, move it from `candidate_minecraft_version_profiles` to
`supported_minecraft_version_profiles`, then run the full release validation.
