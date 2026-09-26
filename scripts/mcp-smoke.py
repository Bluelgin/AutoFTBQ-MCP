#!/usr/bin/env python3
"""Smoke-test a running AutoFTBQ MCP instance using only the Python standard library."""

from __future__ import annotations

import json
import sys
import urllib.error
import urllib.request
from pathlib import Path


def load_runtime(path: Path) -> tuple[str, str]:
    data = json.loads(path.read_text(encoding="utf-8"))
    return str(data["url"]), str(data["token"])


def post(url: str, token: str, method: str, params: dict | None = None, request_id: int = 1) -> dict:
    payload = {
        "jsonrpc": "2.0",
        "id": request_id,
        "method": method,
        "params": params or {},
    }
    headers = {
        "Content-Type": "application/json",
        "Accept": "application/json",
        "Authorization": f"Bearer {token}",
        "MCP-Protocol-Version": "2026-07-28",
        "Mcp-Method": method,
    }
    if method == "tools/call":
        headers["Mcp-Name"] = str((params or {}).get("name", ""))

    request = urllib.request.Request(
        url,
        data=json.dumps(payload).encode("utf-8"),
        headers=headers,
        method="POST",
    )
    with urllib.request.urlopen(request, timeout=15) as response:
        return json.loads(response.read().decode("utf-8"))


def main() -> int:
    runtime = Path(sys.argv[1]) if len(sys.argv) > 1 else Path("config/autoftbq-mcp/runtime.json")
    if not runtime.exists():
        print(f"runtime credentials not found: {runtime}", file=sys.stderr)
        return 2

    url, token = load_runtime(runtime)
    try:
        discover = post(url, token, "server/discover", request_id=1)
        tools = post(url, token, "tools/list", request_id=2)
        health = post(
            url,
            token,
            "tools/call",
            {"name": "autoftbq.health", "arguments": {}},
            request_id=3,
        )
    except urllib.error.HTTPError as error:
        print(error.read().decode("utf-8", errors="replace"), file=sys.stderr)
        return 1
    except Exception as error:
        print(f"smoke test failed: {error}", file=sys.stderr)
        return 1

    discovered = discover.get("result", {}).get("supportedVersions", [])
    listed = tools.get("result", {}).get("tools", [])
    result = health.get("result", {})

    print(f"endpoint: {url}")
    print(f"protocols: {', '.join(discovered)}")
    print(f"tools: {len(listed)}")
    print("health:")
    print(json.dumps(result, indent=2, ensure_ascii=False))

    if "2026-07-28" not in discovered or not listed:
        return 1
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
