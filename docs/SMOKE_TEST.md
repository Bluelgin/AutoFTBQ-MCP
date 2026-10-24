# Runtime smoke test

After launching a compatible Minecraft instance with AutoFTBQ MCP installed, the mod creates:

`config/autoftbq-mcp/runtime.json`

From the Minecraft instance directory run:

```bash
python tools/mcp_smoke.py
```

The script uses only the Python standard library and verifies:

1. bearer-token authentication
2. MCP 2026-07-28 `server/discover`
3. `tools/list`
4. presence of core game/read/write/transaction tools

To also exercise the game-facing health tool:

```bash
python tools/mcp_smoke.py --call-health-tool
```

The health tool is expected to report partial/unavailable game state if no world/server is loaded. That is different from an MCP transport failure.

The runtime token is intentionally regenerated on each game launch; do not hard-code it into a project file.
