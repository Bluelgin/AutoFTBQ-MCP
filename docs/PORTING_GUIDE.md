# Technical porting guide

Read `AGENTS.md`, `docs/ARCHITECTURE.md`, `docs/COMPATIBILITY.md`, `docs/SECURITY.md` and `docs/OPERATION_CONTRACT.md` before changes.

## Reference baseline and boundaries

Currently implemented: **Minecraft 1.20.1 / Forge 47.x / FTB Quests 2001.x / Java 17**. Build from root using `./gradlew build`. In-game acceptance is still required; see `porting/targets.yml`.

- Common MCP tools/transport: `src/main/java/dev/autoftbq/mcp/mcp/`
- Loader-neutral gateway contracts: `src/main/java/dev/autoftbq/mcp/platform/`
- Forge bootstrapping and packets: `src/main/java/dev/autoftbq/mcp/forge/`
- FTBQ adapter/provider abstraction: `src/main/java/dev/autoftbq/mcp/compat/ftbq/`
- Version 2001 adapter: `src/main/java/dev/autoftbq/mcp/compat/ftbq/v2001/`
- Server mutations and transactions: `src/main/java/dev/autoftbq/mcp/transaction/`

## Recommended initial port: Fabric 1.20.1

1. Check actual availability and licensing of Fabric Loader/API, Fabric FTB Quests 2001.x, FTB Library and Teams. Do not assume Forge JARs work on Fabric or APIs are identical.
2. Add a **separate** Fabric Gradle build target, preserving the Forge baseline.
3. Implement Fabric entrypoint/lifecycle, networking, server gateway, client dispatcher, native FTBQ GUI context and client/server sync behind loader-neutral interfaces.
4. Validate the existing FTBQ 2001 generation adapter against the Fabric artifact; create a separate generation adapter if its API differs.
5. Preserve the same MCP tool names and structured results. Use capability flags and explicit unsupported errors.
6. Test on a disposable real world and dedicated server. Do not claim complete Fabric support from compilation alone.

For NeoForge and newer Minecraft versions, also verify mappings, Java versions and FTBQ internals; avoid reflection-based guesses about mutation semantics.

## Safety parity checklist

| Capability | Required behavior |
| --- | --- |
| Authentication | Per-launch local bearer token, loopback by default |
| Authorization | Server checks the actual FTBQ edit permission for writes and undo |
| Revision conflict | Reject stale book revisions without mutating |
| Transactions | Validate whole batch, snapshot, rollback on any failure |
| Undo and retries | Revision-guarded last undo; idempotent proposal IDs |
| Live synchronization | Save and broadcast FTBQ state after success / restore |
| Game threading | HTTP workers never directly mutate Minecraft objects |
| Unknown types | Preserve/validate third-party task and reward data |
| Graphics | State unsupported capture or selection functionality explicitly |
| Legacy | Keep existing Forge behavior and quest/player progress intact |

## Reproducible acceptance

- Run `python scripts/check-architecture.py` and `python scripts/check-porting-metadata.py`.
- Run the exact target Gradle build/test; record dependencies, Java and mappings.
- Start the client; verify MCP health, registry reads, FTBQ book and GUI context.
- Start a matching dedicated server; test a permitted editor vs denied user.
- With a **backup/test world**, create/update/undo a quest, reject invalid inputs, trigger revision conflicts, test failed-batch rollback, retry identical proposal IDs and verify live save/sync.
- Check game restart persistence, player progress and third-party reward/task round trips.
- Share sanitized logs/screenshots, list skipped tests explicitly and update the compatibility manifest accurately.

Use the repository PR template. Port authors must have rights to offer source under MIT; dependencies have separate licenses. Never auto-merge.
