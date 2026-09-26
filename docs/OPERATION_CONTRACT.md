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

### create_chapter

Fields:

- `temp_id`
- `title`
- optional `subtitle`
- optional `icon`

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

### update_quest

Fields:

- `quest_id`
- `changes`

Allowed common changes are title, subtitle, description, icon, x, and y.

### add_dependency

Fields:

- `quest_id`
- `dependency_id`

Self-dependencies and invalid dependency graphs are rejected.

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

## Temporary IDs

A batch may create a chapter/quest with a string `temp_id`.

Later operations in the same batch may use that string wherever the executor accepts a chapter or quest reference.

The commit result returns the mapping from temporary strings to real FTBQ IDs.
