#!/usr/bin/env python3
from __future__ import annotations

import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
PROTECTED = [
    ROOT / "src/main/java/dev/autoftbq/mcp/mcp",
    ROOT / "src/main/java/dev/autoftbq/mcp/platform",
]
FORBIDDEN = {
    "net.minecraftforge": "core must not depend directly on Forge",
    "net.neoforged": "core must not depend directly on NeoForge",
    "net.fabricmc": "core must not depend directly on Fabric",
    "dev.autoftbq.mcp.forge": "MCP/platform core must not import loader implementation",
    "dev.autoftbq.mcp.compat.ftbq.v": "MCP/platform core must not import a concrete FTBQ generation",
}

violations: list[str] = []
for directory in PROTECTED:
    if not directory.exists():
        continue
    for path in directory.rglob("*.java"):
        text = path.read_text(encoding="utf-8")
        for needle, reason in FORBIDDEN.items():
            if needle in text:
                rel = path.relative_to(ROOT)
                violations.append(f"{rel}: {needle} ({reason})")

if violations:
    print("Architecture boundary violations:", file=sys.stderr)
    for violation in violations:
        print(f" - {violation}", file=sys.stderr)
    raise SystemExit(1)

print("Architecture boundaries OK")
