#!/usr/bin/env python3
"""Give the packaged OneConfig/Compose stack a Floyd-private JVM namespace.

Run after Loom remapping, so Minecraft names and mixin remapping remain Loom's
responsibility. Native Skiko/JNI packages deliberately retain their ABI names.
Uses only Python's standard library; never edits source or installed profiles.
"""
import argparse
import hashlib
import io
import json
import os
from pathlib import Path
import re
import struct
import zipfile

PACKAGES = ("androidx", "org.jetbrains.compose", "org.polyfrost.oneconfig")
PREFIX = "gg.floyd.internal.uilibs."


def private_id(value):
    result = "floyd_ui_" + value
    return result if len(result) <= 64 else result[:47] + "_" + hashlib.sha256(value.encode()).hexdigest()[:16]


def owned_id(value):
    return value in {"oneconfig", "oneconfigv1", "compose-multiplatform"} or value.startswith(
        ("androidx_", "org_jetbrains_compose_", "org_jetbrains_androidx_", "org_polyfrost_oneconfig_")
    )


def relocate(data):
    for package in PACKAGES:
        for separator in (".", "/"):
            old = (package.replace(".", separator) + separator).encode()
            new = (PREFIX.replace(".", separator)).encode() + old
            # Already-private entries must remain stable on a repeated invocation.
            prefix = PREFIX.replace(".", separator).encode()
            data = re.sub(rb"(?<!" + re.escape(prefix) + rb")" + re.escape(old), lambda _: new, data)
    return re.sub(rb"(?<!floyd_ui_)oneconfigv1", b"floyd_ui_oneconfigv1", data)


def rewrite_class(data):
    """Rewrite UTF8 constants, preserving bytecode, indexes and modified UTF8.

    Descriptors, annotations, class names and reflection strings all live in the
    constant pool. No decoding is needed (Java modified UTF8 differs from UTF8).
    """
    if data[:4] != b"\xca\xfe\xba\xbe":
        raise ValueError("Invalid class file")
    count = struct.unpack_from(">H", data, 8)[0]
    output = bytearray(data[:10])
    pos, index = 10, 1
    sizes = {3: 4, 4: 4, 5: 8, 6: 8, 7: 2, 8: 2, 9: 4, 10: 4,
             11: 4, 12: 4, 15: 3, 16: 2, 17: 4, 18: 4, 19: 2, 20: 2}
    while index < count:
        tag = data[pos]
        output.append(tag)
        pos += 1
        if tag == 1:
            length = struct.unpack_from(">H", data, pos)[0]
            value = data[pos + 2:pos + 2 + length]
            value = relocate(value)
            # OneConfig looks up its own Fabric container by literal ID.
            text = value.decode("ascii", errors="ignore")
            if value == text.encode() and owned_id(text):
                value = private_id(text).encode()
            output.extend(struct.pack(">H", len(value)))
            output.extend(value)
            pos += 2 + length
        else:
            size = sizes.get(tag)
            if size is None:
                raise ValueError(f"Unknown constant pool tag {tag}")
            output.extend(data[pos:pos + size])
            pos += size
            if tag in (5, 6):
                index += 1
        index += 1
    output.extend(data[pos:])
    return bytes(output)


def metadata(value):
    if isinstance(value, dict):
        return {private_id(k) if owned_id(k) else k: metadata(v) for k, v in value.items()}
    if isinstance(value, list):
        return [metadata(v) for v in value]
    if isinstance(value, str):
        return private_id(value) if owned_id(value) else relocate(value.encode()).decode()
    return value


def rewrite_archive(data, root=True):
    output = io.BytesIO()
    with zipfile.ZipFile(io.BytesIO(data)) as source, zipfile.ZipFile(output, "w", zipfile.ZIP_DEFLATED) as target:
        if not root:
            mod_id = json.loads(source.read("fabric.mod.json")).get("id", "") if "fabric.mod.json" in source.namelist() else ""
            # OneConfig also loads a ModMenu shim JAR without Fabric metadata.
            owns_classes = any(name.startswith(tuple(p.replace(".", "/") + "/" for p in PACKAGES))
                               for name in source.namelist())
            if not owned_id(mod_id) and not mod_id.startswith("floyd_ui_") and not owns_classes:
                # Shared non-UI libraries (especially Skiko and its JNI binaries)
                # retain their original bytes, signatures, names and metadata.
                return data
        seen = set()
        for entry in source.infolist():
            name = entry.filename
            upper = name.upper()
            # Signatures no longer describe modified bytes. Kotlin module mapping
            # protobufs are compile-time indexes, unused by packaged client code.
            if name.endswith(".kotlin_module") or (upper.startswith("META-INF/") and upper.endswith((".SF", ".RSA", ".DSA"))):
                continue
            content = source.read(entry)
            if name.endswith(".jar"):
                content = rewrite_archive(content, root=False)
            elif name.endswith(".class"):
                content = rewrite_class(content)
            elif name == "fabric.mod.json":
                content = (json.dumps(metadata(json.loads(content)), indent=2) + "\n").encode()
            elif name.endswith((".json", ".accesswidener", ".classtweaker", ".properties", ".xml")) or name.startswith("META-INF/services/"):
                content = relocate(content)
            name = relocate(name.encode()).decode()
            # Mixin config/resource names are shared across all Fabric containers.
            # Rename OneConfig-specific resources and every reference to them.
            if name in seen:
                raise ValueError(f"Duplicate relocated entry: {name}")
            seen.add(name)
            entry.filename = name
            entry.compress_type = zipfile.ZIP_DEFLATED
            target.writestr(entry, content)
    return output.getvalue()


def verify_archive(data):
    mods, dependencies = set(), set()
    private_classes = 0

    def walk(content):
        nonlocal private_classes
        with zipfile.ZipFile(io.BytesIO(content)) as archive:
            names = set(archive.namelist())
            if "fabric.mod.json" in names:
                mod = json.loads(archive.read("fabric.mod.json"))
                mod_id = mod["id"]
                if owned_id(mod_id):
                    raise ValueError(f"Unisolated UI mod ID: {mod_id}")
                mods.add(mod_id)
                for nested in mod.get("jars", []):
                    if nested["file"] not in names:
                        raise ValueError(f"Missing nested JAR: {nested['file']}")
                if mod_id == "floydaddons" or mod_id.startswith("floyd_ui_"):
                    dependencies.update(d for d in mod.get("depends", {}) if d.startswith("floyd_ui_"))
                    for mixin in mod.get("mixins", []):
                        resource = mixin if isinstance(mixin, str) else mixin["config"]
                        if resource not in names:
                            raise ValueError(f"Missing mixin resource: {resource}")
            for name in names:
                if name.endswith(".jar"):
                    walk(archive.read(name))
                elif name.endswith(".class"):
                    if name.startswith(tuple(p.replace(".", "/") + "/" for p in PACKAGES)):
                        raise ValueError(f"Unisolated UI class: {name}")
                    private_classes += name.startswith(PREFIX.replace(".", "/"))

    walk(data)
    if dependencies - mods:
        raise ValueError(f"Missing private UI dependencies: {sorted(dependencies - mods)}")
    if not private_classes:
        raise ValueError("No private UI classes found")
    return private_classes


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("jar", type=Path)
    parser.add_argument("--output", type=Path)
    args = parser.parse_args()
    destination = args.output or args.jar
    result = rewrite_archive(args.jar.read_bytes())
    count = verify_archive(result)
    temporary = destination.with_suffix(".isolating.tmp")
    temporary.write_bytes(result)
    os.replace(temporary, destination)
    print(f"Isolated Floyd UI libraries ({count} private classes verified): {destination}")


if __name__ == "__main__":
    main()
