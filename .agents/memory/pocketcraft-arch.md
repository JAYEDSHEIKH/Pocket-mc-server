---
name: PocketCraft architecture
description: Key architectural patterns, singleton bridges, API quirks, and non-obvious constraints for the PocketCraft Android project.
---

## ServerProcessManager singleton bridge
- `ServerProcessManager` is a plain Kotlin singleton (not Hilt) because it must survive across service/ViewModel lifecycle boundaries. Access via `ServerProcessManager.instance`.
- `ServerForegroundService` (Hilt `@AndroidEntryPoint`) reads `ServerProcessManager.instance` directly.
- `HomeViewModel` and `ServerDetailViewModel` also read `.instance` directly.
- `_allStatuses` is a `MutableStateFlow<Map<String, ServerStatus>>` — both ViewModels combine it with Room DB flows to get live status.

## Error event bus
- `ServerProcessManager._errorEvents` is a `MutableSharedFlow<Pair<String, String>>(extraBufferCapacity=20)`.
- `manager.emitError(serverId, message)` is called from `ServerForegroundService` (JRE missing, dir missing, jar missing) and from the `updateStatus()` hook when a process crashes.
- `ServerDetailViewModel.init()` collects `processManager.errorEvents` filtered by serverId and surfaces them as `AppError` dialogs.
- `ServerProcess` sets `lastError` before calling `onStatusChange(CRASHED)`, so `updateStatus()` can read it from the map before removing the entry.

## API 34 startForeground fix
- `ServerForegroundService.onCreate()` branches on `Build.VERSION.SDK_INT >= UPSIDE_DOWN_CAKE` to pass `ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE` to the 3-arg `startForeground()` overload.
- Manifest declares `<property android:name="android.app.PROPERTY_SPECIAL_USE_FGS_SUBTYPE" ...>`.

## Paper Fill v3 API
- Base URL: `https://fill.papermc.io/v3/`
- Versions endpoint: `/projects/paper` returns a map of major-version → [mc-version strings]
- Builds endpoint: `/projects/paper/versions/{mc_version}/builds` returns a list of `PaperBuild`
- Download key is `"server:default"` inside the `downloads` map of each build
- `latestStable()` extension picks the build with highest `id` where `channel == "STABLE"` (case-insensitive)

## JRE bundling — CI only
- The JRE tarball `assets/jre/aarch64-jdk21.tar.gz` is NOT committed to git — it is injected at build time by `.github/workflows/build.yml`
- The workflow downloads from Adoptium API: `https://api.adoptium.net/v3/assets/latest/21/jdk?architecture=aarch64&image_type=jre&os=linux&vendor=eclipse`
- Without CI, `JreInstaller.getJavaBinary()` returns null and `ServerForegroundService` emits a detailed error via `manager.emitError()`
- `JreInstaller.findExistingBinary()` is a fast (non-suspend) existence check used by App Settings to show JRE status

## RCON password security
- A 24-char random RCON password is generated per server using `SecureRandom` and written only to `server.properties` inside app-private storage
- Password never stored in Room DB, never logged

## Storage path convention
- `StorageManager.rootDir` → `getExternalFilesDir("PocketCraft") ?: filesDir`
- `StorageManager.serverDir(id)` → `rootDir/servers/{id}`
- `StorageManager.resolveServerDir(id)` → checks legacy `filesDir/servers/{id}` first (migration compat)
- `StorageManager.createServerDirs(id)` → creates server/, plugins/, world/, logs/ subdirs and returns root
- `CreateServerViewModel.createServer()` uses `storageManager.createServerDirs(profile.id)` — all code paths now agree

## Configurable runtime preferences (AppPrefs)
- `stopTimeoutSeconds` (5–120, default 30) — read in `ServerProcess` via `AppPrefs.stopTimeoutSeconds.value`
- `consoleMaxLines` (500–10000, default 2000) — read in `ServerDetailViewModel` console scan
- `autoScrollConsole` (default true) — read in `ConsoleTab` LaunchedEffect
- `keepScreenOn` (default false) — applied via `DisposableEffect` in `ServerDetailScreen` when status == RUNNING
