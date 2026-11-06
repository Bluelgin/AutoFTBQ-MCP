# AutoFTBQ Creator: wish-to-quest (compact + full blueprints)

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
| `autoftbq.creator_preview` | Validate full or compact blueprint, detect cycles and auto-layout. Returns a preview hash and short-lived `draft_id` | No quest-book writes; works offline |
| `autoftbq.creator_apply` | Apply a reviewed `draft_id` or resubmit identical full/compact blueprint; validate live IDs and atomically create **new chapter only** | Server permission, expected revision, snapshot/rollback and idempotency remain required |

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

For lower request sizes, apply the saved preview using `draft_id` **instead of** the full `blueprint`. The draft is held in memory only (10-minute TTL, max 16 recent drafts, cleared on restart). Specify exactly one of `draft_id` and `blueprint` in `creator_apply`; always pass `preview_hash`, `expected_revision` and `confirm=true`. The preview hash and draft ID are not substitutes for human review or FTBQ server authorization.

## Scope and known limitations

- **Additive-only MVP:** creates one new chapter, not patching or deleting existing chapters. A blank/small chapter is a safe default, not proof of gameplay quality.
- Creator preview is offline and does not query Minecraft. Item IDs are checked against the **running registry** on apply, while the server performs its own checks. Recipe and survival obtainability require separate inspection and may remain unknown.
- Names, task objectives, pacing and rewards are generated by the external AI; their quality depends on evidence and model ability. Automatic source grounding and deep mod-mechanic discovery are not solved by this PR.
- Default MCP tools/list still exposes the full tool catalog; lazy tool discovery and per-Harness profiles remain follow-up work.
- No cost or WQSR improvements are claimed until running-game comparisons are measured. See [Creator evaluation](CREATOR_EVAL.md).

## Test plan

Automated JUnit tests cover deterministic layout, hashes, local key references, cycles, bad inputs, task bounds and supported object conversion. They do not require a Minecraft runtime.

Manual in-game acceptance: connect an agent, generate a new chapter from a one-sentence wish, inspect preview, explicitly approve, check game sync, task icons, dependency layout, quest usability, revision-conflict handling, unknown ID rejection and safe undo. Test both singleplayer and a permissioned dedicated server before declaring full runtime support.


## Compact item-first blueprint (optional)

The full blueprint schema above remains unchanged. For simple item tasks,
set `chapter` to a **string**; the Creator normalizer expands it to the
existing strict full blueprint and safe operation executor.

~~~json
{
  "chapter": "Create Basics",
  "quests": [
    {"id": "alloy", "get": "create:andesite_alloy", "n": 8},
    {"id": "press", "get": "create:mechanical_press", "after": "alloy",
     "give": "minecraft:iron_ingot", "give_n": 2}
  ]
}
~~~

- `chapter`: nonempty chapter title; optional top-level `subtitle`, `icon`
  and `group_id` are forwarded to the full blueprint.
- `quests[].id`: unique local quest key. `title`, `desc` and `icon` are optional.
  `desc` accepts text or an array of text lines.
- `get`: item ID for a single task; `n` defaults to 1. Omitting `get`
  produces the same warned manual-checkmark as a full blueprint.
- `after`: one prerequisite local quest key or an array; layout is unchanged.
- `give`: optional item reward ID; `give_n` defaults to 1.
- Without `title`, a readable fallback is inferred from the item path.
  It is **not** a guaranteed localized name or polished quest title.
- Compact input does **not** accept raw SNBT, commands, multiple tasks,
  XP rewards or addon-specific types. Use the full blueprint or existing
  permission-checked low-level FTBQ tools for advanced requirements.
- Full compiler limits, syntax checks, dependency checks and hashes still
  apply. Runtime ID existence is checked on apply; obtainability remains unknown.

## Bounded local item lookup

Call `minecraft.search_items` with an item ID substring or localized display-name
substring and/or a mod namespace (e.g. `create`). A client-local index builds
lazily and returns **at most 30 candidates per call**, with exact IDs and
display names. Names refresh when runtime data or language changes. Avoid
dumping a complete modpack registry into the model's context.

Registration is **not** proof of survival obtainability. Inspect recipes and
mechanics selectively with `minecraft.search_recipes` and `minecraft.inspect_item`.
Use verified fully qualified item IDs in the blueprint.

## Efficient preview/apply example

1. Search relevant items with `minecraft.search_items`.
2. Send the compact blueprint to `autoftbq.creator_preview`.
3. Show the warnings and goals; obtain explicit human approval.
4. Read a **fresh** FTBQ revision and apply only the reviewed draft:

~~~json
{
  "draft_id": "<draft_id from creator_preview>",
  "preview_hash": "<preview_hash from creator_preview>",
  "expected_revision": "<fresh book_revision>",
  "confirm": true,
  "proposal_id": "<stable id for identical retries>"
}
~~~

The legacy apply-by-blueprint path remains supported. A missing or expired draft
must be previewed again; it is never silently regenerated into an unreviewed plan.

Payload size is not the same as actual LLM Token consumption. See
[Creator evaluation](CREATOR_EVAL.md) for measurement.
