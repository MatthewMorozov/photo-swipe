# Adding a browser target alongside Android

Notes on what it would take to ship a `wasmJs` build of photo-swipe without
disturbing the existing Android app. Nothing here has been prototyped — this is
a design sketch.

## Goal

Keep the Android app exactly as it is today, and add an optional Compose
Multiplatform web target that runs in the browser via `wasmJsBrowserDistribution`.
Both targets share UI and view-model code; each has its own data layer.

## What ports cleanly

The UI layer is the easy part. Everything in `ui/swipe/SwipeScreen.kt` is
Compose-only — `detectDragGestures`, `Animatable`, `spring`/`tween`, the
threshold logic, the color-coded direction overlays, and the entrance
animation work for undo. Compose Multiplatform's wasmJs target supports all of
these. `model/Models.kt` is pure Kotlin data classes and the `SwipeDirection`
enum — moves as-is.

`SwipeViewModel`'s state machine (current index, last action, undo) is also
platform-agnostic in shape. Only the I/O it performs is Android-specific.

## What does not port

Everything that touches storage:

- `ActivityResultContracts.OpenDocumentTree` and persistable URI permissions
- `DocumentFile` tree traversal in `SetupScreen`
- `DocumentsContract.moveDocument` (with the copy+delete fallback) in the
  view-model
- `findFileInFolder()` re-discovery used by undo after a stale URI
- Coil's Android `ImageLoader` resolving `content://` URIs
- `AndroidViewModel`'s `Application` context dependency

The browser equivalent is the **File System Access API**
(`window.showDirectoryPicker()` returning a `FileSystemDirectoryHandle`). It
has the same shape — opaque handles to a folder the user explicitly granted —
but very different mechanics:

- Chromium-only. Firefox and Safari don't ship it. A web build effectively
  means "Chrome/Edge users only" until that changes.
- Permission grants don't survive a tab close by default. Handles can be
  persisted in IndexedDB but the user is re-prompted to re-confirm access on
  the next visit. This is fine for a session-based "sort my downloads" flow
  but worse than Android's permanent grants.
- No atomic cross-folder move. You copy via `getFile()` → `createWritable()`
  in the destination, then `removeEntry()` on the source. That's the same
  fallback path the Android code already uses for cross-provider moves, so
  the view-model logic mostly transfers.
- Image loading switches from Coil to `URL.createObjectURL(blob)` fed into a
  Compose `Image` via `rememberAsyncImagePainter` from a multiplatform image
  loading lib (Coil 3 supports wasmJs, or use the new `ImageBitmap` APIs
  directly).

## Proposed module layout

```
app/                       — Android-only entry point (MainActivity, manifest)
shared/
  commonMain/              — Models, SwipeViewModel core, UI (SetupScreen,
                             SwipeScreen), expect declarations for storage
  androidMain/             — actual storage impl using SAF
                             (current viewmodel I/O, lifted from app/)
  wasmJsMain/              — actual storage impl using File System Access API
webApp/                    — wasmJs entry point, calls into shared
```

The `expect` surface is small — roughly:

```kotlin
expect class FolderHandle              // opaque per-platform reference
expect class PhotoHandle               // ditto for individual files

expect suspend fun pickFolder(): FolderHandle?
expect suspend fun listPhotos(folder: FolderHandle): List<PhotoHandle>
expect suspend fun movePhoto(
    photo: PhotoHandle,
    from: FolderHandle,
    to: FolderHandle,
): PhotoHandle                          // returns new handle for undo
expect suspend fun loadImageBytes(photo: PhotoHandle): ByteArray
```

`FolderConfig` and `DestinationFolder` become generic over `FolderHandle`
instead of holding `Uri` directly. The view-model's undo logic
(`LastAction` storing the original parent) keeps the same shape — it just
holds platform handles instead of URIs.

## Risks and unknowns

1. **Compose Multiplatform version drift.** The current Android build uses
   AGP 8.5.2 / Kotlin 2.0.21. Adding the KMP plugin and Compose Multiplatform
   means matching versions across both, and CMP's wasmJs support has been
   moving fast — likely needs Kotlin 2.1+ and a newer CMP. Bumping Kotlin
   could cascade into Coil/AndroidX updates.
2. **Browser support is narrow.** Without Firefox/Safari, the web target is
   really a "Chrome desktop demo," not a general web app. If the user can
   tolerate that scope, fine; if not, this isn't worth doing.
3. **Image performance.** Loading a folder of 1000+ photos via
   `getFile()`+blob URLs is fine, but full-resolution decoding of large
   JPEGs in wasm is slower than Coil on a phone. May need explicit
   downscaling for the swipe view.
4. **File System Access API gotchas.** The user can revoke handles mid-session
   (e.g., by ejecting an SD card on ChromeOS). Need defensive error handling
   on every move/load, since the Android SAF assumptions don't all hold.
5. **No drag-and-drop fallback.** If we wanted Firefox/Safari support, we'd
   need a completely different model — drag a folder of files into the page,
   process them in memory, save back via download prompts. Different UX,
   probably a separate effort.

## Incremental path

If the user wants to actually try this, the cheapest first step is:

1. Add the KMP plugin to the existing module without moving any code.
   Confirm the Android build still produces the same APK.
2. Lift `model/Models.kt` and the pure parts of `SwipeViewModel` into a
   `commonMain` source set. Still Android-only at this stage.
3. Stub the `expect`/`actual` storage interface, with the actual Android
   impl wrapping the existing `DocumentsContract` calls. Verify Android
   build is unchanged.
4. Add the wasmJs target with a no-op storage impl that returns hardcoded
   sample images. Get `wasmJsBrowserDistribution` building and serving.
5. Implement the File System Access API actual impl. Wire image loading.
6. Polish: persistence of folder handles in IndexedDB, error states for
   revoked permissions, downscaling for large images.

Steps 1–3 are pure refactors that leave the Android app behaviorally
identical. Steps 4–6 are where the real new work is.

## Recommendation

Worth doing only if (a) Chromium-only is acceptable, and (b) we want a web
demo or a way to use the app from a desktop without sideloading the APK.
For "make this a real cross-platform app," it's not a great fit — the SAF
model and the File System Access API model are similar enough to share a
view-model but different enough that the UX will diverge in small,
annoying ways (permission prompts, handle lifetime, what "undo" feels like
when the source folder handle is gone).
