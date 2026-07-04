---
name: PocketCraft Architecture
description: Key design decisions for the PocketCraft Android app — process management, status persistence, and MVVM wiring.
---

## Status persistence contract
`ServerForegroundService.collectStatusUpdates()` is the single authoritative writer for DB status. It collects `ServerProcessManager.allStatuses` as a Flow and diffs against previous state to call `serverProfileDao.updateStatus()` on every change. ViewModels never write status directly — they combine DB state with live process state for display.

**Why:** Earlier design had process callbacks only updating in-memory state; crash/exit statuses were lost on app restart. Centralizing DB writes in the service guarantees persistence.

## ServerProcessManager singleton
`ServerProcessManager` is a plain Kotlin `object` singleton (not Hilt-scoped) so that both the `ServerForegroundService` and all `ViewModel` instances can observe the same `allStatuses: StateFlow<Map<String, ServerStatus>>` without requiring service binding.

**Why:** AIDL binding is complex; a shared in-process singleton is simpler and sufficient for a single-process app.

## Delete race condition guard
`HomeViewModel.deleteServer()` polls `processManager.isRunning(id)` with a 500ms interval up to 35s (matching the graceful-stop timeout in `ServerProcess`) before deleting DB row and files. This prevents file deletion racing with an active JVM child process.

## StepScaffold layout rule
Wizard screens use a non-scrollable outer `Column` with `weight(1f)` on the inner scrollable content `Column`, and the CTA button outside the scroll area. Using `weight(1f)` OR `Spacer(weight)` inside a `verticalScroll` Column causes a runtime measure exception.

## Compose rules applied
- `StatusDot`: always creates `rememberInfiniteTransition`; conditionally applies the animated value (avoids conditional composable calls).
- Icon references: `Icons.Rounded.Dns` not `Icons.Default.DnsRounded` (latter doesn't exist in extended icons).
