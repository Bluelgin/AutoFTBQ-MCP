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
- dimension (from FTB Library's server-registry snapshot)
- advancement (from FTB Library's server-registry snapshot)
- structure (queried authoritatively from the Minecraft server)

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

### ftbq.validate_book

Runs a read-only integrity pass over the live task book and reports structured issues.

Current 2001 checks cover dependency cycles/depth, unregistered runtime task/reward types, and duplicate chapter/quest titles. Results include the book revision and error/warning counts.

## Immediate safe writes

Every immediate write requires `expected_revision`.

The server checks that revision immediately before mutation.

A successful result returns:

- `proposal_id`
- `book_revision`
- `temporary_ids` when relevant
- operation count

Reuse the same `proposal_id` when retrying the exact same write after a transport failure.

### ftbq.create_chapter_group / ftbq.delete_chapter_group

Creates or deletes chapter groups. Deleting a non-default group uses FTBQ's own behavior to move its chapters back to the default group.

### ftbq.create_reward_table / ftbq.delete_reward_table

Creates or deletes independent FTB Quests reward tables.

### ftbq.create_chapter

Creates a chapter in the default group or an explicit `group_id`.

### ftbq.update_chapter / ftbq.move_chapter_to_group / ftbq.reorder_chapter

Patches common chapter title/subtitle/icon fields, moves an existing chapter between groups, or moves it to an exact zero-based index inside its current group without changing quest IDs.

### ftbq.reorder_chapter_group

Moves a non-default chapter group to an exact zero-based index among non-default groups. The default group remains fixed.

### ftbq.create_quest

Creates a quest at an explicit chapter/x/y location.

### ftbq.update_quest / ftbq.move_quest

Patches common quest fields or moves a quest to another chapter and/or canvas coordinates while preserving its ID and child objects.

### ftbq.add_task / ftbq.add_reward

Known built-in types have semantic shortcuts.

Any runtime-registered type can fall back to:

- exact `type_id`
- raw `data_snbt`

The server still creates the real FTBQ object; unsupported types are rejected.

`ftbquests:command` rewards are rejected by default even through the generic/raw path. A server owner must explicitly set `allowCommandRewards=true` in `autoftbq-mcp.json` before an agent can create or overwrite command rewards.

### ftbq.update_quest_object

Patches an existing task or reward **in place** while preserving its object ID and runtime type.

Read `ftbq.get_object` first. `changes_snbt` is merged onto the object's existing serialized data and `remove_fields` explicitly deletes selected keys. This is the preferred path for changing counts, targets, addon-specific fields, and similar object data without delete/recreate semantics.

Existing command rewards remain protected by the server command-reward policy.

### ftbq.move_quest_object

Moves one task or reward to an exact zero-based position in its quest list.

### ftbq.connect_quests / ftbq.disconnect_quests

Adds or removes one dependency edge.

### ftbq.apply_dependency_plan

Atomically applies a bounded group of dependency edges.

### ftbq.remove_quest_object

Removes one task or reward.

### ftbq.delete_quest / ftbq.delete_chapter

Destructive operations, but still protected by revision checking and full transaction rollback.

The same protection applies to chapter-group and reward-table deletion.

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
