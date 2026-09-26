# Security model

AutoFTBQ MCP exposes mutation tools to external software, so the default security posture is intentionally local and fail-closed.

## Network boundary

Default configuration:

- bind address: `127.0.0.1`
- remote access: disabled
- random bearer token per launch
- bounded request body
- Origin validation for local browser-like clients
- no wildcard unauthenticated endpoint

Credentials are written to:

`config/autoftbq-mcp/runtime.json`

Treat that file like a local secret.

## Minecraft authority

Possessing the MCP bearer token is not enough to edit a multiplayer quest book.

Writes are forwarded through the connected Minecraft player to the server.

The server checks the real FTB Quests edit permission before every proposal and undo.

The MCP side cannot grant itself FTBQ permission.

## Concurrency

Every immediate write includes an expected quest-book revision.

If the server book changed after the agent read it, the write returns `conflict` and performs no mutation.

This protects edits made by the player or another administrator during agent reasoning time.

## Rollback

The server snapshots the synchronized FTBQ definition payload before mutation.

If the snapshot exceeds the configured safety cap, the proposal is rejected before mutation.

If a later operation fails, the server restores the snapshot and broadcasts the restored book.

The restore path deliberately avoids destructive FTBQ calls that would remove player progress.

## Undo

Only the last retained proposal snapshot for the player is undoable.

Undo is accepted only when the current revision still equals the proposal's post-write revision.

This prevents an undo from overwriting later human or agent changes.

## Idempotency

Proposal IDs are idempotency keys.

Retry the same proposal with the same ID, expected revision, and operation payload after a transport timeout.

Changing the payload while reusing the same proposal ID is a conflict.

## Untrusted content

Quest text, registry names, datapack JSON, addon metadata, and MCP client-provided strings are data, not trusted instructions.

The mod does not execute arbitrary MCP text as game commands.

FTB Quests command rewards are a separate execution-bearing feature. AutoFTBQ MCP rejects creation/upsert of `ftbquests:command` rewards by default at the server transaction preflight, including the generic raw path. A trusted server owner must explicitly set:

```json
{
  "allowCommandRewards": true
}
```

in `config/autoftbq-mcp.json` to enable them.

## Remote mode

`allowRemote=true` exists for advanced deployments but should not be treated as Internet-safe authentication.

The current bearer-token transport is intended primarily for loopback or a separately secured trusted tunnel/network.

Do not expose the port directly to the public Internet.
