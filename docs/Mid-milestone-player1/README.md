# Player Pro — Detailed Codex Milestone Prompts

Use these prompts one at a time in Codex/agentic coding. Do **not** ask Codex to do all milestones at once. Each milestone is intentionally scoped so the project moves toward a stable, lightweight, Gemini-style headunit video player.

Core principles:
- Video-first UI.
- Exactly one ExoPlayer instance.
- No duplicate floating player.
- Playlist drawer is UI only, never a player owner.
- Heavy features are off by default.
- MediaStore + Room cache + DataStore settings.
- Maestro tests for each milestone.

Recommended order:
1. Single Player Engine Stability
2. Playlist Drawer Interactions
3. Video-First Auto-Hide Controls
4. Floating + Play as Music Rewrite
5. Library Cache + Thumbnails
6. Headunit Performance Polish
7. Maestro Integration Tests
