# Android edition

## Architecture

The native Kotlin / Compose app in `apps/android` exposes three panels: Presets, Adjustments and
Crop. The horizontal strip shows real previews of the current photo, numbered tiles, groups,
persistent favorites and an amount slider. It does not embed the desktop interface.

In version 0.2.0 the editor has a black canvas, a larger photo, compact adjacent preset tiles with
colored name strips, and a separate amount screen with a thin slider and cancel/confirm actions.
Horizontal swipes on the photo move by one preset in the visible group (left = next, right = previous),
including Original, stopping at the ends. The selected tile scrolls into view. Manual overrides and
crop remain intact when changing presets. Vertical and sub-threshold drags do not change the preset;
crop mode uses its own gestures. Long-press still compares the original. Tap the active preset to
edit its amount; cancellation restores the initial value and confirmation is one undo step.
Provisional intensity values are not persisted to the draft until confirmation; Android back
also cancels the intensity edit. Swipe navigation is disabled while adjusting intensity or crop.

`crates/mobile` is a `cdylib` façade over `lightcraft-engine`. Its JNI boundary accepts a JSON
request and returns `{ok, data}` or `{ok: false, error}`. One session and one photo are retained.
Operations: `init`, `presets`, `open`, `importPresets`, `render`. Every render rebuilds settings
from the photo's imported base, applies the preset once, then manual overrides, crop and rotation.
This avoids accumulating edits while sliders move. Mobile owns the undo stack; rendering does not
append engine history. Upstream export encoding embeds sRGB; source EXIF/GPS are not exported.

The desktop UI, CLI and MCP crates are not linked. Shared upstream modules stay in the fork for
updates. The initial build still links engine dependencies, including its GPU crate, but rendering
uses the CPU path. Extracting a smaller preset/render engine is a future optimization.

JNI calls run on one serial IO dispatcher. Bitmap decoding and file IO run off the UI thread.
Previews are debounced with cancellation/revision checks. Native calls finish synchronously;
cancellation discards their results instead of interrupting Rust. Thumbnails render at 160 px,
previews at 1440 px, and JPEG export at 4096 px. A new photo releases the old native session/caches.

Android system pickers grant access to selected files. Inputs are copied to private storage with
a 128 MiB limit; only the current source is retained after a successful photo open. Drafts store
the source path and adjustments; user presets are saved as `.lcpreset`; favorites use preferences.
MediaStore exports use a pending row during copying and remove failed pending exports.

## Build and compatibility

- Android 10 / API 29 minimum, compile/target 36; arm64-v8a and x86_64.
- AGP 9.0.1, Gradle 9.3.1, built-in Kotlin / Compose compiler 2.2.10, Compose BOM 2025.10.01
  (compatible with SDK 36), NDK r28c for 16 KiB-compatible libraries.
- JPEG, PNG, WebP, TIFF and upstream RAW decoders. HEIC is not enabled in this build and reports
  an unsupported-file error. Camera capture is outside the first scope.
- Preset mapping is approximate. Proprietary profiles, looks, unusual XMP wrappers or encrypted
  presets may not work. User-supplied real DNGs still need visual comparison.
- Imported donor crop, geometry and custom white balance are excluded by the upstream importer.
- Crop ratios initialize the frame; dragging corners uses free crop. Rotation resets the frame.
- Export caps the long edge at 4096 px without upscaling beyond the source. Large RAW decoding
  may need substantial native memory; the input-byte limit does not guarantee a decoded-memory cap.
- Thumbnails show preset-only looks without the user's manual adjustments or crop.
- Accessibility polish, crop screen-reader controls, more languages, tablets and physical-device
  performance testing remain to do. No release signing or Play Store publication is configured.

## Validation and next steps

The Android workflow checks the mobile Rust crate, builds both ABIs and APK, runs lint and Android
instrumentation. Tests create JPEGs and synthetic embedded-XMP DNG-preset payloads, exercise the real
JNI/pipeline, verify crop/rotate/zero intensity/persistence/error handling and export through the UI.
Compose captures `editor.png` in the app's external files directory for visual inspection.
The upstream parity counts are unchanged; this app has a separate, deliberately smaller scope.

Next: compare actual user presets, measure memory/latency on a midrange arm64 phone, optimize source
proxies, add export-size choices and preset management, improve accessibility and aspect locking,
then prepare signing and store assets after device validation. Manual sliders display effective
values returned by the engine, including the active preset, before applying explicit overrides.
