# Lifetime Stat Tracker

A Fabric mod for Minecraft `1.20` through `26.2-pre-3` that tracks lifetime
stats across worlds, servers, Realms, and deleted saves.

Unlike vanilla per-world stats, Lifetime Stat Tracker keeps cumulative totals in your config folder so progress is preserved even if worlds are renamed, deleted, or moved.

## Features

- Lifetime totals for key stats like play time, distance walked, jumps, mob kills, and deaths
- Per-world and per-server breakdowns
- Advancement tracking across tracked worlds, with recipe unlocks excluded
- Safe support for singleplayer, modded servers, unmodded servers, and Realms
- Manual seed/remove tools for correcting unavoidable unmodded server ambiguity
- Persistent JSON storage in the Fabric config directory

## Supported Environment

- Minecraft: `1.20` through `26.2-pre-3`, published as compatibility-group jars
  for `1.20-1.20.4`, `1.20.5-1.21.10`, `1.21.11`, and `26.1-26.2-pre-3`
- Fabric Loader: `0.18.4+` for `1.20`/`1.21` profiles; `0.19.2+` for `26.x`
- Fabric API: required; install the Fabric API build matching your Minecraft
  version
- Java: `17+` for `1.20-1.20.4`, `21+` for `1.20.5-1.21.11`, and `25+` for
  `26.x`
- Client install: supported
- Server install: optional, improves per-world server identity

## Installation

1. Install Fabric Loader for your Minecraft version.
2. Install the matching Fabric API in your `mods` folder.
3. Download the Lifetime Stat Tracker jar that matches your Minecraft
   compatibility range.
4. Place the Lifetime Stat Tracker jar in your `mods` folder.
5. For best multiplayer world separation, also install the mod on compatible
   Fabric servers.
6. Launch the game.

## How Tracking Works

The mod reads Minecraft's stat packets and stores local lifetime totals under:

```text
config/lifetime-stat-tracker/
```

Stats are tracked by world/server handle, then rolled into lifetime totals.

Singleplayer worlds use a stable local save identity where possible, so renaming a world should not create a duplicate entry.

Modded servers can identify their current world to the client. When the mod is installed on both client and server, the server sends a world identity packet and the client tracks that server world separately.

Unmodded servers and Realms cannot reliably expose their backend world identity to the client. For safety, those are tracked as one aggregate server entry using high-water marks:

- If a stat rises above the stored server value, only the increase is added.
- If a stat is lower than the stored server value, nothing is added and the stored value is not lowered.
- This prevents double counting when proxy servers redirect you between game modes or worlds with separate stat files.

For lower-stat redirected worlds on unmodded servers, use manual seeding.

## Commands

Both command roots are equivalent:

```text
/lifetimestats
/lst
```

Subcommands:

```text
/lst
```

Shows a summary of lifetime totals.

```text
/lst time
```

Shows total play time and per-world play time.

```text
/lst worlds
```

Lists all tracked worlds and servers.

```text
/lst world <name>
```

Shows details for a matching tracked world/server.

```text
/lst advancements
```

Shows unique advancement counts, excluding recipe unlocks.

```text
/lst current
```

Shows the current tracking handle.

```text
/lst debug
```

Shows request/packet/debug counters, including high-water skips for unmodded server aggregates.

```text
/lst seed world <name>
```

Manually seeds the current unmodded server/Realm world into a named entry.

Use this when a server redirects you to a different game mode/world with lower stats than the aggregate server high-water value. The first seed adds the current additive stats. Running the same seed name later adds only positive deltas since the last seed.

```text
/lst remove world <name>
```

Removes a tracked world/manual seed after creating a backup. The removed world's additive stats are subtracted from lifetime totals and clamped at zero. Ambiguous names are rejected so you can use a more specific name or handle.

```text
/lst clear
```

Backs up all tracker JSON files, then clears stored tracker data.

## Server Support

Client-only install works for:

- Singleplayer
- LAN/integrated play
- Unmodded multiplayer servers
- Realms

Installing the mod on a Fabric server improves tracking:

- The server sends a world identity on join.
- The server resends identity shortly after join.
- The client can request identity if it did not receive it yet.
- The identity payload has a versioned v2 channel while preserving the older v1 packet shape for compatibility.

Server command:

```text
/lstserver identity
```

Prints the world identity the server is advertising to clients, including the identity source and protocol version. This is useful when debugging multi-world or proxy setups.

## Data Files

Data is stored under:

```text
.minecraft/config/lifetime-stat-tracker/
```

Files:

- `totals.json` - lifetime cumulative stat totals
- `snapshots.json` - last absolute snapshot per handle for delta calculation
- `world_stats.json` - per-world/per-server cumulative stats and metadata
- `advancements.json` - advancement IDs earned per handle

Commands that remove or clear data create backups under:

```text
config/lifetime-stat-tracker/backups/
```

## Counting Notes

The tracker only adds positive deltas for additive stats.

Non-additive stats such as `time_since_death` and `time_since_rest` are excluded from lifetime totals because adding them across worlds would overcount.

Recipe advancements are ignored so recipe unlock spam does not inflate advancement counts.

Automatic per-world detection for unmodded proxy servers is out of scope. The safe model is:

- automatic high-water aggregate tracking for the server
- manual `/lst seed world <name>` for known lower-stat redirected worlds
- `/lst remove world <name>` for correcting accidental duplicate seeds

## Development

Build with Gradle:

```bash
./gradlew build
```

On Windows:

```text
.\gradlew.bat build
```

The built jar is written to:

```text
build/libs/
```

## Project Docs

- `AGENTS.md` - fresh-agent workflow, verification ladder, and checkpoint rules
- `TODO.md` - current state, migration roadmap, and compatibility backlog
- `CHANGELOG.md` - repo-facing engineering history
- `COMPATIBILITY.md` - compile-probed multi-version strategy and remaining smoke gates
- `gradle/compatibility-release-playbook.md` - reusable compatibility-group pipeline plan
- `gradle/version-profiles/README.md` - active profile metadata model
- `gradle/smoke-tests.md` - planned launcher smoke-test gate
- `gradle/modrinth-publishing.md` - planned guarded publishing rules

## License

LGPL-3.0-or-later. See [LICENSE](LICENSE) and
[LICENSE-GPL-3.0](LICENSE-GPL-3.0).
