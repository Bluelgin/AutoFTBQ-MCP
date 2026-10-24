# Contributing

AutoFTBQ MCP welcomes community and AI-assisted ports. Please read `AGENTS.md` and `docs/AI_PORTING.md` before requesting or developing a new target.

## License and code ownership

The current project-owned mainline is offered under **MIT** (`LICENSE`). By contributing, you confirm you own the contribution or have permission to make it available under MIT, including AI-assisted work. Keep third-party notices. Do **not** copy incompatible/copyleft-only source from another project without separate authorization. Minecraft, Forge/Fabric/NeoForge, FTB Quests and other dependencies/assets retain their own license terms.

Prior tagged GPL-3.0-only releases retain the rights granted under their original terms; a new LICENSE file does not revoke those rights. See `docs/LICENSE_HISTORY.md`.

## Workflow

1. Check `porting/targets.yml`, issues and PRs before porting.
2. Discuss the version tuple and risks; obtain the user's explicit development authorization if acting as an agent.
3. Work on a feature branch and preserve common MCP contracts and server security.
4. Run architecture, metadata and target-specific build/test commands; perform real in-game tests when possible.
5. Include the PR template, test evidence, disclosure of AI assistance and copyright provenance.
6. Maintainers merge after review, not automatically. CI passing does not establish game compatibility.

Never submit bearer tokens, private world data, hard-coded model keys or `runtime.json`.

Reference checks: `python scripts/check-architecture.py`, `python scripts/check-porting-metadata.py`, `./gradlew build` for current Forge.
