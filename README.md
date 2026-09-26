# AutoFTBQ MCP

AutoFTBQ MCP turns a running Minecraft + FTB Quests client into a local **Model Context Protocol server** for AI agents.

The goal is not just remote CRUD. The runtime exposes live game facts, FTB Quests context, version-aware type discovery, and server-authoritative quest editing with revision guards, snapshot rollback, undo, idempotency, and live synchronization back into the native FTB Quests UI.

## Current baseline

The first implemented adapter targets:

- Minecraft 1.20.1
- Forge 47.x
- FTB Quests 2001.x
- Java 17

This is the first verified compatibility slice, not a claim that one jar works on every Minecraft or FTB Quests version.

The architecture is intentionally split so later Minecraft loaders and FTB Quests API generations can be added without changing the MCP tool contract. See [Compatibility](docs/COMPATIBILITY.md).

## Why this is different from a thin FTBQ bridge

AutoFTBQ MCP keeps the strongest parts of AutoFTBQ V2's game integration:

- server-authoritative FTBQ writes
- optimistic revision checking
- bounded atomic batches
- whole-book snapshots before mutation
- automatic rollback on write failure
- safe undo of the last proposal when the book has not changed
- idempotent proposal IDs for retries
- temporary IDs inside atomic batches
- live registry and recipe inspection
- item evidence that distinguishes "registered" from "proven obtainable"
- server datapack resource inspection
- current FTBQ screen / selection context
- runtime discovery of registered task and reward types
- live FTBQ synchronization after commit

The AI model, prompt loop, and provider configuration are deliberately **outside** the mod. Any MCP-capable agent can be the brain.

## Architecture

```text
MCP client / agent
       |
       | Streamable HTTP + bearer token
       v
AutoFTBQ MCP client mod
  |-- MCP protocol + tool catalog
  |-- live client game data
  |-- FTBQ screen context
  |-- FTBQ generation adapter
  |
  | Forge packet gateway
  v
Minecraft server
  |-- FTBQ permission check
  |-- expected revision check
  |-- pre-validate whole batch
  |-- snapshot quest book
  |-- apply operations
  |-- save
  |-- broadcast FTBQ sync
  |-- rollback on any failure
  `-- retain one safe undo snapshot
```

The MCP endpoint lives on the client because UI selection, client registries/models, and recipe state are valuable agent context. Writes still travel to the Minecraft server and are accepted only through server-side FTB Quests permissions.

For multiplayer authoring, install AutoFTBQ MCP on both the authoring client and the server. Singleplayer naturally contains both sides.

## MCP transport

The mod starts a loopback HTTP endpoint by default:

```text
http://127.0.0.1:25598/mcp
```

A random 256-bit bearer token is generated each launch and written to:

```text
config/autoftbq-mcp/runtime.json
```

The runtime currently serves both MCP lifecycle eras on one endpoint:

- modern: `2026-07-28` using `server/discover`
- legacy Streamable HTTP: `2025-11-25`, `2025-06-18`, `2025-03-26` using `initialize`

Remote access is disabled by default. Command rewards are also disabled by default because they can execute server commands. See [Security](docs/SECURITY.md).

## Core tools

Game knowledge:

- `minecraft.capabilities`
- `minecraft.search_registry`
- `minecraft.validate_ids`
- `minecraft.inspect_item`
- `minecraft.search_recipes`
- `minecraft.inspect_resource`

FTB Quests reads:

- `ftbq.get_context`
- `ftbq.get_book`
- `ftbq.get_chapter`
- `ftbq.get_quest`
- `ftbq.get_object`
- `ftbq.list_task_types`
- `ftbq.list_reward_types`
- `ftbq.get_type_schema`
- `ftbq.validate_book`

Safe authoring:

- `ftbq.create_chapter_group` / `ftbq.delete_chapter_group`
- `ftbq.create_reward_table` / `ftbq.delete_reward_table`
- `ftbq.create_chapter` / `ftbq.update_chapter` / `ftbq.move_chapter_to_group`
- `ftbq.create_quest` / `ftbq.update_quest` / `ftbq.move_quest`
- `ftbq.add_task` / `ftbq.add_reward`
- `ftbq.update_quest_object` / `ftbq.move_quest_object` / `ftbq.remove_quest_object`
- `ftbq.connect_quests` / `ftbq.disconnect_quests`
- `ftbq.apply_dependency_plan`
- `ftbq.delete_quest` / `ftbq.delete_chapter`
- `ftbq.apply_operations`
- `ftbq.undo_last`

Explicit staged transactions:

- `ftbq.transaction_begin`
- `ftbq.transaction_stage`
- `ftbq.transaction_status`
- `ftbq.transaction_commit`
- `ftbq.transaction_abort`

See [Tool contract](docs/TOOLS.md) and [operation contract](docs/OPERATION_CONTRACT.md).

### FTBQ selection context

While the FTB Quests screen is open, **Alt + left-click** chapter or quest buttons to toggle MCP-only context selection. Selected objects receive a green outline and are returned by `ftbq.get_context` as `mcp_selected_chapters` / `mcp_selected_quests`.

This selection is independent from FTBQ's native editor selection and does not require AutoFTBQ Studio.

## Live editing model

Immediate semantic write tools are one server transaction per tool call.

That makes agent construction visible in-game:

```text
create chapter -> commit -> FTBQ sync
create quest   -> commit -> FTBQ sync
add task       -> commit -> FTBQ sync
connect quests -> commit -> FTBQ sync
```

When several changes must succeed or fail together, use the explicit staged transaction tools. Staged operations are intentionally invisible until `transaction_commit`.

Every immediate write should begin from a fresh `book_revision` returned by `ftbq.get_context` or `ftbq.get_book`. A stale revision is rejected rather than overwriting a player's newer edit.

## Runtime type discovery

FTBQ addons and KubeJS integrations can register task/reward types unknown to AutoFTBQ.

The 2001 adapter therefore combines two sources:

1. runtime enumeration of `TaskTypes.TYPES` / `RewardTypes.TYPES`
2. best-effort default serialization by creating an unregistered temporary object and calling `writeData()`

Known official types additionally receive curated field metadata. Unknown addon types still remain accessible through their runtime type ID and raw SNBT payload.

This gives the agent a richer schema for known FTBQ types without hiding third-party types.

## Game-data evidence

AutoFTBQ MCP deliberately does not equate registry presence with gameplay availability.

For example, `minecraft.inspect_item` can prove that an item ID is registered and show its tags/model evidence, but reports survival obtainability and development completeness as unknown unless a future adapter can actually prove them.

Likewise, the capability response names data sources that are not covered yet, such as runtime script loot overrides, trades, custom machine/research systems, and boss summoning rules.

The rule is:

> unknown is not the same thing as unavailable.

## Build

```bash
./gradlew build
```

Windows:

```bat
gradlew.bat build
```

Output is under `build/libs/`.

For a running-game transport check, use the zero-dependency [runtime smoke test](docs/SMOKE_TEST.md):

```bash
python tools/mcp_smoke.py --runtime /path/to/instance/config/autoftbq-mcp/runtime.json --call-health-tool
```

## Status

This repository is an early architecture/bootstrap branch of the AutoFTBQ MCP direction. The 1.20.1/2001 compatibility slice is the implementation baseline; additional loader/version modules should only be marked supported after they compile and pass game smoke tests.

## License

GPL-3.0-only.
