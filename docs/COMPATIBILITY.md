# Compatibility strategy

## Current verified slice

Implementation baseline:

| Layer | Current target |
| --- | --- |
| Minecraft | 1.20.1 |
| Loader | Forge 47.x |
| FTB Quests | 2001.x |
| Java | 17 |
| MCP modern era | 2026-07-28 |
| MCP legacy Streamable HTTP | 2025-11-25, 2025-06-18, 2025-03-26 |

A version is only "supported" after it has its own build target and smoke-tested adapter. Architectural readiness is not the same as verified support.

## Why there cannot be one universal Minecraft jar

Minecraft mappings, loader APIs, networking APIs, and FTB Quests internals change across versions.

Trying to hide every difference behind reflection in one jar would make safety-sensitive mutation logic harder to verify.

The compatibility model is therefore:

```text
stable MCP contract
        |
stable platform/transaction abstractions
        |
+-------------------------------+
| version-specific build target |
+-------------------------------+
        |
loader implementation
        |
FTBQ API-generation adapter
```

One repository may produce many jars, but each jar should target a coherent Minecraft/loader/FTBQ compatibility slice.

## FTB Quests compatibility by API generation

Direct FTBQ references must stay inside a generation namespace:

```text
compat/ftbq/v2001/
compat/ftbq/v2101/
compat/ftbq/v2201/
...
```

A new generation should implement the same conceptual capabilities:

- context/read adapter
- runtime type catalog
- revision fingerprint
- permission snapshot
- query adapter
- transaction executor
- live sync
- snapshot restore
- undo

If an FTBQ generation cannot guarantee one of those features, it should advertise the capability as unavailable instead of emulating unsafe behavior.

## Loader compatibility

Generic code must not import Forge/Fabric/NeoForge networking classes.

Loader code belongs behind platform gateways.

Planned shape once a second loader/version is introduced:

```text
common-contract/
platforms/
  forge-1.20.1/
  neoforge-1.21.1/
  fabric-1.21.1/
  ...
```

The repository is currently a single Forge 1.20.1 build to keep the first slice buildable. The existing package boundaries are chosen so this Gradle split can be mechanical rather than a rewrite.

## Capability-first behavior

Agents should branch on capabilities, not hard-coded version numbers.

Examples:

- runtime type introspection available?
- server resource query available?
- full rollback available?
- selected-quest context available?
- a registry kind available?

Unknown versions fail closed with `unsupported_version`; they must never silently select the nearest adapter.

## MCP protocol compatibility

The HTTP transport keeps modern and legacy MCP lifecycle handling separate.

Modern 2026-07-28 requests are stateless and use `server/discover` plus routing/version headers.

Legacy Streamable HTTP clients negotiate through `initialize`.

Protocol support is isolated from Minecraft compatibility. A future MCP revision should require changes in the protocol package, not in FTBQ adapters.

## Adding a new Minecraft / FTBQ target

1. create the version-specific build target
2. implement the loader gateway
3. implement the FTBQ generation adapter
4. map the stable operation contract to real FTBQ calls
5. verify revision stability
6. verify permission behavior
7. test snapshot restore without deleting player progress
8. test live client synchronization
9. test third-party task/reward round-trip behavior
10. run game smoke tests before marking the target supported

Do not copy a transaction executor to a new generation and assume `readNetDataFull/writeNetDataFull` semantics are unchanged.
