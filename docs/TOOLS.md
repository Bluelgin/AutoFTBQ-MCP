# MCP tools

## General result rules

Tools return structured JSON and a text copy for broad MCP client compatibility.

Common status values include:

- `ok`
- `not_found`
- `unavailable`
- `unsupported`
- `unsupported_version`
- `stale_version`
- `permission_denied`
- `conflict`
- `too_large`
- `failed`

A missing capability or unknown coverage must not be converted into a claim that game content does not exist.

## Game knowledge

### minecraft.capabilities

Reports data sources covered by the active adapter and explicitly lists important unknown areas.

Call this before making claims about complete modpack progression or obtainability.

### minecraft.search_registry

Paged runtime registry search.

The 2001 client adapter currently covers:

- item
- block
- entity / entity_type
- fluid
- mob_effect
- stat / custom_stat
- recipe_type
- biome
- structure

Results include a `data_version`. If the game reloads tags/recipes while paging, the next request can return `stale_version` so the agent restarts from page zero.

### minecraft.validate_ids

Exact registry ID validation for supported registry kinds.

### minecraft.inspect_item

Returns evidence such as:

- exact registered ID
- localized name
- tags
- client model status
- explicit unknown obtainability/completeness fields

This tool intentionally does not infer survival availability from registration.

### minecraft.search_recipes

Searches the live client RecipeManager for recipes consuming and/or producing an exact item ID.

The scan is bounded and reports pagination plus unreadable custom recipes.

### minecraft.inspect_resource

Reads server datapack resources through the authenticated player connection.

Current datasets:

- loot_tables
- advancements

This is resource-pack/datapack evidence only. Runtime scripting systems can override behavior and remain outside this source's coverage.

## FTBQ reads

### ftbq.get_context

Returns local editor context:

- current chapter
- selected native FTBQ quests
- client edit flag
- current client book revision

### ftbq.get_book

Returns chapter-group / chapter overview and revision.

### ftbq.get_chapter

Returns a chapter plus quest summaries.

### ftbq.get_quest

Returns quest data, dependencies, tasks, rewards, and raw SNBT.

### ftbq.get_object

Low-level object read.

### ftbq.list_task_types / ftbq.list_reward_types

Enumerate runtime-registered types, including compatible addon types.

### ftbq.get_type_schema

Combines runtime defaults with curated metadata for known official types.

Unknown third-party types still expose runtime defaults and remain writable through the generic typed-object path.

## Immediate safe writes

Every immediate write requires `expected_revision`.

The server checks that revision immediately before mutation.

A successful result returns:

- `proposal_id`
- `book_revision`
- `temporary_ids` when relevant
- operation count

Reuse the same `proposal_id` when retrying the exact same write after a transport failure.

### ftbq.create_chapter

Creates a chapter in the default chapter group.

### ftbq.create_quest

Creates a quest at an explicit chapter/x/y location.

### ftbq.update_quest

Patches common safe quest fields.

### ftbq.add_task / ftbq.add_reward

Known built-in types have semantic shortcuts.

Any runtime-registered type can fall back to:

- exact `type_id`
- raw `data_snbt`

The server still creates the real FTBQ object; unsupported types are rejected.

### ftbq.connect_quests

Adds one dependency edge.

### ftbq.apply_dependency_plan

Atomically applies a bounded group of dependency edges.

### ftbq.remove_quest_object

Removes one task or reward.

### ftbq.delete_quest / ftbq.delete_chapter

Destructive operations, but still protected by revision checking and full transaction rollback.

### ftbq.apply_operations

Advanced low-level entry point for the version-neutral operation contract.

Prefer semantic tools when they cover the task.

### ftbq.undo_last

Restores the snapshot for a specified successful proposal only if the server revision still equals that proposal's post-write revision.

This prevents undo from erasing somebody else's newer edit.

## Explicit staged transactions

### ftbq.transaction_begin

Captures the current server revision and returns a transaction handle.

### ftbq.transaction_stage

Adds operation objects to the transaction without mutating the game.

### ftbq.transaction_status

Returns the current staged operations.

### ftbq.transaction_commit

Commits the whole staged array through the normal server executor.

If the book changed since `begin`, commit fails with a conflict.

### ftbq.transaction_abort

Drops the staged transaction.

Staged transactions expire after a bounded idle lifetime.
