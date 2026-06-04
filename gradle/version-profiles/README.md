# Minecraft Version Profiles

This directory contains the multi-version profile metadata used by Gradle. The
current supported build remains Minecraft `1.21.11`; broader profiles are
candidates until metadata checks and smoke tests prove them. Current candidate
compile probes pass through `buildValidationVersions`.

## Goal

Build profiles should keep one source tree while letting Gradle swap Minecraft,
Fabric Loader, Fabric API, Loom, Java, metadata, and optional compatibility
source overlays.

Profiles are **release compatibility groups**. A profile does not have to be one
exact Minecraft patch version; it can represent one compiled jar that is tested
and published for several compatible Minecraft versions.

Exact smoke runtime profiles, such as `1.20.1.properties`, are different from
release profiles. They exist so the smoke launcher can boot the exact Minecraft
runtime claimed by a release profile. Do not add these smoke-only profiles to
`supported_minecraft_version_profiles` or `candidate_minecraft_version_profiles`
unless we intentionally decide to publish more jars.

## Profile Lists

`gradle.properties` currently has:

```properties
minecraft_version_profile=1.21.11
supported_minecraft_version_profiles=1.21.11
candidate_minecraft_version_profiles=1.20-1.20.4,1.20.5-1.21.10,26.1-26.2-pre-3
```

Only move a profile from candidate to supported after it builds, verifies
metadata, and passes launcher smoke tests for every listed game version.

Candidate profiles start aligned with source compatibility groups. Split a
profile only after compile probes, binary runtime checks, dependency metadata,
or smoke tests prove that one jar cannot honestly cover the proposed range.
Prefer the fewest unique build artifacts possible; do not add separate builds for
patch ranges that can share one compatible jar.

## Profile Fields

```properties
profile_id=1.20.5-1.21.10
minecraft_version=<proven_compile_anchor>
minecraft_dependency=>=1.20.5 <=1.21.10
modrinth_game_versions=1.20.5,1.20.6,1.21,1.21.1,1.21.2,1.21.3,1.21.4,1.21.5,1.21.6,1.21.7,1.21.8,1.21.9,1.21.10
compat_group=1.20.5-1.21.10
loader_version=0.18.4
loom_version=1.14-SNAPSHOT
fabric_api_version=<matching Fabric API version>
java_version=21
unobfuscated_minecraft=false
```

- `profile_id` is the release output folder and Modrinth version suffix.
- `minecraft_version` is the compile anchor used by Loom and mappings.
- `minecraft_dependency` is the Fabric Loader dependency range written into
  `fabric.mod.json`.
- `modrinth_game_versions` is the exact set of game versions to publish for the
  jar after smoke testing.
- `compat_group` selects any version-specific source overlay.
- `java_version` selects the Java compile release and generated Mixin
  compatibility level. Gradle requests the matching Java toolchain for compile
  and JavaExec tasks.
- `unobfuscated_minecraft=true` is expected only for Minecraft `26.x` profiles
  if this repo follows Inventory Sort's non-remap build lane.

## Implemented Commands

Current profile-foundation commands:

```powershell
.\gradlew.bat printVersionProfile
.\gradlew.bat listVersionProfiles
.\gradlew.bat build "-Pminecraft_version_profile=1.21.11"
.\gradlew.bat buildRelease
.\gradlew.bat buildAllVersions
.\gradlew.bat buildValidationVersions
```

`buildRelease` builds the active profile, collects the release jar under
`build/release/<profile_id>/`, and verifies packaged metadata. `buildAllVersions`
builds only profiles listed in `supported_minecraft_version_profiles`, which is
currently just `1.21.11`. `buildValidationVersions` includes candidates and
currently passes for the initial four-profile compile matrix.

Current smoke commands:

```powershell
.\gradlew.bat verifySmokeTestMatrix
.\gradlew.bat smokeTestSupportedClients
.\gradlew.bat smokeTestSelectedClients "-Plifetimestattracker_smoke_profiles=1.20.5-1.21.10" "-Plifetimestattracker_smoke_game_versions=1.21.10"
.\gradlew.bat smokeTestValidationClients
.\gradlew.bat publishValidation
.\gradlew.bat ciValidation
```

Baseline command:

```powershell
.\gradlew.bat build --no-daemon --console=plain
```

## Compatibility Source Layout

Compatibility-specific code should live under:

```text
src/compat/<compat_group>/main/java/
src/compat/<compat_group>/main/resources/
src/compat/<compat_group>/client/java/
src/compat/<compat_group>/client/resources/
```

Keep shared behavior in `src/main/java` and `src/client/java`. Add compatibility
sources only for target-specific APIs that cannot compile across the intended
range.
