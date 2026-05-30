# Codex Next Prompt

You are improving Player Pro, a Kotlin Android video-only headunit app. Use the micro-module architecture. Do not add music library features. The only audio feature is Play as Music, which continues audio from the current video while hiding/detaching video rendering.

Work one milestone at a time. For each milestone:

1. Open that milestone README.
2. Implement only the files listed for that milestone.
3. Keep the public `*Feature.kt` contract small.
4. Run or update the matching Maestro YAML.
5. Do not introduce heavy dependencies.

Priority order:

1. Make Gradle compile.
2. Make app launch without videos.
3. Make MediaStore library refresh work.
4. Make ExoPlayer play one selected video.
5. Add resume playback.
6. Add controls.
7. Add Play as Music.
8. Add floating player last.
