#!/usr/bin/env python3
r"""
Rebuild Lifetime Stat Tracker JSON files from backed-up Minecraft worlds.

Run this on the server that has access to the world folders, for example:

  python3 rebuild_lifetime_stats.py \
    --worlds-root ~/minecraft/server/worlds \
    --player-uuid 0064f958-f2ee-44fd-b5d9-27934d87bc71 \
    --out ~/lst-rebuild \
    --seed-lst-dir ~/seeded-lifetime-stat-tracker \
    --exclude-world Hardcore_World_14 \
    --exclude-world Shared_Health_Hardcore_18

Then copy the generated JSON files into the Lifetime Stat Tracker data folder:
  Windows: %APPDATA%\LifetimeStatTracker\
  macOS:   ~/Library/Application Support/LifetimeStatTracker/
  Linux:   $XDG_DATA_HOME/lifetime-stat-tracker/ or ~/.local/share/lifetime-stat-tracker/
"""

from __future__ import annotations

import argparse
import json
import os
import re
import shutil
from dataclasses import dataclass
from pathlib import Path
from typing import Any


DEFAULT_PLAYER_UUID = "0064f958-f2ee-44fd-b5d9-27934d87bc71"
RECIPE_MARKER = ":recipes/"


@dataclass(frozen=True)
class WorldSource:
    path: Path
    display_name: str
    handle: str
    stats_path: Path
    advancements_path: Path | None
    mtime_ms: int


def main() -> int:
    args = parse_args()
    worlds_root = args.worlds_root.expanduser().resolve()
    out_dir = args.out.expanduser().resolve()
    player_uuid = args.player_uuid.lower()

    worlds = discover_worlds(
        worlds_root=worlds_root,
        player_uuid=player_uuid,
        handle_prefix=args.handle_prefix,
        include_archive=not args.no_archive,
        exclude_worlds=set(args.exclude_world),
    )

    totals: dict[str, int] = {}
    snapshots: dict[str, dict[str, int]] = {}
    world_stats: dict[str, dict[str, Any]] = {}
    advancements: dict[str, list[str]] = {}
    seeded_handles: set[str] = set()
    if args.seed_lst_dir:
        seeded_handles = seed_lifetime_stats(args.seed_lst_dir.expanduser().resolve(), totals, snapshots, world_stats, advancements)

    for world in worlds:
        flat_stats = flatten_stats(read_json(world.stats_path))
        if not flat_stats:
            print(f"skip empty stats: {world.path}")
            continue

        flat_advancements = read_advancements(world.advancements_path)
        snapshots[world.handle] = flat_stats
        world_stats[world.handle] = {
            "displayName": world.display_name,
            "firstSeen": world.mtime_ms,
            "lastSeen": world.mtime_ms,
            "stats": flat_stats,
        }
        if flat_advancements:
            advancements[world.handle] = sorted(flat_advancements)

        for key, value in flat_stats.items():
            if value > 0 and is_additive_stat(key):
                totals[key] = totals.get(key, 0) + value

    out_dir.mkdir(parents=True, exist_ok=True)
    write_json(out_dir / "totals.json", totals)
    write_json(out_dir / "snapshots.json", snapshots)
    write_json(out_dir / "world_stats.json", world_stats)
    write_json(out_dir / "advancements.json", advancements)
    write_summary(out_dir / "summary.txt", worlds, totals, snapshots, advancements, worlds_root, seeded_handles)

    print(f"wrote LST rebuild to: {out_dir}")
    print(f"worlds imported: {len(snapshots)}")
    print(f"total stat keys: {len(totals)}")
    print(f"unique advancements: {len({adv for advs in advancements.values() for adv in advs})}")
    return 0


def parse_args() -> argparse.Namespace:
    parser = argparse.ArgumentParser(description="Rebuild Lifetime Stat Tracker JSON from Minecraft world stats.")
    parser.add_argument(
        "--worlds-root",
        type=Path,
        required=True,
        help="Path to minecraft/server/worlds.",
    )
    parser.add_argument(
        "--player-uuid",
        default=DEFAULT_PLAYER_UUID,
        help=f"Player UUID without .json/.dat suffix. Default: {DEFAULT_PLAYER_UUID}",
    )
    parser.add_argument(
        "--out",
        type=Path,
        default=Path("./lst-rebuild"),
        help="Output folder for totals/snapshots/world_stats/advancements JSON.",
    )
    parser.add_argument(
        "--handle-prefix",
        default="server-world",
        help="Handle prefix used in snapshots/world_stats. Default: server-world",
    )
    parser.add_argument(
        "--no-archive",
        action="store_true",
        help="Skip worlds under a folder named archive.",
    )
    parser.add_argument(
        "--seed-lst-dir",
        type=Path,
        help="Existing LST data folder to seed from before importing old worlds.",
    )
    parser.add_argument(
        "--exclude-world",
        action="append",
        default=[],
        help="Relative world folder to skip, repeatable. Example: --exclude-world Shared_Health_Hardcore_18",
    )
    return parser.parse_args()


def discover_worlds(
    worlds_root: Path,
    player_uuid: str,
    handle_prefix: str,
    include_archive: bool,
    exclude_worlds: set[str],
) -> list[WorldSource]:
    if not worlds_root.is_dir():
        raise SystemExit(f"worlds root does not exist or is not a directory: {worlds_root}")
    normalized_excludes = {normalize_rel_path(value) for value in exclude_worlds}

    worlds: list[WorldSource] = []
    used_handles: dict[str, int] = {}
    for stats_path in sorted(worlds_root.rglob(f"stats/{player_uuid}.json")):
        world_dir = stats_path.parent.parent
        if not include_archive and "archive" in {part.lower() for part in world_dir.parts}:
            continue

        rel = world_dir.relative_to(worlds_root)
        display_name = str(rel).replace(os.sep, " / ")
        rel_posix = rel.as_posix()
        if normalize_rel_path(rel_posix) in normalized_excludes:
            print(f"exclude active world: {rel_posix}")
            continue
        handle = unique_handle(f"{handle_prefix}:{slugify(rel_posix)}", used_handles)
        advancements_path = world_dir / "advancements" / f"{player_uuid}.json"
        mtime_ms = int(stats_path.stat().st_mtime * 1000)

        worlds.append(
            WorldSource(
                path=world_dir,
                display_name=display_name,
                handle=handle,
                stats_path=stats_path,
                advancements_path=advancements_path if advancements_path.exists() else None,
                mtime_ms=mtime_ms,
            )
        )

    if not worlds:
        print(f"no crawl stats imported for {player_uuid} under {worlds_root}")

    return worlds


def seed_lifetime_stats(
    seed_dir: Path,
    totals: dict[str, int],
    snapshots: dict[str, dict[str, int]],
    world_stats: dict[str, dict[str, Any]],
    advancements: dict[str, list[str]],
) -> set[str]:
    if not seed_dir.is_dir():
        raise SystemExit(f"seed LST dir does not exist or is not a directory: {seed_dir}")

    seed_totals = read_json_if_exists(seed_dir / "totals.json")
    seed_snapshots = read_json_if_exists(seed_dir / "snapshots.json")
    seed_world_stats = read_json_if_exists(seed_dir / "world_stats.json")
    seed_advancements = read_json_if_exists(seed_dir / "advancements.json")

    seeded_handles: set[str] = set()

    totals.update(clean_int_map(seed_totals))
    for handle, stats in seed_snapshots.items():
        if isinstance(handle, str) and isinstance(stats, dict):
            snapshots[handle] = clean_int_map(stats)
            seeded_handles.add(handle)

    for handle, value in seed_world_stats.items():
        if not isinstance(handle, str) or not isinstance(value, dict):
            continue
        display_name = value.get("displayName")
        stats = clean_int_map(value.get("stats", {}))
        world_stats[handle] = {
            "displayName": display_name if isinstance(display_name, str) and display_name else handle,
            "firstSeen": int_or_default(value.get("firstSeen"), 0),
            "lastSeen": int_or_default(value.get("lastSeen"), 0),
            "stats": stats,
        }
        seeded_handles.add(handle)

    for handle, value in seed_advancements.items():
        if isinstance(handle, str) and isinstance(value, list):
            advancements[handle] = sorted(
                adv for adv in value
                if isinstance(adv, str) and not is_recipe_advancement(adv)
            )
            seeded_handles.add(handle)

    print(f"seeded existing LST data from: {seed_dir}")
    server_handles = sorted(handle for handle in snapshots if handle.startswith("server:"))
    if server_handles:
        print("seed server handles: " + ", ".join(server_handles))
    return seeded_handles


def flatten_stats(stats_root: dict[str, Any]) -> dict[str, int]:
    stats = stats_root.get("stats", {})
    if not isinstance(stats, dict):
        return {}

    flat: dict[str, int] = {}
    for stat_type, values in stats.items():
        if not isinstance(values, dict):
            continue
        for stat_value, raw_count in values.items():
            if isinstance(raw_count, bool) or not isinstance(raw_count, int):
                continue
            if raw_count < 0:
                continue
            flat[f"{stat_type}:{stat_value}"] = raw_count
    return dict(sorted(flat.items()))


def clean_int_map(value: Any) -> dict[str, int]:
    if not isinstance(value, dict):
        return {}
    cleaned: dict[str, int] = {}
    for key, raw_count in value.items():
        if not isinstance(key, str):
            continue
        if isinstance(raw_count, bool) or not isinstance(raw_count, int):
            continue
        if raw_count < 0:
            continue
        cleaned[key] = raw_count
    return dict(sorted(cleaned.items()))


def read_advancements(path: Path | None) -> set[str]:
    if path is None:
        return set()

    root = read_json(path)
    earned: set[str] = set()
    for advancement_id, value in root.items():
        if advancement_id == "DataVersion":
            continue
        if not isinstance(advancement_id, str) or is_recipe_advancement(advancement_id):
            continue
        if isinstance(value, dict) and "done" in value:
            earned.add(advancement_id)
    return earned


def is_additive_stat(key: str) -> bool:
    return key not in {
        "minecraft:custom:minecraft:time_since_death",
        "minecraft:custom:minecraft:time_since_rest",
    }


def is_recipe_advancement(advancement_id: str) -> bool:
    return RECIPE_MARKER in advancement_id


def slugify(value: str) -> str:
    value = value.replace(os.sep, "/")
    value = value.strip().lower()
    value = re.sub(r"[^a-z0-9._/-]+", "_", value)
    value = value.replace("/", "__")
    value = re.sub(r"_+", "_", value)
    return value.strip("_") or "world"


def unique_handle(base_handle: str, used_handles: dict[str, int]) -> str:
    count = used_handles.get(base_handle, 0)
    used_handles[base_handle] = count + 1
    if count == 0:
        return base_handle
    return f"{base_handle}_{count + 1}"


def normalize_rel_path(value: str) -> str:
    return value.replace("\\", "/").strip().strip("/")


def read_json(path: Path) -> dict[str, Any]:
    with path.open("r", encoding="utf-8") as f:
        data = json.load(f)
    if not isinstance(data, dict):
        raise ValueError(f"expected JSON object in {path}")
    return data


def read_json_if_exists(path: Path) -> dict[str, Any]:
    if not path.exists():
        return {}
    return read_json(path)


def int_or_default(value: Any, default: int) -> int:
    if isinstance(value, bool):
        return default
    if isinstance(value, int):
        return value
    return default


def write_json(path: Path, data: Any) -> None:
    tmp = path.with_suffix(path.suffix + ".tmp")
    with tmp.open("w", encoding="utf-8") as f:
        json.dump(data, f, indent=2, sort_keys=True)
        f.write("\n")
    shutil.move(tmp, path)


def write_summary(
    path: Path,
    worlds: list[WorldSource],
    totals: dict[str, int],
    snapshots: dict[str, dict[str, int]],
    advancements: dict[str, list[str]],
    worlds_root: Path,
    seeded_handles: set[str],
) -> None:
    unique_advancements = {adv for advs in advancements.values() for adv in advs}
    with path.open("w", encoding="utf-8") as f:
        f.write(f"worlds_root: {worlds_root}\n")
        f.write(f"worlds_found: {len(worlds)}\n")
        f.write(f"worlds_imported: {len(snapshots)}\n")
        f.write(f"total_stat_keys: {len(totals)}\n")
        f.write(f"unique_advancements: {len(unique_advancements)}\n\n")
        if seeded_handles:
            f.write("seeded handles:\n")
            for handle in sorted(seeded_handles):
                f.write(f"  [seed] {handle}\n")
            f.write("\n")
        f.write("key totals:\n")
        for key in [
            "minecraft:custom:minecraft:play_time",
            "minecraft:custom:minecraft:walk_one_cm",
            "minecraft:custom:minecraft:jump",
            "minecraft:custom:minecraft:mob_kills",
            "minecraft:custom:minecraft:deaths",
        ]:
            f.write(f"  {key}: {totals.get(key, 0)}\n")
        f.write("\nworlds:\n")
        for world in worlds:
            imported = "yes" if world.handle in snapshots else "no"
            f.write(f"  [{imported}] {world.handle} -> {world.display_name}\n")


if __name__ == "__main__":
    raise SystemExit(main())
