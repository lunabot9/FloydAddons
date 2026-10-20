#!/usr/bin/env python3
"""Reject local-only extensions in public Git history and release archives."""
import argparse
import io
import subprocess
import sys
import zipfile
from pathlib import Path

NAMESPACE = b"gg/floyd/" + b"polinex"
MARKERS = (NAMESPACE, NAMESPACE.replace(b"/", b"."))
SOURCE_SUFFIXES = {".kt", ".java", ".json", ".class"}
# Last reviewed public commit before any local-only extensions existed.
PUBLIC_BASELINE = "5d1f671f3175e6c96eba7aac880ff396b4251623"


def check_path(name):
    name = name.replace("\\", "/").lower()
    if "polinex" in name or "secretsequence" in name:
        raise ValueError(f"Local-only extension path: {name}")


def check_content(name, data):
    check_path(name)
    if Path(name).suffix.lower() in SOURCE_SUFFIXES and any(m in data.lower() for m in MARKERS):
        raise ValueError(f"Local-only extension reference: {name}")


def check_archive(name, data, depth=0):
    if depth > 8:
        raise ValueError(f"Archive nesting exceeds inspection limit: {name}")
    with zipfile.ZipFile(io.BytesIO(data)) as archive:
        for entry in archive.infolist():
            check_path(entry.filename)
            if Path(entry.filename).suffix.lower() in SOURCE_SUFFIXES:
                check_content(entry.filename, archive.read(entry))
            elif entry.filename.lower().endswith('.jar'):
                check_archive(entry.filename, archive.read(entry), depth + 1)


def git(*args):
    return subprocess.check_output(["git", *args])


def check_ref(ref):
    """Inspect reachable history beyond the reviewed public baseline, including deletions."""
    objects = git("rev-list", "--objects", ref, "--not", PUBLIC_BASELINE).decode().splitlines()
    selected = []
    for line in objects:
        oid, _, name = line.partition(" ")
        if not name:
            continue
        check_path(name)
        suffix = Path(name).suffix.lower()
        wrapper = name == "gradle/wrapper/gradle-wrapper.jar" or name.endswith("/gradle/wrapper/gradle-wrapper.jar")
        if suffix in {".jar", ".bundle"} and not wrapper:
            raise ValueError(f"Binary distribution must not be committed: {name}")
        if suffix in SOURCE_SUFFIXES or wrapper:
            selected.append((oid, name))
    proc = subprocess.Popen(["git", "cat-file", "--batch"], stdin=subprocess.PIPE, stdout=subprocess.PIPE)
    try:
        for oid, name in selected:
            proc.stdin.write((oid + "\n").encode())
            proc.stdin.flush()
            header = proc.stdout.readline().split()
            if len(header) != 3 or header[1] != b"blob":
                raise ValueError(f"Cannot inspect Git object: {name}")
            data = proc.stdout.read(int(header[2]))
            if proc.stdout.read(1) != b"\n":
                raise ValueError("Incomplete Git object response")
            if name.endswith(".jar"):
                check_archive(name, data)
            else:
                check_content(name, data)
    finally:
        proc.stdin.close()
        proc.stdout.close()
        proc.wait()
    print(f"Public history verified: {ref} ({len(selected)} source blobs)")


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--ref", action="append", default=[])
    parser.add_argument("--pre-push", action="store_true")
    parser.add_argument("--artifacts", nargs="*", default=[])
    args = parser.parse_args()
    refs = args.ref
    if args.pre_push:
        for line in sys.stdin:
            local_ref, local_sha, remote_ref, remote_sha = line.split()
            if set(local_sha) != {"0"}:
                refs.append(local_sha)
    if not refs and not args.pre_push and not args.artifacts:
        refs = ["HEAD"]
    for ref in dict.fromkeys(refs):
        check_ref(ref)
    for name in args.artifacts:
        path = Path(name)
        check_path(path.name)
        check_archive(path.name, path.read_bytes())
        print(f"Public archive verified: {path.name}")


if __name__ == "__main__":
    try:
        main()
    except (ValueError, OSError, subprocess.CalledProcessError, zipfile.BadZipFile) as error:
        print(f"PUBLIC DISTRIBUTION BLOCKED: {error}", file=sys.stderr)
        sys.exit(1)
