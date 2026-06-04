# Minecraft Compatibility

Research date: 2026-06-04

Scope: Lifetime Stat Tracker source compatibility from Minecraft `1.20` through
`26.2-pre-3`, using the Inventory Sort pipeline as the release/profile model but
auditing this mod's own API surface.

## Executive Recommendation

The goal is the fewest unique build artifacts that can honestly support the
targeted Minecraft range. Align candidate release profiles with source
compatibility groups by default. Split a candidate only when one compiled jar
literally cannot cover the combined runtime dependency metadata, binary
compatibility, or smoke-test matrix.

Supported release profile map:

| Release profile | Compile anchor | Runtime claim after smoke tests | Java | Source compat group |
| --- | --- | --- | ---: | --- |
| `1.20-1.20.4` | `1.20` | `1.20`, `1.20.1`, `1.20.2`, `1.20.3`, `1.20.4` | 17 | `1.20-1.20.4` |
| `1.20.5-1.21.10` | `1.21.10` | `1.20.5` through `1.21.10` | 21 | `1.20.5-1.21.10` |
| `1.21.11` | `1.21.11` | `1.21.11` | 21 | `1.21.11` |
| `26.1-26.2-pre-3` | `26.2-pre-3` | `26.1`, `26.1.1`, `26.1.2`, `26.2-pre-3` | 25 | `26.x` |

Compile probes and packaged-jar client smoke tests now pass for this
four-profile map. Keep `1.21.11` separate as its own proven release profile
unless a later release-planning pass decides to collapse it into a broader
tested profile.

The `1.20-1.20.4` release profile can stay as one profile only if its
advancement mixin treats advancement keys as raw objects and extracts ids via a
small adapter. Without that, split it into `1.20-1.20.1` and
`1.20.2-1.20.4`, because Mojang changed the client advancement progress map at
`1.20.2`.

## Probed API Matrix

The following matrix is based on local `javap` inspection of the cached
Inventory Sort Minecraft jars plus Fabric API module jars where Fabric API class
names changed.

| Version range | Stat packet | Advancement progress key | Id/registry type | Payload registration | Server dir | Client commands |
| --- | --- | --- | --- | --- | --- | --- |
| `1.20-1.20.1` | `ClientboundAwardStatsPacket#getStats()` returns `Map<Stat<?>, Integer>` | `Map<Advancement, AdvancementProgress>` and `Advancement#getId()` | `ResourceLocation` | legacy channel API, no `PayloadTypeRegistry`, no `RegistryFriendlyByteBuf`, no `StreamCodec` | `File` | `ClientCommandManager` |
| `1.20.2-1.20.4` | `getStats()` | `Map<AdvancementHolder, AdvancementProgress>` and `AdvancementHolder#id()` | `ResourceLocation` | Minecraft has legacy `CustomPacketPayload#id()`, but current stream-codec payload API is still unavailable | `File` | `ClientCommandManager` |
| `1.20.5-1.20.6` | record accessor `stats()` returns `Object2IntMap<Stat<?>>` | `AdvancementHolder#id()` | `ResourceLocation` | `CustomPacketPayload`, `RegistryFriendlyByteBuf`, `StreamCodec`, `PayloadTypeRegistry.playS2C/playC2S` | `File` | `ClientCommandManager` |
| `1.21-1.21.10` | `stats()` | `AdvancementHolder#id()` | `ResourceLocation` | same `playS2C/playC2S` API shape | `Path` | `ClientCommandManager` |
| `1.21.11` | `stats()` | `AdvancementHolder#id()` | `Identifier` | same `playS2C/playC2S` API shape | `Path` | `ClientCommandManager` |
| `26.1-26.2-pre-3` | `stats()` | `AdvancementHolder#id()` | `Identifier` | `PayloadTypeRegistry.clientboundPlay/serverboundPlay` | `Path` | `ClientCommands` |

Observed but currently irrelevant Minecraft changes:

- `ClientboundUpdateAdvancementsPacket` constructor shapes drift across the
  range, but this mod injects into `ClientAdvancements.update(...)` and reads
  the client's `progress` map after the vanilla update, so constructor changes
  are not a direct source break.
- `ServerboundClientCommandPacket.Action.REQUEST_STATS` exists across the
  inspected range.
- `MinecraftServer.storageSource`, `getWorldData().getLevelName()`, and the
  storage-source `getLevelId()` reflection route are still present across the
  inspected range.

## Drift Points Relevant To This Mod

### Stat Packet Capture

Current code calls `ClientboundAwardStatsPacket#stats()`, which is only valid
from `1.20.5` onward.

Break:

- `1.20-1.20.4` exposes `getStats()` instead.
- The return type is a regular `Map<Stat<?>, Integer>`, not an
  `Object2IntMap<Stat<?>>`.

Required shim:

- `StatsPacketCompat` with one source implementation for `1.20-1.20.4` and one
  for `1.20.5+`.
- Either normalize the legacy map to `Object2IntMap<Stat<?>>` before passing it
  to `LifetimeStatsManager`, or widen `LifetimeStatsManager.onStatsPacket` to
  accept a simple iterable/map abstraction.

### Advancement Capture

Current code shadows:

```java
private Map<AdvancementHolder, AdvancementProgress> progress;
```

and calls:

```java
entry.getKey().id().toString();
```

Breaks:

- `1.20-1.20.1` has no `AdvancementHolder`; the progress map is keyed by
  `Advancement`, and ids come from `getId()`.
- `1.20.2-1.21.10` uses `AdvancementHolder#id()` returning
  `ResourceLocation`.
- `1.21.11+` uses `AdvancementHolder#id()` returning `Identifier`; this is a
  different bytecode descriptor even though the source still looks like
  `.id().toString()`.

Required shim:

- Shadow the map as raw `Map<?, AdvancementProgress>` in version ranges where a
  single mixin should cover multiple advancement key shapes.
- Add `AdvancementIdCompat.idString(Object key)` that tries `id()` and
  `getId()` reflectively and returns `String.valueOf(...)`.
- If avoiding reflection, split source groups at `1.20.2` and `1.21.11`.

### Stat Key Serialization

Current code serializes stat ids through:

```java
BuiltInRegistries.STAT_TYPE.getKey(type)
valueRegistry.getKey(value)
```

Break:

- `Registry#getKey(T)` returns `ResourceLocation` through `1.21.10`.
- `Registry#getKey(T)` returns `Identifier` in `1.21.11` and `26.x`.
- A jar compiled against one descriptor can hit `NoSuchMethodError` on the
  other descriptor, even when assigning to `Object`.
- The existing fallback to `getId(...)` avoids a crash, but it can degrade saved
  stat keys to numeric ids. That is not acceptable for stable cross-version
  lifetime data.

Required shim:

- `RegistryKeyCompat.keyString(Object registry, Object value)` should invoke
  `getKey(Object)` reflectively and stringify the result.
- Keep `getId(...)` as an emergency fallback only.

### Custom Payload Networking

Current code uses the modern typed payload stack:

- `CustomPacketPayload`
- `RegistryFriendlyByteBuf`
- `StreamCodec`
- `PayloadTypeRegistry.playS2C()`
- `PayloadTypeRegistry.playC2S()`
- `ClientPlayNetworking` and `ServerPlayNetworking` typed payload handlers

Breaks:

- `1.20-1.20.1` has no Minecraft `CustomPacketPayload` class.
- `1.20.2-1.20.4` has an older Minecraft `CustomPacketPayload#id()` shape, but
  not the current stream-codec typed payload stack.
- `1.20-1.20.4` Fabric networking should use channel ids and `FriendlyByteBuf`
  handlers.
- `26.x` keeps typed payload handlers but renames Fabric payload registry access
  from `playS2C/playC2S` to `clientboundPlay/serverboundPlay`.

Required shim:

- `1.20-1.20.4`: legacy channel constants plus manual encode/decode with
  `FriendlyByteBuf`.
- `1.20.5-1.21.11`: current `CustomPacketPayload` records and
  `PayloadTypeRegistry.playS2C/playC2S`.
- `26.x`: same payload records, but registration through
  `PayloadTypeRegistry.clientboundPlay/serverboundPlay`.

### Client Command Builders

Current code imports `ClientCommandManager`.

Break:

- `ClientCommandManager` exists through `1.21.11`.
- In `26.x`, Fabric API exposes `ClientCommands` instead.

Required shim:

- `ClientCommandCompat.literal(...)` and `ClientCommandCompat.argument(...)`.
- `1.20.x`/`1.21.x` implementation delegates to `ClientCommandManager`.
- `26.x` implementation delegates to `ClientCommands`.

### Server Directory Fallback

Current code directly calls `MinecraftServer#getServerDirectory()` and expects a
`Path`.

Break:

- `1.20-1.20.6` returns `java.io.File`.
- `1.21+` returns `java.nio.file.Path`.

Required shim:

- `ServerPathCompat.serverDirectoryName(MinecraftServer server)` should call
  `getServerDirectory` reflectively and normalize either `File` or `Path` to a
  `Path`/file-name string.
- Use this helper in both the client integrated-server fallback and the optional
  server identity fallback.

### Java, Mixin, And Build Lane

Breaks:

- `1.20-1.20.4` must build with Java 17 and a `JAVA_17` Mixin compatibility
  level.
- `1.20.5-1.21.11` builds with Java 21 and `JAVA_21`.
- `26.x` builds with Java 25 and should follow Inventory Sort's non-remap Loom
  lane.
- Current `fabric.mod.json` and mixin config hardcode `minecraft: ~1.21.11` and
  `JAVA_21`.

Required shim/build work:

- Profile-expand `fabric.mod.json` dependencies.
- Profile-expand the mixin compatibility level.
- Use per-profile Java toolchains.
- Use the Inventory Sort `26.x` non-remap pattern for `26.1` and `26.2-pre-3`.

## Source Compat Group Plan

### `1.20-1.20.4`

Purpose: oldest supported lane with Java 17, old stat packet accessor, old
networking, and mixed advancement key shape inside the range.

Must contain:

- legacy networking registration and send/receive helpers
- `ClientboundAwardStatsPacket#getStats()` extractor
- advancement id helper that handles both `Advancement#getId()` and
  `AdvancementHolder#id()`
- no references to `RegistryFriendlyByteBuf`, `StreamCodec`, or modern
  `PayloadTypeRegistry`

Split trigger:

- If raw/reflection advancement id extraction is rejected, split into
  `1.20-1.20.1` and `1.20.2-1.20.4`.

### `1.20.5-1.21.10`

Purpose: Java 21 typed-payload lane before the `Identifier` descriptor change.

Must contain:

- current typed payload records and `PayloadTypeRegistry.playS2C/playC2S`
- `stats()` stat packet extractor
- `ClientCommandManager` command helper
- server-directory helper so `File` and `Path` runtimes can both work

This source group should start as one candidate release profile. Split it only
if compile probes, binary runtime checks, or dependency metadata prove that one
jar cannot cover the full `1.20.5-1.21.10` range.

### `1.21.11`

Purpose: exact Java 21 lane after Mojang's id type rename.

Must contain:

- same typed payload registration as `1.20.5-1.21.10`
- `Identifier`-safe stat key serialization
- `Identifier`-safe advancement id extraction
- `ClientCommandManager` command helper

Collapse trigger:

- Once stat-key and advancement-id helpers avoid direct `ResourceLocation` or
  `Identifier` method descriptors, this source group can likely be merged into
  `1.20.5-1.21.10`.

### `26.x`

Purpose: Java 25, non-remap forward lane for `26.1-26.1.2` and
`26.2-pre-3`.

Must contain:

- `PayloadTypeRegistry.clientboundPlay/serverboundPlay`
- `ClientCommands` command helper
- Java 25 mixin/build metadata
- Inventory Sort's non-remap release artifact behavior

Start with one candidate release profile for `26.1-26.2-pre-3`. Split into
`26.1-26.1.2` and `26.2-pre-3` only if Fabric dependency metadata, the chosen
compile anchor, or smoke tests prove that one jar cannot honestly claim both
families.

## Release Profile Notes

Profiles should describe:

- `profile_id`: release folder and Modrinth suffix.
- `minecraft_version`: compile anchor used by Loom and mappings.
- `minecraft_dependency`: Fabric Loader dependency range written into
  `fabric.mod.json`.
- `modrinth_game_versions`: exact game versions to list after smoke testing.
- `compat_group`: source overlay folder for version-specific APIs.
- `java_version`: Java toolchain and Mixin compatibility level.
- Fabric Loader, Fabric API, Loom, and any non-remap lane flag.

Do not mark a Minecraft version as compatible because it probably works. A
version may be listed on Modrinth only after the exact packaged jar has launched
on that exact Minecraft runtime.

## Probe Commands

Current baseline:

```powershell
.\gradlew.bat build --no-daemon --console=plain
```

After profile support exists:

```powershell
.\gradlew.bat build "-Pminecraft_version_profile=<profile>" --no-daemon --console=plain
.\gradlew.bat buildAllVersions --no-daemon --console=plain
```

## Evidence Sources

- Current Lifetime Stat Tracker source, Gradle metadata, and mixin metadata.
- Local Inventory Sort version profiles, compatibility docs, and `26.x`
  compatibility source overlays.
- Local `javap` inspection of cached Minecraft jars for `1.20`, `1.20.1`,
  `1.20.2`, `1.20.3`, `1.20.4`, `1.20.5`, `1.20.6`, `1.21`, `1.21.6`,
  `1.21.9`, `1.21.10`, `1.21.11`, `26.1.2`, and `26.2-pre-3`.
- Official Fabric Maven metadata/POMs for the `26.x` Fabric API modules:
  [Fabric API `0.150.0+26.1.2`](https://maven.fabricmc.net/net/fabricmc/fabric-api/fabric-api/0.150.0+26.1.2/fabric-api-0.150.0+26.1.2.pom),
  [Fabric API `0.150.2+26.2`](https://maven.fabricmc.net/net/fabricmc/fabric-api/fabric-api/0.150.2+26.2/fabric-api-0.150.2+26.2.pom),
  and
  [fabric-command-api-v2 metadata](https://maven.fabricmc.net/net/fabricmc/fabric-api/fabric-command-api-v2/maven-metadata.xml).
