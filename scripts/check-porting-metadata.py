#!/usr/bin/env python3
"""Minimal strict parser/validator for our flat porting/targets.yml format."""
from __future__ import annotations
import re
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
FIELDS = {"id", "minecraft", "loader", "ftb_quests", "java",
          "status", "build", "game_smoke", "build_path", "evidence"}


def parse(source: str) -> list[dict[str, str]]:
    targets: list[dict[str, str]] = []
    started = schema = False
    for number, line in enumerate(source.splitlines(), 1):
        line = line.rstrip()
        if not line or line.lstrip().startswith("#"):
            continue
        if line == "schema_version: 1" and not schema and not started:
            schema = True
        elif line == "targets:" and schema and not started:
            started = True
        elif started and (m := re.fullmatch(r"  - id: ([a-z0-9][a-z0-9.-]*)", line)):
            targets.append({"id": m.group(1)})
        elif started and targets and (m := re.fullmatch(r"    ([a-z_]+): (.+)", line)):
            key, raw = m.groups()
            if key not in FIELDS or key in targets[-1] or key == "id":
                raise ValueError(f"line {number}: invalid or duplicate field {key}")
            if raw.startswith('"') and raw.endswith('"'):
                raw = raw[1:-1]
            elif not re.fullmatch(r"[A-Za-z0-9._/-]+", raw):
                raise ValueError(f"line {number}: invalid scalar")
            targets[-1][key] = raw
        else:
            raise ValueError(f"line {number}: unsupported manifest form")
    if not schema or not started or not targets:
        raise ValueError("manifest needs schema_version: 1 and targets")
    return targets


def validate(targets: list[dict[str, str]]) -> None:
    seen = set()
    for row in targets:
        missing = FIELDS - row.keys()
        if missing:
            raise ValueError(f"{row.get('id', '?')}: missing {sorted(missing)}")
        name = row["id"]
        if name in seen:
            raise ValueError(f"duplicate id: {name}")
        seen.add(name)
        if row["status"] not in {"proposed", "implemented", "experimental", "supported"}:
            raise ValueError(f"{name}: invalid status")
        if row["loader"] not in {"forge", "fabric", "neoforge"}:
            raise ValueError(f"{name}: invalid loader")
        if row["build"] not in {"not_run", "passed", "failed"}:
            raise ValueError(f"{name}: invalid build")
        if row["game_smoke"] not in {"not_run", "pending", "passed", "failed"}:
            raise ValueError(f"{name}: invalid smoke")
        if row["status"] == "proposed" and (
            row["build"] != "not_run" or row["game_smoke"] != "not_run"
            or row["build_path"] != "none"
        ):
            raise ValueError(f"{name}: a proposed target cannot claim a build")
        if row["status"] != "proposed" and row["build_path"] == "none":
            raise ValueError(f"{name}: implemented target needs build path")
        if row["status"] == "supported" and not (
            row["build"] == "passed" and row["game_smoke"] == "passed"
            and row["evidence"].startswith("https://")
        ):
            raise ValueError(f"{name}: supported requires real-game evidence")
    if "forge-1.20.1-ftbq-2001" not in seen:
        raise ValueError("reference Forge target missing")


def main() -> None:
    targets = parse((ROOT / "porting/targets.yml").read_text(encoding="utf-8"))
    validate(targets)
    license_text = (ROOT / "LICENSE").read_text(encoding="utf-8")
    if not license_text.startswith("MIT License\n") or "Permission is hereby granted" not in license_text:
        raise ValueError("Expected a standard MIT LICENSE")
    readme = (ROOT / "README.md").read_text(encoding="utf-8")
    if "[AGENTS.md](AGENTS.md)" not in readme or "docs/AI_PORTING.md" not in readme:
        raise ValueError("README missing AI-porting entrypoint")
    print(f"Porting metadata OK ({len(targets)} target records)")


if __name__ == "__main__":
    main()
