# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Build Commands

All commands run from the project root. The Gradle wrapper handles the correct Gradle version automatically.

```bash
# Debug APK
./gradlew assembleDebug          # macOS/Linux
gradlew.bat assembleDebug        # Windows
# Output: app/build/outputs/apk/debug/app-debug.apk

# Build and install on connected device
./gradlew installDebug

# Release APK (unsigned)
./gradlew assembleRelease

# Clean
./gradlew clean
```

There are no unit tests in this project.

**Requirements:** JDK 17+, Android SDK Platform API 35, Build-Tools 35.0.0. Device/emulator must be API 26+.

## Architecture

Single-Activity app using Jetpack Compose with no navigation library at the routing level — screen switching is done with a plain `var config by remember { mutableStateOf<FolderConfig?>(null) }` in `MainActivity`. When `config` is null, `SetupScreen` is shown; once the user confirms their setup, `SwipeScreen` takes over.

**Data flow:**
- `model/Models.kt` — pure data: `FolderConfig` (sourceUri + destinations map), `DestinationFolder`, `PhotoItem`, `SwipeDirection` enum
- `viewmodel/SwipeViewModel.kt` — `AndroidViewModel` exposing a single `StateFlow<SwipeState>`. Handles photo loading from SAF tree URIs, atomic move via `DocumentsContract.moveDocument` with copy+delete fallback for cross-provider moves, and one-level undo (stores `LastAction` containing the original parent URI so the file can be found and moved back)
- `ui/setup/SetupScreen.kt` — folder picker UI using `ActivityResultContracts.OpenDocumentTree`. Takes persistable URI permissions on selected folders. Requires at least one destination before enabling Start
- `ui/swipe/SwipeScreen.kt` — drag gesture handling with `detectDragGestures`, `Animatable` for fly-off animation, threshold-based direction detection (120dp), and color-coded direction overlays that fade in as the user drags

**File access model:** All file operations go through Android's Storage Access Framework (SAF). No raw file paths — everything is `Uri`-based via `DocumentFile` and `DocumentsContract`. This means the app works without broad storage permissions on Android 10+.

**Key implementation detail in undo:** After moving a file, the original `PhotoItem.uri` is stale. `undoLastAction()` re-discovers the moved file by name using `findFileInFolder()` in the destination directory before moving it back.

## Dependencies

Defined in `gradle/libs.versions.toml`. Key libraries:
- Coil (`coil-compose`) for image loading from SAF URIs
- `androidx.documentfile` for SAF tree navigation
- Material 3 + `material-icons-extended`
- No networking libraries — app is fully offline

## Local build environment (Windows, this machine)

The repo is **missing the Gradle wrapper files** — there is no `gradlew`, `gradlew.bat`, or `gradle/wrapper/gradle-wrapper.jar`. Only `gradle/wrapper/gradle-wrapper.properties` exists. Running `./gradlew` or `gradlew.bat` will fail with "not recognized". Must either install Gradle directly or generate the wrapper via `gradle wrapper`.

**Current wrapper target:** `gradle-8.7-bin.zip`. Gradle 8.7 does **not** support JDK 25 (needs Gradle 9.1+). Current toolchain pins:
- AGP `8.5.2` (libs.versions.toml) — constrains max Gradle to ~8.11
- Kotlin `2.0.21`
- JDK 25.0.2 is the only JDK installed on this machine (via `winget install Microsoft.OpenJDK.17`, which resolves to newest — got 25, not 17)

User preference: use a more recent Gradle than 8.7. Pending decision — either Gradle 8.14.x (may not work with JDK 25), or install JDK 21 (LTS) alongside.

**Environment variables already set (User scope):**
- `JAVA_HOME` = `C:\Program Files\Microsoft\jdk-25.0.2.10-hotspot`
- `ANDROID_HOME` = `C:\Users\MiniPC\.android` (user preference: install under `~/.android`, not `C:\Android`)

**Android SDK installed under `%USERPROFILE%\.android\`:**
- `cmdline-tools/latest` (from `commandlinetools-win-11076708_latest.zip`)
- `platform-tools`
- `build-tools;35.0.0`
- `platforms;android-35`
- All SDK licenses accepted

**PowerShell quirk on this machine:** fresh PowerShell sessions do not always inherit an updated PATH from the registry. To rehydrate PATH in a session before running `java`, `sdkmanager`, etc., use:
```powershell
$machinePath = [System.Environment]::GetEnvironmentVariable("PATH", "Machine")
$userPath = [System.Environment]::GetEnvironmentVariable("PATH", "User")
$env:PATH = "$machinePath;$userPath"
```

Bash (Git Bash) is also available in this environment and was used for basic file listing; prefer PowerShell for build-related work so env vars behave as expected.

## In-progress work (resumable)

**SwipeScreen entrance animation for undo** — edits applied to `app/src/main/java/com/photoswipe/app/ui/swipe/SwipeScreen.kt` but **not yet compiled/verified** because the build environment isn't ready (see above).

Goal: when the user undoes a swipe (via the edge-drag gesture or the toolbar undo button), the recovered photo should slide in from the direction it was originally sent — so it feels like you're physically pulling the photo back — instead of just appearing at center.

Implementation summary:
- New file-level helper `directionToOffset(dir, distance)` → `(Pair<Float, Float>)` mapping each `SwipeDirection` to a card translation offset at the given fly distance.
- New compose state `var enterFromDirection by remember { mutableStateOf<SwipeDirection?>(null) }` inside `PhotoSwipeContent`.
- `LaunchedEffect(currentIndex)` branches: if `enterFromDirection` is non-null, it clears the flag and animates `animOffsetX`/`animOffsetY` from their current position (which the caller pre-snapped to the entrance position) back to `0f` with `spring(stiffness = Spring.StiffnessMediumLow)`. Otherwise snaps to 0 as before.
- `flyOff`'s `then` callback changed from `() -> Unit` to `suspend () -> Unit` so `animOffsetX.snapTo(...)` can run before `onUndo()` inside the callback.
- Gesture-based undo path (`dominant == undoDirection && isEdgeStartForUndo`): after the fly-off animation completes, captures `lastActionDirection`, sets `enterFromDirection`, snaps offsets to `directionToOffset(capturedDir, flyDistance)`, then calls `onUndo()`.
- Toolbar undo button: replaced the direct `onClick = onUndo` with a `scope.launch { ... }` block that animates the current photo off toward `lastActionDirection` with `tween(180)`, sets `enterFromDirection`, then calls `onUndo()` — giving the button-based undo the same "physical return" feel as the gesture-based one.

Things to verify once the build works:
1. App compiles (`assembleDebug`).
2. Gesture undo: recovered photo slides in from the original swipe direction, not just appears.
3. Button undo: current photo flies off toward `lastActionDirection` and the recovered photo then slides back in from that same direction.
4. Normal forward swipes still work (new photo appears at center instantly, no entrance animation).
5. No visible "flash" frame where the new photo briefly renders at (0,0) before the entrance animation kicks in — the coroutine ordering is supposed to prevent this by snapping offsets before calling `onUndo()`.
