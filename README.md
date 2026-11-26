# Tadone

Your magic task helper — a Kotlin Compose desktop app for students. 3D-minimal "clay" UI,
SQLite storage, Google Classroom via [Composio](https://composio.dev), and Gemini as an inline study helper.

Palette: `#CE7F20` `#EEDFC7` `#607938` `#533416`

## Features
- Animated splash, tilting 3D task cards, ambient motion
- Local tasks in SQLite (`%APPDATA%\Tadone\tadone.db`, `~/.local/share/Tadone` on Linux)
- Natural-language quick add ("lab report due friday 5pm")
- Google Classroom sync: courses, coursework, your submission state
- Upload a file (via Google Drive) or attach a link to a submission, turn in / unsubmit
- Push title/description edits back to Classroom (teacher accounts)
- "Ask Tadone": per-task and global AI chat that knows your task list
- One sign-in for all connected apps; tasks stay local

## Setup
1. On first launch, **Sign in** (Google, GitHub or email via Composio) — or reuse an existing
   `composio login` on this machine, or continue offline. The account keys are stored automatically.
2. Open **Apps** and click **Connect** on Google Classroom (plus Drive, Docs, Sheets, … as needed).
   Tadone creates the Composio auth config for you; you only approve access in the browser.
3. Add a **Gemini API key** in Settings ([AI Studio](https://aistudio.google.com/apikey), or `GEMINI_API_KEY`).

A project API key can still be pasted under Settings → Advanced.

## Build
```bash
./gradlew run                 # run locally (JDK 17)
./gradlew packageMsi          # Windows installer (on Windows)
./gradlew preview             # headless screenshots → /tmp/tadone-shots
```
Every push triggers `.github/workflows/release.yml`, which builds MSI + EXE on Windows
and publishes a GitHub release `v1.0.<run>` (pre-release for non-main branches).
