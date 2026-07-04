# PocketCraft

An Android app that lets you create, configure, run, and manage Minecraft: Java Edition servers (PaperMC and Fabric) entirely on your phone — no PC, no root required.

## Project overview

- **Type:** Native Android (Kotlin + Jetpack Compose + Material 3)
- **Architecture:** MVVM + Hilt DI + Room + Kotlin Coroutines/Flow
- **Phase 1 (current):** Paper-only MVP — create → download → start → live console → stop

## Tech stack

| Layer | Technology |
|-------|-----------|
| Language | Kotlin 2.0 |
| UI | Jetpack Compose + Material 3 |
| DI | Hilt |
| DB | Room |
| Networking | Retrofit + OkHttp + Moshi |
| Background work | WorkManager |
| JVM | Bundled aarch64 OpenJDK 21 (extracted to app-private storage on first run) |

## Building

This project **cannot be compiled locally** — there is no Android SDK in this environment. All build output is produced by GitHub Actions.

**To get a debug APK:**
1. Push to `main` or `develop` — the CI workflow in `.github/workflows/build.yml` triggers automatically
2. Go to Actions → the latest run → **Artifacts** → download `PocketCraft-debug-<N>.apk`

The CI workflow automatically downloads the Azul Zulu OpenJDK 21 aarch64 JRE and bundles it as an asset before building.

## API references

- **Paper Fill API:** `https://fill.papermc.io/v3/projects/paper/versions/{mc_version}/builds`
- **Fabric Meta API:** `https://meta.fabricmc.net/v2/versions/loader/{mc_version}` (Phase 3)
- **Modrinth API:** `https://api.modrinth.com/v2/search` (Phase 3)

## Implementation phases

| Phase | Status | Scope |
|-------|--------|-------|
| 1 | ✅ Complete | Paper only: create → download → start → console → stop, file manager, settings |
| 2 | ⬜ | Full Material 3 UI pass, server.properties editor, Players tab (RCON) |
| 3 | ⬜ | Fabric support, Plugins/Mods tab (Modrinth) |
| 4 | ⬜ | Multi-server dashboard, Network tab + tunnel, backups, onboarding, haptics |

## Important: Getting a working APK

The app **requires the JRE asset to be bundled at build time**. This is done automatically by GitHub Actions:

1. Push to `main` or `develop`
2. Go to **Actions → latest run → Artifacts → PocketCraft-debug-N.apk**

Building locally without CI produces an APK that will crash when starting servers (JRE not found).

## What was built / fixed (Phase 1 overhaul)

- **GitHub Actions CI** (`.github/workflows/build.yml`) — downloads OpenJDK 21 aarch64 from Adoptium and bundles it as the asset before building the APK
- **Error surfacing** — server crashes now emit detailed pop-up dialogs explaining the exact cause (missing JRE, missing jar, directory error, network error)
- **Server create errors** — creation failures now show an error dialog with expandable stack trace and close ×
- **Storage consistency** — `CreateServerViewModel` now uses `StorageManager.createServerDirs()` so all code paths agree on where server files live; external app-specific storage with legacy-path migration
- **ServerProcessManager** — added `errorEvents` SharedFlow; service and process both emit structured error messages to the UI
- **Configurable settings** — stop timeout (5–120 s), console max lines (500–10 000), auto-scroll toggle, keep-screen-on toggle; all wired into runtime
- **JRE status in App Settings** — shows whether JRE is installed with full path; re-extract button explains the CI-only constraint
- **Storage path display** in App Settings
- **Service startup pre-flight** — ensures server directory exists before writing eula.txt / checking server.jar; each failure emits a human-readable error event

## Key files

```
app/src/main/java/com/pocketcraft/app/
├── core/
│   ├── jre/JreInstaller.kt          — extracts bundled JDK 21 on first run
│   ├── downloader/PaperApi.kt       — Retrofit client for fill.papermc.io
│   ├── downloader/DownloadWorker.kt — WorkManager worker for jar downloads
│   ├── process/ServerProcess.kt     — ProcessBuilder wrapper + console flow
│   ├── process/ServerProcessManager.kt — singleton registry of live processes
│   └── process/ServerForegroundService.kt — foreground service (keeps JVM alive)
├── data/
│   ├── ServerProfile.kt             — Room entity (server config + status)
│   ├── ServerProfileDao.kt
│   └── AppDatabase.kt
├── ui/
│   ├── theme/                       — Color.kt, Type.kt, Theme.kt (dark control-room)
│   ├── home/                        — Server list + status cards
│   ├── createserver/                — Multi-step wizard
│   └── serverdetail/console/        — Live log viewer + command input
├── navigation/NavGraph.kt
└── MainActivity.kt
```

## Per-server runtime layout

```
filesDir/servers/<server-uuid>/
├── server.jar
├── eula.txt          (written by app after explicit EULA acceptance)
├── server.properties
├── world/
├── plugins/          (Paper only)
└── logs/latest.log
```

## User preferences

- Distribution via direct APK / GitHub Releases (not Play Store — app executes downloaded native code + JVM at runtime, which Play Store restricts)
- minSdk 26, arm64-v8a ABI only
- Dark theme is the default and only theme in v1
