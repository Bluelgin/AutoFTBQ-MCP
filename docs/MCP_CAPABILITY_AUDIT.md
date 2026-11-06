# AutoFTBQ-MCP capability audit — FTB Quests 2001.x

> Baseline: \`main\` at \`84d58a8\` (2026-11-06), Forge 1.20.1 / Java 17.
> This is a **source-level API/implementation audit**, not a claim that all FTBQ
> GUI features, addon task types or in-game workflows were tested.

## Executive summary

**Do not rewrite the MCP editor.** Existing code already exposes substantial,
server-authoritative FTBQ read/edit operations. The priority gap is a safe,
composable **inspect → plan → review diff → commit → verify** workflow for
modifying *existing* quest books, with compact reads and clear handling of
unknown third-party types. Creator is an optional facade, not a replacement
for those general-purpose tools.

The separate draft Creator PR #3 is **not merged** as of this audit. Its
optional compact blueprint, item index and temporary draft are incremental
authoring ergonomics; they do not close the general editing gap. Keep future
PRs independent and explicit about their target base.

## Capability coverage from source

| Surface | Existing APIs and source | Assessment | Gap / regression risk |
| --- | --- | --- | --- |
| Context and book discovery | \`ftbq.get_context\`, \`get_book\`, \`get_chapter\`, \`get_quest\`, \`get_object\` — \`McpToolService\`, \`FTBQ2001ReadService\` | Present | A scoped chapter read includes per-object \`data_snbt\`; no cheap, structured, revision-bound inventory of every task/reward, dependencies and coordinates in one call |
| Registry / recipes | \`minecraft.capabilities\`, \`search_registry\`, \`validate_ids\`, \`inspect_item\`, \`search_recipes\`, \`inspect_resource\` — \`GameDataCatalog\`, \`FTBQ2001QueryExecutor\` | Present, partial evidence | Registry membership does **not** imply survival obtainable; scripts, machines, loot/trades and progression gates not fully covered |
| Runtime task/reward types | \`ftbq.list_task_types\`, \`list_reward_types\`, \`get_type_schema\` — \`FTBQ2001TypeCatalog\` | Present | Curated fields exist for known types; unknown add-ons return runtime defaults, **not** a complete validated schema |
| Structural edits | Chapter/group, quest, reward-table create/edit/order/move/delete, dependencies — \`McpToolCatalog\`, \`McpToolService\` | Present | Some editing operations accept broad raw objects; preserve unknown fields and avoid accidental child deletes |
| Quest objects | \`add_task\`, \`add_reward\`, \`update_quest_object\`, \`move_quest_object\`, \`remove_quest_object\`; semantic item/checkmark/XP and generic typed/raw fallback — \`McpToolService\`, \`FTBQ2001ProposalExecutor\` | Present | Not every addon type can safely round-trip via curated JSON alone; raw SNBT is an escape hatch, not a fully understood schema |
| Bulk edit | \`ftbq.apply_operations\` (up to 1000), \`apply_dependency_plan\` — \`FTBQ2001ProposalExecutor\` | Present | **No general preview/diff endpoint** for proposed changes to existing objects; a list of operations alone does not prove resulting state |
| Transactions | \`transaction_begin/stage/status/commit/abort\` — \`TransactionStagingService\` | Present | Staging is not a semantic preview; default TTL 15 min; verify conflict/retry handling |
| Atomicity / rollback | Server-authoritative expected revision, permission check, full network snapshot, rollback, save/sync, proposal idempotency, \`undo_last\` — \`FTBQ2001ProposalExecutor\` | Present, code reviewed | 16 MiB undo snapshot ceiling; only last proposal undo for the actor; snapshot failure and concurrent writers need in-game validation |
| Visual inspection / design | \`validate_book\`, \`get_layout_snapshot\`, \`extract_design_profile\`, \`compare_design_profiles\`, \`analyze_layout\`, captures, map rendering — \`FTBQ2001VisualService\` and \`QuestDesignAnalyzer\` | Present | Layout assessment is **not** a proof of gameplay quality; screenshots/visual tools need live GUI testing |
| Creator (unmerged PR #3) | \`creator_context/preview/apply\` and compact item-first drafts — draft PR #3 | Draft / separate | Supports **new chapters**, not a universal existing-book edit API; do not duplicate existing low-level semantics |

**Read limits:** Existing \`get_quest\` includes every task/reward’s serialized
SNBT through \`objectBase\`. \`get_chapter\` returns quest titles/positions/counts,
but not their full task/reward metadata and dependencies. A compact
chapter-scoped snapshot can close this gap without inventing a new serialization
format or leaking bulky raw data by default.

## Confirmed design boundaries

1. The server is the authority on editor permissions, live book revision,
   command rewards and final object validity. Client-side preview is **advisory**.
2. All mutations should reuse the existing \`applyProposal\` path, snapshot,
   rollback, save, sync, and revision/idempotency checks. Do not create a
   second writer or relax command-reward policy.
3. Expose **read-only** compact snapshots with explicit \`book_revision\`,
   scope, completeness/truncation, and field provenance. Do not silently
   truncate task lists or imply exhaustive knowledge.
4. A preview should show existing targets' known *before* values and proposed
   *intent*, not claim an exact after-state for arbitrary SNBT or addon types.
   For unknown operations mark \`effect_unverified\` or reject preview.
5. Applying a preview must require explicit \`confirm=true\`, unchanged hash,
   fresh expected revision and normal server-side permission validation.
   A short-lived draft ID is an optimization, not an authorization token.
6. Preserve third-party NBT on unrelated edits; if the server rejects a type
   or a plan would overwrite unknown fields, fail closed with actionable error.
7. Keep Creator optional. The general API must permit arbitrary Host/Agent
   research, creation and iterative edits without a particular prompt workflow.

## Prioritized implementation plan (separate PRs)

### PR B — Compact chapter snapshot and general edit preview

- Add a bounded read-only \`ftbq.get_chapter_snapshot\`: chapter ID/title,
  revision, quest ID/title/coordinates, dependency IDs, task/reward ID/type/title
  and counts. Exclude all \`data_snbt\` by default; full \`get_object\` remains
  available on demand. Preserve truthful truncation/coverage indicators.
- Add a bounded read-only \`ftbq.preview_operations\` for reviewing edits to
  existing books. Include current revision, effect categories, stable target
  IDs, change fields, destructive flags, warnings, deterministic plan hash
  and an optional short-lived draft reference.
- The preview must **not** claim complete semantic validation for raw SNBT,
  addon-specific schemas or compound changes across a batch. Preserve
  server preflight on actual commit, plus existing \`apply_operations\`.
- A later \`ftbq.apply_preview\` shortcut may reuse the exact reviewed batch,
  but it must retain explicit confirmation and server guards. Never silently
  apply a revised plan after a stale revision.

### PR C — Typed edits, without making addon schemas up

- Inventory task/reward type coverage, classify each type as semantic,
  curated, runtime-defaults-only, or unknown.
- Add typed builders only where there is a verified mapping. Unknown
  addon types remain inspectable/raw-round-trippable, not magically
  editable via invented field names.
- Exercise \`patch_quest_object\` preservation when altering unrelated
  fields; specifically test custom/unknown tags, item-stack tags, and
  command-reward restrictions.

### PR D — Harness workflow and acceptance evidence

- Ship an agent workflow: discover scope → inspect snapshot → ask for
  specific missing data → plan → diff preview → approval → commit → read-back.
- Use equivalent 10-, 30- and 48-quest test fixtures and comparable
  permissions, revisions and installed addons.
- Measure actual model input/output tokens, number of tool calls, JSON
  bytes, user interventions and errors separately; never infer token
  savings directly from JSON byte counts.
- Include survival/pack playability and user-facing book legibility as
  **human-tested**, not unit-test-passed, fields.

## Required acceptance matrix

| Case | Verification | Gate |
| --- | --- | --- |
| Read selected existing chapter | Snapshot includes IDs/links/tasks/rewards without SNBT, with explicit counts and revision | Automated + live |
| Rename/reposition only | Diff names exact objects/fields; unrelated task/reward data unchanged after save/reload | In-game |
| Delete task/quest | Preview highlights destructive effect and affected IDs; server still controls commit | Automated + live |
| Invalid dependency or ID | No partial writes, explicit error, unchanged revision | Automated + live |
| Unknown addon task/reward | Inspect without corruption; unrelated edit preserves underlying serialized data | Live addon fixture |
| Stale preview / simultaneous editor | Commit rejects mismatched revision and requires re-preview | Multiplayer |
| Permission denied / command reward | Server rejects; no shortcut can bypass server policy | Multiplayer |
| Retry / undo | Identical proposal safe to retry; last valid proposal reversible within existing boundaries | Automated + live |
| Save/reopen / server sync | Task and reward data remain identical when not edited | Dedicated server |
| Token benchmark | Real Harness usage and reproducible fixtures; results explicitly labeled | Evaluation |

## Current status

- **Source audit complete for the checked repository files only.**
- No FTB Quests upstream full GUI feature inventory or third-party addon
  integration matrix was available in this pass; **do not claim 100% coverage**.
- No runtime game, multiplayer or gameplay-quality result is implied by this
  document. CI success validates only the code/tests that actually run.
