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
separate local record in your config folder, so play time, deaths, distance
travelled, mob kills, jumps, and advancement progress can keep building over
time.

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
- Local JSON storage with backups for destructive commands

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

The mod does not require a server install to be useful. Client-only installs
work for singleplayer, LAN, Realms, and multiplayer, but unmodded servers and
Realms cannot reliably expose their internal world identity to the client. In
that case, Lifetime Stat Tracker uses a safe aggregate server entry.

For accurate per-world tracking on servers with resets, multiple worlds, or
proxy routing, install the mod on the Fabric server as well.
```
