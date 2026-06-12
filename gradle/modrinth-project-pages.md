# Modrinth Project Pages

This file is the source-of-truth copy for public Modrinth project summaries and
description pages. Update it before changing live Modrinth project metadata.

## Lifetime Stat Tracker

- Modrinth project name: `Lifetime Stat Tracker`
- Project ID: `rJCvFZKh`
- Summary:

Track lifetime Minecraft stats across worlds, saves, Realms, servers, and resets.

### Description Markdown

```markdown
Lifetime Stat Tracker is a lightweight Fabric mod that keeps long-term player
statistics across worlds, servers, Realms, deleted saves, and resets.

Minecraft normally stores stats per world. If a world is renamed, replaced,
deleted, or reset, that history can disappear. Lifetime Stat Tracker keeps a
separate local record in a shared per-user Tempest Studios data folder outside
`.minecraft`, with data separated by Minecraft instance and player profile, so
play time, deaths, distance travelled, mob kills, jumps, and advancement
progress can keep building over time without merging unrelated accounts or
same-named worlds from different installations.

It works client-side for singleplayer, LAN, Realms, and multiplayer servers.
For unmodded servers, it tracks a safe server-level aggregate so stats are not
double-counted when the client cannot see the server's internal world identity.
For accurate per-world tracking on Fabric servers with resets, multiple worlds,
or proxy-style routing, install the mod on the server as well so the server can
send the client a reliable world identity.

## Features

- Persistent lifetime stats across worlds, saves, servers, Realms, and resets
- Per-world and per-server stat breakdowns
- Tracks play time, deaths, distance, jumps, mob kills, and more
- Tracks earned advancements while ignoring recipe unlock spam
- Client-only support for singleplayer, Realms, LAN, and multiplayer
- Optional server install for reliable per-world Fabric server identity
- Manual tools for correcting ambiguous unmodded server tracking
- Launcher-agnostic shared JSON storage scoped by instance and player profile
  with backups for destructive commands

## Good for

- Players who want long-term stat history
- Hardcore and challenge worlds
- Seasonal or reset-heavy singleplayer worlds
- Singleplayer world archives
- Long-running single-world servers
- Fabric servers that install the mod server-side for cleaner per-world tracking

## Install note

Install Fabric Loader, Fabric API, and the Lifetime Stat Tracker jar that
matches your Minecraft version.

Lifetime Stat Tracker reads the stats Minecraft sends to your client and saves
JSON history in a shared per-user Tempest Studios data folder outside
`.minecraft`:

```text
Windows: %APPDATA%\TempestStudios\Lifetime-Stat-Tracker\
macOS:   ~/Library/Application Support/TempestStudios/Lifetime-Stat-Tracker/
Linux:   $XDG_DATA_HOME/tempest-studios/lifetime-stat-tracker/ or ~/.local/share/tempest-studios/lifetime-stat-tracker/
```

This keeps one lifetime history across different launchers and launcher
instances without merging different Minecraft accounts or same-named worlds from
different game directories. JSON files are stored under
`instances/<instance>/profiles/<player profile>/` inside the shared root.

When upgrading from older storage layouts, the mod copies existing data into the
active instance/profile folder on first run if that folder is still empty. It
checks unnamespaced shared-folder data first, then `2.8.0` app-data, then older
`.minecraft/config/lifetime-stat-tracker/` data as a fallback. Each legacy
source is auto-imported only once, and old files are left in place as a backup.

The mod does not require a server install to be useful. Client-only installs
work for singleplayer, LAN, Realms, and multiplayer, but unmodded servers and
Realms cannot reliably expose their internal world identity to the client. In
that case, Lifetime Stat Tracker uses a safe aggregate server entry.

For accurate per-world tracking on servers with resets, multiple worlds, or
proxy routing, install the mod on the Fabric server as well.
```
