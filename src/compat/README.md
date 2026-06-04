# Compatibility Source Groups

Compatibility groups hold source files that only compile for a specific
Minecraft API shape. The first overlays are active and selected by the current
Gradle version profile's `compat_group`.

Expected layout:

```text
src/compat/<compat_group>/main/java/
src/compat/<compat_group>/main/resources/
src/compat/<compat_group>/client/java/
src/compat/<compat_group>/client/resources/
```

Keep shared logic in `src/main/java` and `src/client/java`. Add compatibility
group code only when a Minecraft version range needs different class names,
method signatures, mixin targets, packet APIs, command builders, or small API
adapters.

Initial source compat groups:

```text
src/compat/1.20-1.20.4/
src/compat/1.20.5-1.21.10/
src/compat/1.21.11/
src/compat/26.x/
```

Release profiles should align with these source groups by default. Split a
release profile away from its source group only when compile probes, binary
runtime checks, dependency metadata, or smoke tests prove that one jar cannot
honestly cover the proposed range.
The target is the fewest unique builds possible; compatibility overlays exist
only for API shapes that cannot share source.

Recommended package ownership:

```text
tempeststudios.lifetimestattracker.compat
tempeststudios.lifetimestattracker.compat.client
tempeststudios.lifetimestattracker.compat.mixin
```

Implemented compatibility adapters:

- `StatsPacketCompat`: `getStats()` on `1.20-1.20.4`, `stats()` on
  `1.20.5+`.
- `AdvancementIdCompat`: raw-object id extraction for `Advancement#getId()`,
  `AdvancementHolder#id()` returning `ResourceLocation`, and
  `AdvancementHolder#id()` returning `Identifier`.
- `RegistryKeyCompat`: descriptor-safe `Registry#getKey(...)` string extraction
  across `ResourceLocation` and `Identifier`.
- `NetworkPayloadCompat`: legacy `FriendlyByteBuf` channels on `1.20-1.20.4`,
  typed `CustomPacketPayload` on `1.20.5-1.21.11`, and renamed
  `PayloadTypeRegistry.clientboundPlay/serverboundPlay` on `26.x`.
- `ClientCommandCompat`: `ClientCommandManager` through `1.21.11`,
  `ClientCommands` on `26.x`.
- `ServerPathCompat`: normalize `MinecraftServer#getServerDirectory()` returning
  `File` through `1.20.6` and `Path` from `1.21+`.
- `ServerPermissionCompat`: descriptor-safe server op checks across
  `GameProfile` and `NameAndId`.
- replacement mixins only where the adapter approach cannot cover a future
  target method or field shape.

Avoid compiling duplicate fully-qualified class names from shared and compat
source folders. Prefer shared code calling small compat adapters over copying
whole feature classes into a compat group.
