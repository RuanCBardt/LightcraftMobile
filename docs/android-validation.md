# Android validation — 2026-10-13

Validated locally on Windows using Rust 1.99 (gnullvm host + LLVM-MinGW), JDK 21 and Android SDK 36.
The original repository revision used is `b3d392b4994ee99dc389890f006600769d0c4ff5`.

| Check | Result |
|---|---|
| `cargo check -p lightcraft-mobile` | Passed |
| `cargo fmt -p lightcraft-mobile --check` | Passed |
| `cargo clippy -p lightcraft-mobile -- -D warnings` | Passed |
| `cargo test -p lightcraft-mobile` | 2 tests passed |
| Native release builds: arm64-v8a and x86_64 | Passed |
| ARM64 ELF segment alignment | All LOAD segments aligned to 0x4000 (16 KiB) |
| JNI library dependencies | Only Android system libc, libm and libdl |
| Android Kotlin + instrumentation compilation | Passed |
| `assembleDebug` | Passed; development-signed APK |
| `lintDebug` | Passed, no errors; pinned-version suggestions and dynamic ABI-analysis warning remain |
| `connectedDebugAndroidTest` on API 36 x86_64 emulator | 2 tests passed |
| Compose editor screenshot | Captured and visually inspected |

Rust tests cover idempotent preset intensity, manual-adjustment precedence and invalid values/crop.
Android native tests exercise actual JNI, JPEG decoding/rendering, synthetic embedded-XMP DNG preset
import, unsupported-profile reporting, 0% intensity, manual values returned by the engine, crop,
rotation, preset persistence and missing-file errors. The Compose flow tests import a generated
image, choose a preset, open adjustments, crop, undo and save to the Android gallery.

The DNG-preset test uses a synthetic packet, not a user's real preset pack. This does not establish
visual parity with Lightroom. Physical ARM64 device performance, large RAW memory behavior,
HEIC, the full upstream desktop CI and Play Store distribution were not validated.
No shared upstream image-processing code was changed.

## Version 0.2.0 — reference layout and photo swipes

Kotlin compilation, `assembleDebug`, `lintDebug` (0 errors) and all 3 Android instrumentation tests
passed on the dedicated API 36 x86_64 emulator. The expanded Compose flow performs real swipes on
the photo: next/previous, vertical drag, short drag, group boundaries, preservation of manual edits,
and crop-gesture isolation. It also checks intensity cancellation, confirmation and undo. The native
import/render test and export flow remain green. Preset-strip and intensity screenshots use an
original procedural landscape, and were captured for visual inspection. User reference photographs
are not included in the app, test sources, commits or release artifacts. The Rust engine is unchanged.
