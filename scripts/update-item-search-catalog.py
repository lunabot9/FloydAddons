"""Regenerate the offline SkyBlock search catalog from a pinned NEU data revision."""
import io
import json
import pathlib
import re
import urllib.request
import zipfile

ROOT = pathlib.Path(__file__).resolve().parents[1]
API = "https://api.github.com/repos/NotEnoughUpdates/NotEnoughUpdates-REPO/commits/master"


def fetch(url):
    with urllib.request.urlopen(urllib.request.Request(url, headers={"User-Agent": "FloydAddons-catalog"}), timeout=120) as response:
        return response.read()


def extra_attributes(nbt):
    """Keep SkyBlock metadata, omitting old SNBT list index prefixes outside strings."""
    start = re.search(r'ExtraAttributes\s*:\s*\{', nbt)
    if not start:
        return "{}"
    begin = start.end() - 1
    depth = 0
    quoted = escaped = False
    output = []
    pos = begin
    while pos < len(nbt):
        char = nbt[pos]
        if not quoted and char.isdigit() and output and output[-1] in '[,':
            index = re.match(r'\d+:', nbt[pos:])
            if index:
                pos += len(index.group())
                continue
        output.append(char)
        if quoted:
            if escaped:
                escaped = False
            elif char == '\\':
                escaped = True
            elif char == '"':
                quoted = False
        elif char == '"':
            quoted = True
        elif char == '{':
            depth += 1
        elif char == '}':
            depth -= 1
            if depth == 0:
                return ''.join(output)
        pos += 1
    raise ValueError('Unclosed ExtraAttributes compound')


def main():
    revision = json.loads(fetch(API))["sha"]
    archive = zipfile.ZipFile(io.BytesIO(fetch(f"https://codeload.github.com/NotEnoughUpdates/NotEnoughUpdates-REPO/zip/{revision}")))
    entries = []
    for name in sorted(archive.namelist()):
        if not re.fullmatch(r"[^/]+/items/[^/]+\.json", name):
            continue
        data = json.loads(archive.read(name))
        nbt = data.get("nbttag", "")
        texture = re.search(r'Value:\s*"([A-Za-z0-9+/=]+)"', nbt)
        model = re.search(r'ItemModel:\s*"([^"]+)"', nbt)
        dye = re.search(r'(?:\{|,)\s*color:\s*(-?\d+)(?=[,}])', nbt)
        entries.append(dict(id=data.get("internalname", pathlib.PurePosixPath(name).stem),
                            name=data.get("displayname", ""), item=data.get("itemid", "minecraft:paper"),
                            damage=data.get("damage", 0), lore=data.get("lore", []),
                            model=model.group(1) if model else None,
                            texture=texture.group(1) if texture else None,
                            dyeColor=(int(dye.group(1)) & 0xFFFFFF) if dye else None,
                            extraAttributes=extra_attributes(nbt),
                            recipe=data.get("recipe", {}), count=data.get("count", 1),
                            recipes=data.get("recipes", []), crafttext=data.get("crafttext", ""),
                            info=data.get("info", [])))
    destination = ROOT / "src/main/resources/assets/floydaddons/data/item_search.json"
    mobs = {"neurepo:mobs/" + pathlib.PurePosixPath(name).name: json.loads(archive.read(name))
            for name in archive.namelist() if re.fullmatch(r"[^/]+/mobs/[^/]+\.json", name)}
    petnums = json.loads(archive.read(next(name for name in archive.namelist() if name.endswith("/constants/petnums.json"))))
    destination.write_text(json.dumps(dict(revision=revision, items=entries, mobs=mobs, petnums=petnums), ensure_ascii=False, separators=(",", ":")), encoding="utf-8")
    license_name = next(name for name in archive.namelist() if name.endswith("/LICENSE"))
    destination.with_name("item_search-LICENSE.txt").write_bytes(archive.read(license_name))
    print(f"Wrote {len(entries)} items from {revision} ({destination.stat().st_size:,} bytes)")


if __name__ == "__main__":
    main()
