# Connect AutoFTBQ MCP to Codex

Codex CLI and the Codex IDE extension can connect to local Streamable HTTP MCP servers through the shared Codex MCP configuration.

AutoFTBQ MCP uses a random bearer token on every Minecraft launch. Do **not** paste that token into Codex config. Instead, configure Codex with `http_headers_helper`, which runs a local helper before HTTP requests and reads the current token from Minecraft's `runtime.json`.

## 1. Install the mod

Current verified runtime target:

- Minecraft 1.20.1
- Forge 47.x
- FTB Quests 2001.x
- Java 17

For singleplayer, put the built AutoFTBQ MCP jar in the client instance's `mods/` folder.

For multiplayer authoring, install the same compatible AutoFTBQ MCP build on both:

- the authoring client
- the Minecraft server

The connected player must have normal FTB Quests editor permission. MCP does not bypass FTBQ permission checks.

## 2. Start Minecraft first

Launch Minecraft, enter the world/server, and wait for AutoFTBQ MCP to start.

The mod writes:

```text
<instance>/config/autoftbq-mcp/runtime.json
```

Example:

```text
D:\Minecraft\Instances\MyPack\config\autoftbq-mcp\runtime.json
```

It contains the current local MCP URL and a per-launch bearer token.

The default endpoint is:

```text
http://127.0.0.1:25598/mcp
```

If you changed the MCP port, always use the URL written in `runtime.json`.

## 3. Test AutoFTBQ MCP before involving Codex

From the AutoFTBQ-MCP repository:

```powershell
python tools/mcp_smoke.py --runtime "D:\Minecraft\Instances\MyPack\config\autoftbq-mcp\runtime.json" --call-health-tool
```

A healthy result should show:

- MCP discovery succeeded
- the tool list is present
- `autoftbq.health` responds

This separates Minecraft/MCP problems from Codex configuration problems.

## 4. Generate the Codex configuration

On Windows, from the AutoFTBQ-MCP repository:

```powershell
powershell -ExecutionPolicy Bypass -File .\tools\codex_config_snippet.ps1 -RuntimeFile "D:\Minecraft\Instances\MyPack\config\autoftbq-mcp\runtime.json"
```

The script prints a block similar to:

```toml
[mcp_servers.autoftbq]
enabled = true
required = false
url = "http://127.0.0.1:25598/mcp"
http_headers_helper = "powershell.exe -NoProfile -ExecutionPolicy Bypass -File \"C:\\path\\to\\AutoFTBQ-MCP\\tools\\codex_headers.ps1\" -RuntimeFile \"D:\\Minecraft\\Instances\\MyPack\\config\\autoftbq-mcp\\runtime.json\""
startup_timeout_sec = 10
tool_timeout_sec = 60
default_tools_approval_mode = "writes"
```

Paste that block into:

```text
%USERPROFILE%\.codex\config.toml
```

Codex CLI and the Codex IDE extension share this MCP configuration.

The header helper reads the **current** token from `runtime.json` every time Codex needs headers. Restarting Minecraft changes the token, but you do not need to edit the Codex config again as long as the runtime file path stays the same.

### Cross-platform helper

If PowerShell is not appropriate, use:

```text
tools/codex_headers.py
```

It accepts the runtime file as its first argument and prints only:

```json
{"Authorization":"Bearer <current token>"}
```

which is the format Codex expects from `http_headers_helper`.

## 5. Verify Codex sees the server

Start Minecraft **before** starting the Codex session, then run:

```powershell
codex mcp list
```

Confirm that `autoftbq` is configured.

The real end-to-end test is to open Codex and ask it to call:

```text
autoftbq.health
```

Then ask it to call:

```text
ftbq.get_book
```

If those work, Codex can see the running game.

## 6. Recommended first Codex instruction

Paste this into Codex:

```text
Use the AutoFTBQ MCP server to work on the FTB Quests book in my currently running Minecraft instance.

First call autoftbq.health, then ftbq.get_context and ftbq.get_book. Do not assume item IDs or recipes: use the minecraft.* tools when game facts are needed.

For edits, prefer the semantic ftbq.* authoring tools instead of ftbq.apply_operations. Before each immediate write, use the latest book_revision. If a write returns conflict, re-read the book/context and reconcile instead of retrying blindly.

After a meaningful batch of edits, call ftbq.validate_book and fix any errors you introduced. Do not create command rewards unless I explicitly ask and the server policy allows them.
```

You can then follow with normal instructions such as:

```text
Read the current book and create a small progression line for the Create mod. Use real registered item IDs and recipes from the running game. I want to watch the quests appear live in FTB Quests.
```

## 7. Selecting quests for Codex in-game

While the FTB Quests screen is open:

- **Alt + left-click a quest** to toggle it into MCP context
- **Alt + left-click a chapter** to toggle the chapter into MCP context

Selected objects get a green outline.

Then tell Codex:

```text
Call ftbq.get_context. Only modify the MCP-selected quests I marked in-game.
```

The context response exposes:

- `mcp_selected_quests`
- `mcp_selected_chapters`

This is independent from FTBQ's normal editor selection.

## 8. Approval behavior

The recommended config uses:

```toml
default_tools_approval_mode = "writes"
```

AutoFTBQ marks read tools as read-only and marks destructive tools separately. This gives Codex useful approval metadata while Minecraft/FTBQ server-side permission, revision, rollback, and command-reward policy remain the real security boundary.

If you want more confirmation prompts, change the Codex MCP approval policy to `prompt`.

## Troubleshooting

### Codex gets 401 Unauthorized

Run the helper manually:

```powershell
powershell.exe -NoProfile -ExecutionPolicy Bypass -File ".\tools\codex_headers.ps1" -RuntimeFile "D:\Minecraft\Instances\MyPack\config\autoftbq-mcp\runtime.json"
```

It must print a compact JSON object containing an `Authorization` header.

If the runtime file disappeared, Minecraft/AutoFTBQ MCP is not currently running or shut down normally.

### Connection refused / server unavailable

Check `runtime.json` and make sure the configured Codex `url` matches its `url` field.

Start Minecraft first, then start a fresh Codex session.

### MCP reads work but writes return permission_denied

The connected Minecraft player does not currently have FTB Quests editor permission.

Grant/enable editing through the normal FTB Quests server/admin workflow. AutoFTBQ MCP intentionally cannot bypass that check.

### Writes return conflict

The quest book changed after Codex last read its revision, possibly because you edited it in-game.

Tell Codex to re-read `ftbq.get_context` / `ftbq.get_book` and retry the intended edit against the new state.

### Codex is connected but does not use AutoFTBQ tools

Explicitly tell it:

```text
Use the autoftbq MCP server. Start with autoftbq.health and ftbq.get_book.
```

For the first test, avoid vague requests until the tool connection is proven.

## Notes

- `required = false` means Codex itself can still start when Minecraft is closed.
- For actual FTBQ work, launch Minecraft first and then open/restart the Codex session.
- The MCP endpoint is loopback-only by default.
- Do not expose the MCP port directly to the public Internet.
