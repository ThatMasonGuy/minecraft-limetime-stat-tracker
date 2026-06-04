# Minecraft Version Profiles

This directory is reserved for the planned multi-version profile system. The
current repository still uses flat properties in `gradle.properties` and builds
one Minecraft `1.21.11` jar.

## Goal

Build profiles should keep one source tree while letting Gradle swap Minecraft,
Fabric Loader, Fabric API, Loom, Java, metadata, and optional compatibility
source overlays.

Profiles are **release compatibility groups**. A profile does not have to be one
exact Minecraft patch version; it can represent one compiled jar that is tested
and published for several compatible Minecraft versions.

## Planned Profile Lists

The eventual `gradle.properties` model should have:

```properties
minecraft_version_profile=1.21.11
supported_minecraft_version_profiles=
candidate_minecraft_version_profiles=1.20-1.20.4,1.20.5-1.20.6,1.21-1.21.5,1.21.6-1.21.8,1.21.9-1.21.11,26.1.2,26.2-pre-3
```

Only move a profile from candidate to supported after it builds, verifies
metadata, and passes launcher smoke tests for every listed game version.

## Planned Profile Fields

```properties
profile_id=1.21.9-1.21.11
minecraft_version=1.21.11
minecraft_dependency=>=1.21.9 <=1.21.11
modrinth_game_versions=1.21.9,1.21.10,1.21.11
compat_group=1.21_late
loader_version=0.18.4
loom_version=1.14-SNAPSHOT
fabric_api_version=0.140.2+1.21.11
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
- `java_version` selects the Gradle toolchain and generated Mixin compatibility
  level.
- `unobfuscated_minecraft=true` is expected only for Minecraft `26.x` profiles
  if this repo follows Inventory Sort's non-remap build lane.

## Planned Commands

These commands are documentation for the intended pipeline and may not exist
until the Gradle migration is implemented:

```powershell
.\gradlew.bat printVersionProfile
.\gradlew.bat build "-Pminecraft_version_profile=1.21.11"
.\gradlew.bat buildAllVersions
.\gradlew.bat verifyReleaseJars
.\gradlew.bat ciValidation
```

Current command:

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
