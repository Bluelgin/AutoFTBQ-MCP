#!/usr/bin/env python3
"""Print AutoFTBQ MCP HTTP headers for Codex http_headers_helper."""

from __future__ import annotations

import json
import os
import sys
from pathlib import Path


def resolve_runtime(argv: list[str]) -> Path:
    if len(argv) > 1 and argv[1].strip():
        return Path(argv[1]).expanduser().resolve()

    env = os.environ.get("AUTOFTBQ_MCP_RUNTIME", "").strip()
    if env:
        return Path(env).expanduser().resolve()

    local = Path.cwd() / "config" / "autoftbq-mcp" / "runtime.json"
    if local.exists():
        return local.resolve()

    raise RuntimeError(
        "AutoFTBQ MCP runtime.json not found. Pass its path as the first argument "
        "or set AUTOFTBQ_MCP_RUNTIME."
    )


def main() -> int:
    try:
        runtime_file = resolve_runtime(sys.argv)
        data = json.loads(runtime_file.read_text(encoding="utf-8"))
        token = str(data.get("token", "")).strip()
        if not token:
            raise RuntimeError(
                "runtime.json does not contain a token. "
                "Is Minecraft with AutoFTBQ MCP currently running?"
            )
        print(json.dumps({"Authorization": f"Bearer {token}"}, separators=(",", ":")))
        return 0
    except Exception as exc:
        print(str(exc), file=sys.stderr)
        return 2


if __name__ == "__main__":
    raise SystemExit(main())
