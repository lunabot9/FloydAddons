#!/usr/bin/env python3
from __future__ import annotations

import argparse
import json
import zipfile
from pathlib import Path
from typing import Any

ROOT = Path(__file__).resolve().parents[1]
DEFAULT_SOURCE_ZIP = Path(r"C:/Users/gobsi/Downloads/SkyBlock Legacy (1).zip")
TARGET_PACK_ZIP = ROOT / "src/main/resources/floyd_skyblock_pack_fallback.zip"
TARGET_ITEM_JSON = ROOT / "src/main/resources/floyd_skyblock_items.json"

INCLUDED_OVERLAY_ROOTS = (
    "legacy_abiphone",
    "legacy_accessory",
    "legacy_arrow",
    "legacy_arrow_poison",
    "legacy_axe",
    "legacy_bait",
    "legacy_booster",
    "legacy_bow",
    "legacy_capsule",
    "legacy_chisel",
    "legacy_drill",
    "legacy_dwarven_metal",
    "legacy_equipment",
    "legacy_farming_tool",
    "legacy_fishing_net",
    "legacy_fishing_rod",
    "legacy_garden_chip",
    "legacy_gemstone",
    "legacy_lasso",
    "legacy_pet_item",
    "legacy_pickaxe",
    "legacy_reforge_stone",
    "legacy_rod_part",
    "legacy_salt",
    "legacy_shovel",
    "legacy_sword",
    "legacy_trap",
    "legacy_trophy",
    "legacy_uncategorized",
    "legacy_vacuum",
    "legacy_wand",
    "legacy_watering_can",
    "vanilla_colors",
    "vanilla_tooltips",
)
ROOT_FILES = ("pack.mcmeta", "pack.png")
ITEM_PREFIX = "assets/hypixel_skyblock/items/"
MODEL_PREFIX = "assets/hypixel_skyblock/models/"


def parse_args() -> argparse.Namespace:
    parser = argparse.ArgumentParser(
        description="Rebuild Floyd's bundled Hypixel SkyBlock fallback pack from a SkyBlock Legacy zip."
    )
    parser.add_argument(
        "--source-zip",
        type=Path,
        default=DEFAULT_SOURCE_ZIP,
        help=f"Path to the SkyBlock Legacy zip (default: {DEFAULT_SOURCE_ZIP})",
    )
    return parser.parse_args()


def is_safe_entry_name(name: str) -> bool:
    return bool(name) and not name.startswith("/") and "\\" not in name and ".." not in name.split("/")


def entry_destination(name: str) -> str | None:
    if not is_safe_entry_name(name):
        return None
    if "/" not in name:
        return name if name in ROOT_FILES else None
    if name.startswith("assets/"):
        return name
    root, remainder = name.split("/", 1)
    if root not in INCLUDED_OVERLAY_ROOTS or not remainder.startswith("assets/"):
        return None
    return remainder


def read_merged_entries(source_zip: Path) -> dict[str, bytes]:
    merged: dict[str, bytes] = {}
    with zipfile.ZipFile(source_zip) as archive:
        for file_name in ROOT_FILES:
            try:
                merged[file_name] = archive.read(file_name)
            except KeyError:
                pass

        for name in archive.namelist():
            destination = entry_destination(name)
            if destination is None or destination in ROOT_FILES:
                continue
            if name.endswith("/"):
                continue
            merged[destination] = archive.read(name)
    return merged


def write_pack_zip(entries: dict[str, bytes], target_zip: Path) -> None:
    with zipfile.ZipFile(target_zip, "w", compression=zipfile.ZIP_DEFLATED) as archive:
        for name in sorted(entries):
            archive.writestr(name, entries[name])


def load_json_bytes(entries: dict[str, bytes], prefix: str) -> dict[str, Any]:
    loaded: dict[str, Any] = {}
    for name, payload in entries.items():
        if not name.startswith(prefix) or not name.endswith(".json"):
            continue
        loaded[name] = json.loads(payload.decode("utf-8"))
    return loaded


def find_model_reference(element: Any) -> str | None:
    if isinstance(element, dict):
        model = element.get("model")
        if isinstance(model, str) and ":" in model:
            return model
        for value in element.values():
            found = find_model_reference(value)
            if found:
                return found
    elif isinstance(element, list):
        for value in element:
            found = find_model_reference(value)
            if found:
                return found
    return None


def is_head_definition(element: Any) -> bool:
    if isinstance(element, dict):
        if element.get("type") == "minecraft:head":
            return True
        base = element.get("base")
        if base == "minecraft:item/template_skull":
            return True
        model = element.get("model")
        if model == "minecraft:item/template_skull":
            return True
        return any(is_head_definition(value) for value in element.values())
    if isinstance(element, list):
        return any(is_head_definition(value) for value in element)
    return False


def parse_identifier(identifier: str) -> tuple[str, str] | None:
    if ":" not in identifier:
        return None
    namespace, path = identifier.split(":", 1)
    if not namespace or not path:
        return None
    return namespace, path


def resolve_vanilla_parent(initial_model: str, model_parents: dict[str, str]) -> str | None:
    current = initial_model
    visited: set[str] = set()
    for _ in range(32):
        if current in visited:
            return None
        visited.add(current)
        parsed = parse_identifier(current)
        if parsed is None:
            return None
        namespace, _ = parsed
        if namespace == "minecraft":
            return current
        current = model_parents.get(current, "")
        if not current:
            return None
    return None


def normalize_model(identifier: str) -> str:
    namespace, path = parse_identifier(identifier) or ("", "")
    if namespace == "minecraft" and path.startswith("item/"):
        return f"{namespace}:{path.removeprefix('item/')}"
    return identifier


def skyblock_id_from_item_path(item_path: str) -> str:
    return Path(item_path).stem.upper()


def regenerate_item_table(entries: dict[str, bytes], target_json: Path) -> tuple[int, int, list[str]]:
    item_definitions = load_json_bytes(entries, ITEM_PREFIX)
    model_definitions = load_json_bytes(entries, MODEL_PREFIX)

    model_parents: dict[str, str] = {}
    for name, root in model_definitions.items():
        model_id = f"hypixel_skyblock:{name.removeprefix(MODEL_PREFIX).removesuffix('.json')}"
        parent = root.get("parent") if isinstance(root, dict) else None
        if isinstance(parent, str) and ":" in parent:
            model_parents[model_id] = parent

    with target_json.open("r", encoding="utf-8") as handle:
        existing = json.load(handle)

    updated = dict(existing)
    refreshed = 0
    added = 0
    unresolved_heads: list[str] = []

    for name, root in item_definitions.items():
        skyblock_id = skyblock_id_from_item_path(name)
        if is_head_definition(root):
            if skyblock_id not in existing:
                updated[skyblock_id] = {
                    "model": f"hypixel_skyblock:{name.removeprefix(ITEM_PREFIX).removesuffix('.json')}"
                }
                added += 1
            continue

        model_ref = find_model_reference(root)
        if not model_ref:
            continue

        resolved = resolve_vanilla_parent(model_ref, model_parents)
        if not resolved:
            continue

        entry = {"model": normalize_model(resolved)}
        had_existing = skyblock_id in updated
        existing_entry = updated.get(skyblock_id)
        if isinstance(existing_entry, dict) and "texture" in existing_entry:
            continue
        updated[skyblock_id] = entry
        if had_existing:
            refreshed += 1
        else:
            added += 1

    ordered = {key: updated[key] for key in sorted(updated)}
    with target_json.open("w", encoding="utf-8", newline="\n") as handle:
        json.dump(ordered, handle, indent=2)
        handle.write("\n")

    unresolved_heads.sort()
    return refreshed, added, unresolved_heads


def main() -> int:
    args = parse_args()
    source_zip = args.source_zip
    if not source_zip.is_file():
        raise FileNotFoundError(f"SkyBlock Legacy zip not found: {source_zip}")

    merged_entries = read_merged_entries(source_zip)
    write_pack_zip(merged_entries, TARGET_PACK_ZIP)
    refreshed, added, unresolved_heads = regenerate_item_table(merged_entries, TARGET_ITEM_JSON)

    print(f"Rebuilt {TARGET_PACK_ZIP.name} with {len(merged_entries)} merged entries from {source_zip.name}.")
    print(f"Refreshed {refreshed} existing item mappings and added {added} new non-head mappings.")
    print("Preserved existing custom-head payloads and mapped new head ids back to bundled hypixel models.")
    print(f"{len(unresolved_heads)} head ids still need manual follow-up.")
    if unresolved_heads:
        print("Unresolved head ids:")
        for skyblock_id in unresolved_heads:
            print(f"  {skyblock_id}")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
