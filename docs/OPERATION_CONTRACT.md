# Version-neutral operation contract

`ftbq.apply_operations` and staged transactions accept an array of operation objects.

This is an advanced interface. Semantic MCP tools should be preferred because they can validate intent more strongly.

## Safety properties

The server:

- parses the complete array before mutation
- limits a batch to 1000 operations
- validates strict SNBT before mutation
- snapshots the book before mutation
- resolves temporary IDs only inside the current batch
- refreshes FTBQ's ID map between operations
- rolls the whole batch back if any operation fails

## Semantic operations

### create_chapter_group

Fields:

- `temp_id`
- `title`

Creates a real FTBQ chapter group and returns its real ID in the transaction temporary-id map.

### create_reward_table

Fields:

- `temp_id`
- optional `title`

Creates an independent FTBQ reward table.

### create_chapter

Fields:

- `temp_id`
- `title`
- optional `subtitle`
- optional `icon`
- optional `group_id` (real or temporary chapter-group id)

### create_quest

Fields:

- `temp_id`
- `chapter_id`
- `title`
- optional `subtitle`
- optional `description`
- optional `icon`
- `x`
- `y`

`chapter_id` may reference a temporary chapter created earlier in the same transaction.

### update_chapter

Fields:

- `chapter_id`
- `changes`

Allowed common changes are title, subtitle, and icon.

### move_chapter_to_group

Fields:

- `chapter_id`
- `group_id`

The executor removes the chapter from its previous group before adding it to the new group.

### update_quest

Fields:

- `quest_id`
- `changes`

Allowed common changes are title, subtitle, description, icon, x, and y.

### move_quest

Fields:

- `quest_id`
- optional `chapter_id`
- optional `x`
- optional `y`

At least one destination field must be present at the MCP semantic-tool layer. The quest keeps its ID, tasks, rewards, and dependencies.

### add_dependency

Fields:

- `quest_id`
- `dependency_id`

Self-dependencies and invalid dependency graphs are rejected.

### remove_dependency

Fields:

- `quest_id`
- `dependency_id`

The edge must already exist.

### add_item_task

Fields:

- `quest_id`
- `item_id`
- `count`

### add_checkmark_task

Fields:

- `quest_id`

### add_xp_task

Fields:

- `quest_id`
- `amount`

### add_item_reward

Fields:

- `quest_id`
- `item_id`
- `count`

### add_xp_reward / add_xp_levels_reward

Fields:

- `quest_id`
- `amount`

### add_typed_quest_object

Generic runtime type creation.

Fields:

- `quest_id`
- `object_kind`: `task` or `reward`
- `type_id`: exact runtime-registered type
- `data_snbt`

This is the primary extension path for third-party FTBQ task/reward implementations.

### patch_quest_object

Fields:

- `object_id`
- `changes_snbt`: compound SNBT patch
- optional `remove_fields`: string array

The server first serializes the existing task/reward, merges the patch, removes explicitly listed fields, and reads the result back into the **same object**. The object ID and registered runtime type cannot change.

Command rewards remain blocked by policy unless explicitly enabled by the server owner.

### move_quest_object

Fields:

- `quest_id`
- `object_kind`: `task` or `reward`
- `object_id`
- `new_index`

Moves an existing task/reward to an exact zero-based index without recreating it.

### remove_quest_object

Fields:

- `object_id`

Only tasks and rewards are accepted.

### delete_quest

Fields:

- `quest_id`

### delete_chapter

Fields:

- `chapter_id`

### delete_chapter_group

Fields:

- `group_id`

The default group cannot be deleted.

### delete_reward_table

Fields:

- `reward_table_id`

## Raw round-trip operations

These exist to preserve fields from addon types and FTBQ features not yet represented by semantic tools:

- `update_book_raw`
- `upsert_chapter_group_raw`
- `delete_chapter_group`
- `upsert_reward_table_raw`
- `delete_reward_table`
- `upsert_chapter_raw`
- `upsert_quest_raw`
- `upsert_quest_object_raw`

Raw writes use strict SNBT parsing. Unknown extra operation fields are rejected so typos do not silently become no-ops.

For `upsert_quest_object_raw`, an existing task/reward must keep its real runtime `type_id`; supplying a different type ID is rejected. This closes policy bypasses and prevents accidental reinterpretation of addon objects.

## Temporary IDs

A batch may create a chapter group, reward table, chapter, or quest with a string `temp_id`.

Later operations in the same batch may use that string wherever the executor accepts a chapter or quest reference.

The commit result returns the mapping from temporary strings to real FTBQ IDs.
