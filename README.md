# Tadone ✨

Your magic task helper — a Kotlin Compose desktop app for students. 3D-minimal "clay" UI,
SQLite storage, Google Classroom via [Composio](https://composio.dev), and Claude as an inline study helper.

Palette: `#CE7F20` `#EEDFC7` `#607938` `#533416`

## Features
- Animated splash, tilting 3D task cards, ambient motion
- Local tasks in SQLite (`%APPDATA%\Tadone\tadone.db`)
- Natural-language quick add ("lab report due friday 5pm")
- Google Classroom sync: courses, coursework, your submission state
- Upload a file (via Google Drive) or attach a link to a submission, turn in / unsubmit
- Push title/description edits back to Classroom (teacher accounts)
- "Ask Tadone": per-task and global AI chat that knows your task list
- No login — single-user, everything local

## Setup (in the app → Settings)
1. **Anthropic API key** (or set `ANTHROPIC_API_KEY`). Default model `claude-opus-5-5`.
2. **Composio API key** (project key from dashboard.composio.dev) and a user id (default `default`).
3. Create auth configs for **Google Classroom** and **Google Drive** in the Composio dashboard,
   paste their `ac_…` ids, click **Connect**, then **Sync Classroom**.

## Build
```bash
./gradlew run                 # run locally (JDK 17)
./gradlew packageMsi          # Windows installer (on Windows)
./gradlew preview             # headless screenshots → /tmp/tadone-shots
```
Every push triggers `.github/workflows/release.yml`, which builds MSI + EXE on Windows
and publishes a GitHub release `v1.0.<run>` (pre-release for non-main branches).
