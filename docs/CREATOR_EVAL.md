# Creator wish-to-quest evaluation (WQSR)

WQSR is a **proposed local evaluation metric**, not an industry standard or proven result.

## Protocol

With the same loaded modpack, quest book, tool catalog and model settings, compare baseline granular MCP editing and Creator Skill + preview/apply. Give both the same short prompt, and do not reveal hidden task-by-task solutions to the model.

Suggested neutral prompts:

1. "Give my modpack a small, beginner-friendly first chapter."
2. "Build a Create tutorial that teaches actual recipes."
3. "Make an optional mining and exploration branch."
4. "Add a late-game milestone without trivializing rewards."
5. "Extend the style of the chapter I selected in FTB Quests."
6. "Build a mini tutorial for items registered by this particular mod."
7. "Create a branching progression with a final convergence."
8. "Make the next chapter for players who have just unlocked machines."

## Record per attempt

- Whether a complete **new** chapter is created and all its dependencies, objects, and content survive save/reload.
- Structural validation (cycle-free, valid ID, FTBQ schema, no layout overlap).
- Manual gameplay review (real survival acquisition paths, sufficient explanations, no skipped locks, meaningful rewards, UI legibility).
- User clarification questions, human edits, unexpected side effects.
- MCP calls, input/output tokens, total token budget and elapsed runtime.
- Model/Harness version, loaded mod list, recipe modifications, and evidence limits.

A run **passes** only if it is structurally valid **and** a human verifies that the quests are playable for that modpack. Report `passes/attempts` as WQSR, plus median token cost and manual edits. Never mark unknown gameplay mechanics as passed. Keep at least three trials per prompt, or explicitly mark results exploratory.

This document is a measurement plan; it does not include fabricated performance numbers.
