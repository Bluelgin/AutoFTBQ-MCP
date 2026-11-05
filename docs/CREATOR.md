# AutoFTBQ Creator: wish-to-quest MVP

The **Creator workflow** reduces prompt engineering: users state an outcome, an external MCP-capable agent studies the loaded game and proposes a quest blueprint, and the mod converts the blueprint into deterministic, revision-guarded FTBQ operations. No LLM SDK, credentials or prompt loop run inside the mod.

## Quick start

Install the mod and connect an MCP-capable agent as described in [CODEX.md](CODEX.md). Give the agent `skills/autoftbq-creator/SKILL.md` as instructions (copy the file content if your Harness does not support loading Skills directly).

Try a single wish:

> Make a beginner-friendly chapter for Create in this modpack. Infer reasonable quest pacing, use only observed game facts and let me approve a compact preview before you write anything.

The agent calls `autoftbq.creator_context` to learn what book is loaded and where the player is working. It can inspect relevant items and recipes before building a chapter. You do **not** have to hand-author quest coordinates or every task.

## High-level tools

| Tool | Meaning | Side effects |
| --- | --- | --- |
| `autoftbq.creator_context` | One-stop read of selection, book summary and coverage (not complete knowledge of all mods) | None |
| `autoftbq.creator_preview` | Validate blueprint, detect cycles/unknown dependencies and auto-layout. Returns compact counts, coordinates, warnings and a `preview_hash` | None; works offline |
| `autoftbq.creator_apply` | Validate item registry IDs and atomically write **new chapter only**, using the same compiled plan | Writes with existing FTBQ editor permission, expected revision, snapshot/rollback and idempotent proposal ID |

There is **no tool that accepts only free-form natural-language text and secretly invokes a second model**. The external agent remains responsible for creative planning. AutoFTBQ executes verified, deterministic operations.

## Blueprint schema

```json
{
  "chapter": {
    "title": "First Steps",
    "subtitle": "A little guided survival",
    "icon": "minecraft:crafting_table"
  },
  "quests": [
    {
      "key": "wood",
      "title": "Gather logs",
      "description": ["Start by collecting local wood."],
      "tasks": [{"kind": "item", "item_id": "minecraft:oak_log", "count": 8}],
      "rewards": [{"kind": "xp_levels", "amount": 1}]
    },
    {
      "key": "bench",
      "title": "Craft a workbench",
      "depends_on": ["wood"],
      "tasks": [{"kind": "item", "item_id": "minecraft:crafting_table", "count": 1}]
    }
  ]
}
```

Only `chapter` and `quests` are accepted at the top level. Chapter fields: `title` (required), optional `subtitle`, `icon`, `group_id`. Each quest has required unique `key` and `title`, optional `subtitle`, `description` (string or string array), `icon`, `depends_on`, `tasks`, `rewards`. No arbitrary embedded operations, commands, SNBT or coordinates.

For items, provide a real fully qualified `item_id` and optional positive `count` (default 1). For XP/XP levels provide positive `amount`. A `checkmark` task takes no other fields. Rewards cannot be checkmarks. Tasks omitted from a quest become a manual checkmark and yield a warning. Rewards are optional.

Limits: 1–48 quests, at most 8 tasks and 8 rewards per quest, 12 local dependencies per quest, item count 1–4096, XP amount 1–100000. Quest keys must be alphanumeric with underscore/hyphen and start with a letter. Local dependencies must exist and cannot be cyclic. Layout is left-to-right by dependency depth, centered within each column.

## Preview → approval → apply

1. Agent builds the blueprint and calls `autoftbq.creator_preview({"blueprint": ...})`.
2. Show the generated plan to the player. Review the compiler's warnings and separate unverifiable gameplay assumptions. Seek authorization for the exact plan.
3. Agent reads a **fresh** `book_revision` and calls:

```json
{
  "blueprint": {"chapter": {"title": "..."}, "quests": [{"key": "intro", "title": "..."}]},
  "preview_hash": "64-character hash from the matching creator_preview",
  "expected_revision": "latest server/client revision",
  "confirm": true,
  "proposal_id": "stable-id-for-identical-retries"
}
```

The preview hash is derived from canonical compiled operations and rejects a changed plan; it is **not** proof of human consent. The agent/Harness must enforce human review. Server permissions, optimistic revision checks, rollback and command-reward policy remain authoritative. Reusing `proposal_id` is intended only when retrying the exact same request.

4. Validate the book and inspect its chapter map. Do not claim that structural checks prove obtainable content or that a schematic is a native screenshot.

## Scope and known limitations

- **Additive-only MVP:** creates one new chapter, not patching or deleting existing chapters. A blank/small chapter is a safe default, not proof of gameplay quality.
- Creator preview is offline and does not query Minecraft. Item IDs are checked against the **running registry** on apply, while the server performs its own checks. Recipe and survival obtainability require separate inspection and may remain unknown.
- Names, task objectives, pacing and rewards are generated by the external AI; their quality depends on evidence and model ability. Automatic source grounding and deep mod-mechanic discovery are not solved by this PR.
- Default MCP tools/list still exposes the full tool catalog; lazy tool discovery and per-Harness profiles remain follow-up work.
- No cost or WQSR improvements are claimed until running-game comparisons are measured. See [Creator evaluation](CREATOR_EVAL.md).

## Test plan

Automated JUnit tests cover deterministic layout, hashes, local key references, cycles, bad inputs, task bounds and supported object conversion. They do not require a Minecraft runtime.

Manual in-game acceptance: connect an agent, generate a new chapter from a one-sentence wish, inspect preview, explicitly approve, check game sync, task icons, dependency layout, quest usability, revision-conflict handling, unknown ID rejection and safe undo. Test both singleplayer and a permissioned dedicated server before declaring full runtime support.
