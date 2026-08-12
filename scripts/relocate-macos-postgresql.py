#!/usr/bin/env python3
"""Make a Homebrew/EDB PostgreSQL runtime relocatable inside DSE ERP.app on macOS.

Copies non-system dylib dependencies into runtime/postgresql/lib/dse-deps and rewrites
Mach-O load commands to @loader_path-relative paths. This prevents packaged binaries
from retaining build-machine references such as /opt/homebrew/Cellar/....
"""
from __future__ import annotations

import argparse
import hashlib
import os
from pathlib import Path
import shutil
import subprocess
import sys

SYSTEM_PREFIXES = ("/System/Library/", "/usr/lib/")
FORBIDDEN_PREFIXES = (
    "/opt/homebrew/Cellar/", "/usr/local/Cellar/",
    "/opt/homebrew/opt/", "/usr/local/opt/",
)


def run(*args: str, check: bool = True) -> subprocess.CompletedProcess[str]:
    return subprocess.run(
        args,
        text=True,
        encoding="utf-8",
        errors="replace",
        stdout=subprocess.PIPE,
        stderr=subprocess.PIPE,
        check=check,
    )


def is_macho(path: Path) -> bool:
    if not path.is_file() or path.is_symlink():
        return False
    result = run("file", "-b", str(path), check=False)
    return result.returncode == 0 and "Mach-O" in result.stdout


def deps(path: Path) -> list[str]:
    result = run("otool", "-L", str(path))
    out: list[str] = []
    for line in result.stdout.splitlines()[1:]:
        line = line.strip()
        if not line:
            continue
        out.append(line.split(" (compatibility version", 1)[0].strip())
    return out


def content_key(path: Path) -> str:
    h = hashlib.sha256()
    with path.open("rb") as fh:
        for chunk in iter(lambda: fh.read(1024 * 1024), b""):
            h.update(chunk)
    return h.hexdigest()[:10]


def copy_dependency(source: Path, deps_dir: Path) -> Path:
    real = source.resolve()
    deps_dir.mkdir(parents=True, exist_ok=True)
    target = deps_dir / real.name
    if target.exists():
        try:
            if target.stat().st_size == real.stat().st_size and content_key(target) == content_key(real):
                return target
        except OSError:
            pass
        target = deps_dir / f"{real.stem}-{content_key(real)}{real.suffix}"
    if not target.exists():
        shutil.copy2(real, target)
        target.chmod(target.stat().st_mode | 0o755)
    return target


def loader_reference(owner: Path, target: Path) -> str:
    rel = os.path.relpath(target, owner.parent).replace(os.sep, "/")
    return "@loader_path/" + rel


def resolve_dep(dep: str, owner: Path, source_prefix: Path, bundle_root: Path, deps_dir: Path) -> Path | None:
    if dep.startswith(SYSTEM_PREFIXES):
        return None

    # A dependency already rewritten to a relative location needs no copying here.
    if dep.startswith("@loader_path/"):
        candidate = (owner.parent / dep[len("@loader_path/"):]).resolve()
        return candidate if candidate.exists() else None

    if dep.startswith("@executable_path/") or dep.startswith("@rpath/"):
        # Homebrew PostgreSQL generally resolves these through its own copied tree.
        # Leave them intact; final verification executes every required command and
        # rejects build-machine absolute paths.
        return None

    src = Path(dep)
    if not src.is_absolute() or not src.exists():
        return None

    try:
        relative = src.resolve().relative_to(source_prefix.resolve())
        bundled = bundle_root / relative
        if bundled.exists():
            return bundled.resolve()
    except ValueError:
        pass

    # Dependency belongs to another Homebrew formula (OpenSSL, ICU, readline, zstd, ...).
    return copy_dependency(src, deps_dir)


def all_macho(root: Path) -> list[Path]:
    return sorted(p for p in root.rglob("*") if is_macho(p))


def main() -> int:
    if sys.platform != "darwin":
        print("ERROR: relocate-macos-postgresql.py must run on macOS", file=sys.stderr)
        return 2

    ap = argparse.ArgumentParser()
    ap.add_argument("bundle_root", type=Path, help="target runtime/postgresql directory")
    ap.add_argument("source_prefix", type=Path, help="original PostgreSQL installation prefix")
    args = ap.parse_args()

    root = args.bundle_root.resolve()
    source_prefix = args.source_prefix.resolve()
    deps_dir = root / "lib" / "dse-deps"
    if not (root / "bin" / "initdb").is_file():
        raise SystemExit(f"Bundled initdb not found under {root}")

    # Scan repeatedly because copied third-party dylibs can themselves add dependencies.
    processed: set[Path] = set()
    while True:
        candidates = all_macho(root)
        pending = [p for p in candidates if p not in processed]
        if not pending:
            break
        for owner in pending:
            changes: list[tuple[str, str]] = []
            for dep in deps(owner):
                target = resolve_dep(dep, owner, source_prefix, root, deps_dir)
                if target is None:
                    continue
                new_ref = loader_reference(owner, target)
                if dep != new_ref:
                    changes.append((dep, new_ref))
            for old, new in changes:
                run("install_name_tool", "-change", old, new, str(owner))
            if owner.suffix == ".dylib":
                # Give copied dylibs a location-independent install name. Consumers are
                # still patched to a direct @loader_path path, so no runtime rpath is required.
                run("install_name_tool", "-id", f"@rpath/{owner.name}", str(owner), check=False)
            processed.add(owner)

    # Re-scan after all modifications and fail CI if any build-machine Homebrew path remains.
    offenders: list[str] = []
    for owner in all_macho(root):
        for dep in deps(owner):
            if dep.startswith(FORBIDDEN_PREFIXES):
                offenders.append(f"{owner.relative_to(root)} -> {dep}")

    if offenders:
        print("ERROR: PostgreSQL runtime still contains non-relocatable Homebrew references:", file=sys.stderr)
        for item in offenders:
            print(" - " + item, file=sys.stderr)
        return 1

    # install_name_tool invalidates existing signatures. Ad-hoc sign every modified/copied
    # Mach-O so the files can execute inside an unsigned/ad-hoc jpackage application.
    for owner in all_macho(root):
        run("codesign", "--force", "--sign", "-", "--timestamp=none", str(owner))

    print(f"Relocatable PostgreSQL runtime prepared: {root}")
    print(f"Bundled dependency directory: {deps_dir}")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
