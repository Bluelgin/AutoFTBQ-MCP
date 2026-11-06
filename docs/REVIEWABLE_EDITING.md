# Reviewable existing-book edits (FTB Quests 2001)

This is a **general MCP authoring interface**, independent of the optional
Creator workflow. Agents can inspect, propose, review and commit existing-book
edits using the same operation contract. It does **not** run an LLM or require
any particular Host/Skill.

## Read a compact chapter snapshot

Tool: `ftbq.get_chapter_snapshot`

~~~json
{"chapter_id":"<existing chapter ID>","offset":0,"limit":20}
~~~

- Returns client `book_revision`, `can_edit`, chapter ID/title, total quest
  count, quest ID/title/coordinates, dependencies and task/reward ID/type/title.
- Raw `data_snbt` is not returned. Use `ftbq.get_quest` / `ftbq.get_object`
  for specific objects whose raw data is necessary.
- At most 30 quests/page (default 20). Each quest reports full task/reward
  and dependency counts, with `*_truncated` flags if a collection exceeds 32.
  Use `next_offset` and `has_more` to paginate.
- This is a client observation, not an authoritative server freeze. The
  server rejects stale revisions on commit.

## Preview a plan without writing to FTBQ

Tool: `ftbq.preview_operations`

~~~json
{
  "expected_revision": "<revision from get_context or snapshot>",
  "operations": [
    {"kind":"update_quest","quest_id":"<quest id>","changes":{"title":"Gather and understand"}},
    {"kind":"move_quest","quest_id":"<quest id>","x":4.0,"y":2.0}
  ]
}
~~~

Returns `status`, `preview_kind:"intent_not_simulation"`, `preview_hash`,
`draft_id`, `draft_ttl_seconds`, `book_revision`, `changes`, `warnings`,
and destructive/unverified counts. Each changed object is identified by stable
ID, known before metadata and safely bounded requested field values. This deliberately omits raw SNBT.

Reviewable initial subset: create chapter/quest, update/move/reorder chapter
and quest, remove task/reward, delete quest/chapter, add official semantic
tasks/rewards, add typed quest objects, patch object SNBT, and
connect/disconnect dependencies. Unreviewable raw upserts remain available
through the existing `ftbq.apply_operations` advanced interface.

The preview checks client revision and known current target existence.
It does **not** execute FTBQ operations, compute a guaranteed after-state,
prove server permissions/validity or model unknown addon effects. Typed/SNBT
changes are explicitly marked `effect_unverified`.

## Commit the exact reviewed plan

Only after the player approves the shown plan and its risks, call
`ftbq.apply_preview`:

~~~json
{
  "draft_id": "<draft ID from preview>",
  "preview_hash": "<hash from preview>",
  "expected_revision": "<the same revision>",
  "confirm": true,
  "proposal_id": "<optional stable id for identical retries>"
}
~~~

- Drafts last 10 minutes, max 16, memory-only, and are bound to the current client world/data session; switching worlds or reloading game data invalidates reuse. A draft ID is **not**
  authorization.
- Hash binds the exact plan **and the base revision**. Wrong hash,
  mismatched revision, expired draft or missing confirmation fails closed.
- Commit still uses the original server `applyProposal` path, including
  permission checks, revision guard, command-reward policy, preflight,
  snapshot, rollback, save/sync, idempotency and undo.
- If server validation fails, re-inspect and revise; preview is not a
  substitute for a successful write or in-game acceptance.
- After commit read back the affected chapter and detailed task/reward data.

## Acceptance gaps (manual, not passed by JUnit)

- Test save/reload, UI, singleplayer and dedicated server syncing.
- Verify unrelated addon task/reward NBT survives an edit. Compare serialized
  before/after data and game behavior, not just titles and task counts.
- Exercise stale revisions, parallel editors, permissions, invalid IDs,
  command-reward policy, rollback and last-proposal undo.
- Measure real Agent/Harness token costs separately from JSON byte counts.

The original `ftbq.apply_operations`, generic reads and staged transactions
remain available for advanced scenarios. The preview is an optional review
layer, **not a limitation** on the MCP's overall capabilities.
