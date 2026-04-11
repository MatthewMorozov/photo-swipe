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
