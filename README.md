# Lifetime Stat Tracker

A **client-side Fabric mod** that tracks your Minecraft lifetime stats across worlds and servers.

Unlike vanilla per-world stats, Lifetime Stat Tracker keeps cumulative totals in your config folder so your progress is preserved even if worlds are renamed, deleted, or moved.

## Features

- 📊 **Lifetime totals** for key stats (play time, distance walked, jumps, mob kills, deaths)
- 🌍 **Per-world / per-server tracking** with a breakdown by handle
- 🏆 **Advancement tracking** across all tracked worlds (recipe unlocks excluded)
- 💾 **Persistent JSON storage** in your Fabric config directory
- ⌨️ **Client commands** with ` /lifetimestats ` and short alias ` /lst `
- 🔄 **Automatic stat requests** on join and periodically while playing

## Supported Environment

- **Minecraft:** `1.21.11`
- **Loader:** Fabric Loader `0.18.4+`
- **Fabric API:** `0.140.2+1.21.11`
- **Java:** `21+`
- **Side:** Client-only

## Installation

1. Install **Fabric Loader** for Minecraft 1.21.11.
2. Install **Fabric API** in your `mods` folder.
3. Place the Lifetime Stat Tracker `.jar` in your `mods` folder.
4. Launch the game.

## Commands

Both command roots are equivalent:

- `/lifetimestats`
- `/lst`

Subcommands:

- `/lst` — Summary of lifetime totals
- `/lst time` — Total play time + per-world breakdown
- `/lst worlds` — List all tracked worlds/servers
- `/lst world <name>` — Detailed stats for a specific world/server (partial match)
- `/lst advancements` — Advancement totals and details
- `/lst current` — Show current tracked handle
- `/lst help` — Show command help

## How Tracking Works

- On world/server join, the mod computes a **handle**:
  - Multiplayer: `server:<ip:port>`
  - Singleplayer: `local:<levelName>:<seed>`
- When stat packets arrive:
  - First time for a handle: current snapshot is used as baseline and added to totals
  - Subsequent packets: only positive deltas are added
- Advancements are tracked when marked complete in the client advancement state.

## Data Files

Data is stored under:

- `.minecraft/config/lifetime-stat-tracker/`

Files:

- `totals.json` — Lifetime cumulative stat totals
- `snapshots.json` — Last absolute snapshot per handle (for delta calc)
- `world_stats.json` — Per-world/per-server cumulative stats and metadata
- `advancements.json` — Advancement IDs earned per handle

## Notes

- This is a **client-side tracker** and does not require server installation.
- Stats are only updated when Minecraft provides stat packets (the mod requests them automatically with cooldown).
- Existing worlds/servers are tracked once you join with the mod installed.

## Development

Build with Gradle:

```bash
./gradlew build
```

Output jar will be in:

- `build/libs/`

## License

MIT (see [LICENSE](LICENSE)).
