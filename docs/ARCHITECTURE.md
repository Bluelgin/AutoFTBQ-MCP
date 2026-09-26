# Architecture

## Design rule

MCP owns the protocol.  
The external agent owns reasoning.  
Minecraft owns game truth.  
The Minecraft server owns final FTB Quests mutation.

No LLM provider SDK or API key belongs inside the mod.

## Layers

### MCP layer

`dev.autoftbq.mcp.mcp`

Responsibilities:

- Streamable HTTP transport
- MCP lifecycle compatibility
- authentication boundary
- tool schemas
- structured results
- mapping semantic tools to version-neutral operations

This layer must not directly depend on a concrete FTB Quests API generation.

### Client game-data layer

`GameDataCatalog` and generation adapters expose live facts:

- registries
- item evidence
- recipes
- FTBQ screen context
- current quest book
- runtime task/reward types

Every data response should identify its source, status, and coverage when the distinction matters.

### Platform gateway

`dev.autoftbq.mcp.platform`

This is the loader-neutral boundary for server-authoritative actions.

The current Forge implementation lives under:

`dev.autoftbq.mcp.forge.network`

Future Fabric/NeoForge implementations should provide the same gateway semantics rather than leaking loader APIs into MCP code.

### FTB Quests generation adapter

`dev.autoftbq.mcp.compat.ftbq.v2001`

All direct FTB Quests 2001 API knowledge lives under this package.

Future incompatible API generations get sibling adapters, for example:

- `compat.ftbq.v2101`
- `compat.ftbq.v2201`

Do not scatter `if (ftbqVersion ...)` checks through generic code.

### Server transaction executor

The server is the authority for every write.

The current 2001 executor follows this sequence:

1. confirm FTB Quests server file exists
2. confirm the requesting player's edit permission
3. enforce proposal idempotency
4. compare `expected_revision` with server revision
5. parse and pre-validate the entire operation array
6. snapshot the full synchronized FTBQ definition payload
7. reject if the rollback snapshot exceeds the safety cap
8. apply every operation
9. refresh IDs so later operations can reference newly created objects
10. save
11. broadcast the new quest book and editor permission state
12. record undo state

If any mutation fails after the snapshot, the executor restores the snapshot, saves it, and broadcasts the restored state.

## Immediate writes versus staged transactions

Two authoring modes intentionally coexist.

### Immediate semantic tools

One tool call equals one safe server transaction.

Use these for visible construction and ordinary agent workflows.

### Explicit staged transaction

`begin -> stage -> stage -> commit`

Use this when a set of edits must be atomic.

Staging is local to the MCP runtime and does not mutate FTBQ. The final commit still goes through the exact same server executor.

## Threading

HTTP worker threads must not touch Minecraft client objects directly.

Client reads and packet sends are marshalled through `ClientThread` onto the Minecraft client thread.

Server writes are handled by Forge networking on the server main thread.

The MCP HTTP worker may wait for bounded futures, but neither Minecraft main thread should wait for the HTTP worker.

## Stable contracts

The following are intended to remain stable across MC/loader/FTBQ versions:

- MCP tool names
- common structured result fields
- version-neutral operation kinds where feasible
- revision/conflict semantics
- transaction result semantics
- evidence/coverage semantics

Version-specific adapters translate those contracts to the concrete game APIs.
