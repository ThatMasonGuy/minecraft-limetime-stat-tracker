# Compatibility Source Groups

Compatibility groups hold source files that only compile for a specific
Minecraft API shape. This directory is reserved for the planned multi-version
pipeline; no compatibility overlays are active yet.

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

Release profiles may be more granular than source groups. For example,
`1.20.5-1.20.6` and `1.21-1.21.10` can share source overlays if their compile
probes pass, while `26.1-26.1.2` and `26.2-pre-3` should remain separate release
profiles even if they both use `src/compat/26.x/`.

Recommended package ownership:

```text
tempeststudios.lifetimestattracker.compat
tempeststudios.lifetimestattracker.compat.client
tempeststudios.lifetimestattracker.compat.mixin
```

Required compatibility candidates:

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
- replacement mixins only where the adapter approach cannot cover a target
  method or field shape.

Avoid compiling duplicate fully-qualified class names from shared and compat
source folders. Prefer shared code calling small compat adapters over copying
whole feature classes into a compat group.
