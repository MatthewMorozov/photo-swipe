# Photo Swipe

A Kotlin Multiplatform app for quickly sorting photos into folders using swipe gestures. Pick a source folder, assign up to eight destination folders to swipe directions (4 cardinal + 4 diagonal), then swipe through your photos one by one to move them where you want.

Targets:
- **Android** (full app, with Storage Access Framework folder pickers)
- **Browser via wasmJs** (interaction-only demo with procedurally generated images and in-memory "moves")

## Features

- Choose any folder on your device as a source
- Assign destination folders to all 8 swipe directions — Left, Right, Up, Down, and the four diagonals (Up-Left, Up-Right, Down-Left, Down-Right). Assign as many or as few as you need.
- Photos are presented in chronological order (oldest first)
- Visual overlay previews the destination as you drag
- One-level undo after each move
- Progress bar and counter
- Works entirely on-device — no network access, no cloud

---

## Requirements

| Requirement | Version |
|---|---|
| Java Development Kit (JDK) | 17 or newer |
| Android SDK Build-Tools | 35.0.0 |
| Android SDK Platform | API 35 |
| Android device / emulator | API 26 (Android 8.0) or newer |

---

## Option A — Android Studio (recommended for beginners)

Android Studio bundles the JDK, Android SDK, and an emulator in one installer.

1. Download **Android Studio** from https://developer.android.com/studio
2. Run the installer and follow the setup wizard. Accept the default SDK components.
3. Open Android Studio → **File → Open** → select the `photo-swipe` folder.
4. Wait for the Gradle sync to finish (bottom status bar).
5. Connect an Android device via USB with **USB debugging** enabled, or start an emulator from the **Device Manager**.
6. Press the green **Run** button (▶) or press `Shift+F10`.

---

## Option B — Command Line (no Android Studio required)

### Step 1 — Install a JDK

You need JDK 17 or newer. Any of the following work:

**Windows (winget):**
```
winget install Microsoft.OpenJDK.17
```

**Windows (Chocolatey):**
```
choco install temurin17
```

**macOS (Homebrew):**
```
brew install --cask temurin@17
```

**Linux (apt):**
```
sudo apt install openjdk-17-jdk
```

**Manual download:** https://adoptium.net/temurin/releases/?version=17

Verify the install:
```
java -version
```

---

### Step 2 — Install the Android SDK Command-Line Tools

1. Go to https://developer.android.com/studio#command-tools and download the **Command line tools only** package for your OS.

2. Create a directory for the SDK and unzip the tools into it:

   **Windows:**
   ```
   mkdir C:\Android\cmdline-tools
   # Extract the downloaded zip so the layout is:
   # C:\Android\cmdline-tools\latest\bin\sdkmanager.bat
   ```

   **macOS / Linux:**
   ```
   mkdir -p ~/Android/cmdline-tools
   # Extract so the layout is:
   # ~/Android/cmdline-tools/latest/bin/sdkmanager
   ```

   The extracted folder must be renamed (or placed) as `latest` inside `cmdline-tools`. Many zip files extract to a folder called `cmdline-tools` — rename that to `latest`.

3. Set environment variables. Add these to your shell profile (`.bashrc`, `.zshrc`, PowerShell `$PROFILE`, or Windows System Environment Variables):

   **macOS / Linux:**
   ```bash
   export ANDROID_HOME=~/Android
   export PATH=$PATH:$ANDROID_HOME/cmdline-tools/latest/bin
   export PATH=$PATH:$ANDROID_HOME/platform-tools
   ```

   **Windows (PowerShell `$PROFILE`):**
   ```powershell
   $env:ANDROID_HOME = "C:\Android"
   $env:PATH += ";C:\Android\cmdline-tools\latest\bin;C:\Android\platform-tools"
   ```

   **Windows (persistent — run once in an admin PowerShell):**
   ```powershell
   [System.Environment]::SetEnvironmentVariable("ANDROID_HOME", "C:\Android", "User")
   $current = [System.Environment]::GetEnvironmentVariable("PATH", "User")
   [System.Environment]::SetEnvironmentVariable("PATH", "$current;C:\Android\cmdline-tools\latest\bin;C:\Android\platform-tools", "User")
   ```

4. Install the required SDK components:

   ```
   sdkmanager "platform-tools" "build-tools;35.0.0" "platforms;android-35"
   ```

   Accept the licenses when prompted:
   ```
   sdkmanager --licenses
   ```

---

### Step 3 — Build the app

All build commands are run from the root of the project (`photo-swipe/`). The Gradle wrapper (`gradlew`) downloads the correct Gradle version automatically on first run — no separate Gradle install needed.

**macOS / Linux — make the wrapper executable first:**
```bash
chmod +x gradlew
```

#### Build a debug APK
```
./gradlew assembleDebug          # macOS / Linux
gradlew.bat assembleDebug        # Windows
```
Output: `app/build/outputs/apk/debug/app-debug.apk`

#### Build a release APK (unsigned)
```
./gradlew assembleRelease
```
Output: `app/build/outputs/apk/release/app-release-unsigned.apk`

To install an unsigned release build you must sign it first (see [Signing](#signing) below).

#### Build and install directly on a connected device
```
./gradlew installDebug
```
This builds the debug APK and pushes it to the first connected device via ADB. USB debugging must be enabled on the device.

#### Clean build artifacts
```
./gradlew clean
```

---

### Step 4 — Install the APK manually (optional)

If you prefer to copy the APK to the device yourself instead of using `installDebug`:

1. Enable **Install unknown apps** (or **Unknown sources**) on the device.
2. Copy the APK to the device and open it in a file manager, **or** use ADB:

```
adb install app/build/outputs/apk/debug/app-debug.apk
```

To see connected devices:
```
adb devices
```

If multiple devices are connected, target a specific one:
```
adb -s <device-serial> install app/build/outputs/apk/debug/app-debug.apk
```

---

---

## Building the Web Demo (wasmJs)

The web build shares all UI and view-model code with the Android app via a `Storage` interface; the browser version uses a `MockStorage` that generates colorful gradient images and tracks "moves" in memory only — no real files are touched.

### Requirements

- **JDK 17+** (this project has been verified to work on JDK 25 with Gradle 9.1)
- A modern browser (the build targets Wasm GC, so Chrome/Edge 119+, Firefox 120+, or Safari 18.2+)

### Build the production bundle

```
./gradlew :app:wasmJsBrowserDistribution          # macOS / Linux
gradlew.bat :app:wasmJsBrowserDistribution        # Windows
```

Output: `app/build/dist/wasmJs/productionExecutable/` — contains `index.html`, `photoswipe.js`, and the `.wasm` files.

### Run it

The wasm files won't load over `file://` — they need an HTTP server. Two options:

**Option 1 — Gradle dev server** (rebuilds on change, opens browser automatically):
```
./gradlew :app:wasmJsBrowserDevelopmentRun
```

**Option 2 — Serve the production bundle with any static server**, e.g. Python:
```
cd app/build/dist/wasmJs/productionExecutable
python -m http.server 8000
```
Then open http://localhost:8000/.

### What the demo does

- Click **Start Demo** to enter the swipe screen
- Drag photos in any of 8 directions (cardinals + diagonals); the demo wires up sample destinations to each
- Each direction has its own color-coded overlay that fades in as you drag toward it
- Swipe is animated with the same gesture/animation code the Android app uses
- Undo button restores the previous photo with the same "fly back" entrance animation
- "Moves" are tracked in memory only — refresh to reset

---

## Signing

The debug build is automatically signed with a debug keystore and can be installed on any device with **USB debugging** or **unknown sources** enabled. It cannot be published to the Play Store.

To create a release-signed APK for distribution:

1. Generate a keystore (run once, keep the file safe):
   ```
   keytool -genkey -v -keystore my-release-key.jks -keyalg RSA -keysize 2048 -validity 10000 -alias my-key
   ```

2. Add a `signingConfigs` block to `app/build.gradle.kts`:
   ```kotlin
   signingConfigs {
       create("release") {
           storeFile = file("../my-release-key.jks")
           storePassword = "your-store-password"
           keyAlias = "my-key"
           keyPassword = "your-key-password"
       }
   }
   buildTypes {
       release {
           signingConfig = signingConfigs.getByName("release")
       }
   }
   ```

3. Build:
   ```
   ./gradlew assembleRelease
   ```

**Never commit the keystore or passwords to version control.**

---

## Permissions

The app requests the following permissions at runtime:

| Permission | Purpose |
|---|---|
| `READ_MEDIA_IMAGES` (API 33+) | Read photos from the source folder |
| `READ_EXTERNAL_STORAGE` (API ≤ 32) | Read photos on older Android versions |

Folder access is granted via the system folder picker (Storage Access Framework) — no broad storage permission is required on Android 10+.

---

## Project Structure

```
photo-swipe/
├── app/
│   └── src/
│       ├── commonMain/kotlin/com/photoswipe/app/
│       │   ├── model/Models.kt              # FolderConfig, PhotoItem, SwipeDirection
│       │   ├── storage/Storage.kt           # Platform-agnostic storage interface
│       │   ├── viewmodel/SwipeViewModel.kt  # State machine; depends on Storage
│       │   └── ui/
│       │       ├── swipe/SwipeScreen.kt     # Shared swipe UI (Compose Multiplatform)
│       │       └── theme/Theme.kt           # Material 3 dark theme
│       ├── androidMain/
│       │   ├── kotlin/com/photoswipe/app/
│       │   │   ├── MainActivity.kt          # Android entry point
│       │   │   ├── storage/SafStorage.kt    # SAF-backed Storage impl
│       │   │   └── ui/setup/SetupScreen.kt  # Folder picker (OpenDocumentTree)
│       │   ├── res/
│       │   └── AndroidManifest.xml
│       └── wasmJsMain/
│           ├── kotlin/com/photoswipe/app/
│           │   ├── Main.kt                  # ComposeViewport entry
│           │   ├── storage/MockStorage.kt   # Procedural images, in-memory moves
│           │   └── ui/setup/SetupScreen.kt  # Simple Start Demo button
│           └── resources/index.html
├── gradle/
│   ├── libs.versions.toml                   # Dependency version catalog
│   └── wrapper/gradle-wrapper.properties
├── build.gradle.kts
├── settings.gradle.kts
└── gradle.properties
```
