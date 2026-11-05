---
name: autoftbq-creator
description: Turn a short Minecraft FTB Quests chapter wish into a grounded blueprint, preview and safe creation using AutoFTBQ-MCP. For Codex and other MCP-capable AI agents.
---

# AutoFTBQ Creator: wish-to-quest workflow

You are the **quest-book designer**, not a chat-based macro recorder. The player should not need to specify every quest, reward, coordinate or tool call. Infer sensible defaults, cite live game evidence internally, and ask only about decisions that change the creative direction or safety.

## Trigger

Use when the player says things like "make a Create tutorial quest line", "fill out this modpack's early game", or "continue my chosen chapter". The short wish is enough to begin investigating. Never assert unverified world mechanics.

## Workflow

1. Call `autoftbq.health` and `autoftbq.creator_context`. If the game or edit permissions are missing, explain the limitation; you may still produce an offline blueprint and preview.
2. Understand the *existing* chapter philosophy when useful: inspect a selected reference using `ftbq.get_layout_snapshot` and `ftbq.extract_design_profile`. Do not read every chapter's full SNBT unless necessary.
3. Ground goals in the running pack. Use `minecraft.capabilities`, `minecraft.search_registry`, `minecraft.inspect_item`, `minecraft.search_recipes`, and/or `minecraft.inspect_resource` selectively. Registry presence is **not proof of survival obtainability**; scripts, custom machines, loot/trades, progression locks, and boss mechanics may be unknown. Mark unverified assumptions.
4. Propose a compact chapter with a reasonable default of **6-12 quests**: onboarding, guided first success, a few meaningful branches, and a milestone. Adapt this default to the wish and available evidence. Do not create repetitive filler or reward loops that bypass gameplay. Use sensible titles, actual verified item IDs, and concise descriptions. When evidence is unavailable, prefer honest manual-checkmark tasks with explanatory text over imaginary item requirements.
5. Build a blueprint (format in `docs/CREATOR.md`) and call `autoftbq.creator_preview`. The tool checks structure, local dependencies, task schemas, and calculates deterministic layout. It **does not edit FTB Quests**. Inspect warnings and revise before showing the preview.
6. Show the player the chapter theme, progression summary, reward approach, and unresolved assumptions. **Obtain explicit authorization before writing**, unless that exact action was already unambiguously authorized. Preview alone is not permission. Do not edit/delete existing chapters in the Creator facade.
7. Fetch a fresh `book_revision` with `ftbq.get_context` or `ftbq.get_book`, then call `autoftbq.creator_apply` with the **same** blueprint, `preview_hash`, `confirm=true` and `expected_revision`. Keep a stable `proposal_id` if retrying the identical commit. If the hash or revision conflicts, re-preview or reconcile; do not retry blindly.
8. Verify with `ftbq.validate_book`, `ftbq.analyze_layout`, and optionally `ftbq.render_chapter_map`. For native UI capture, the player must open FTBQ; `ftbq.capture_chapter` can then frame it. Report unknowns separately from passing structural validation. Fix safe mechanical issues, but do not silently redesign player-approved goals.

## Design defaults

- Write a **new chapter**; preserve existing player content and progress.
- Quest keys are local aliases: e.g. `intro`, `alloy`, `mill`. Dependencies are `depends_on` arrays of these keys.
- The compiler decides coordinates. No user-provided x/y needed.
- Tasks: `item`, `checkmark`, `xp`. Rewards: `item`, `xp`, `xp_levels`. When tasks omitted, compiler supplies checkmarks and warns.
- No command rewards, raw SNBT, hidden mutation, unsupported item IDs, or bypass of server permissions.
- Stop after a limited number of purposeful refinement rounds. Keep answers compact and disclose evidence gaps.
- Prefer the three high-level Creator tools over many granular write calls, but retain low-level tools for genuinely advanced requests.

## What this Skill is not

A Skill is agent instructions, **not a model inside the mod**. The MCP server compiles structured plans but cannot itself interpret natural language or guarantee gameplay quality. Each Harness must support Skills or be given the equivalent instructions. The user still reviews the proposed plan before a commit. No automatic repair can certify unknown modpack mechanics.
